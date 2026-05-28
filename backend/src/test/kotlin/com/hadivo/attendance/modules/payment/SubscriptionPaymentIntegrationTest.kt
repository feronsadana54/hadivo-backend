package com.hadivo.attendance.modules.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.audit.AuditLogRepository
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.membership.Membership
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import com.hadivo.attendance.modules.settings.TenantAttendanceSettings
import com.hadivo.attendance.modules.settings.TenantAttendanceSettingsRepository
import com.hadivo.attendance.modules.subscription.Subscription
import com.hadivo.attendance.modules.subscription.SubscriptionPlan
import com.hadivo.attendance.modules.subscription.SubscriptionRepository
import com.hadivo.attendance.modules.subscription.SubscriptionStatus
import com.hadivo.attendance.modules.tenant.Tenant
import com.hadivo.attendance.modules.tenant.TenantMode
import com.hadivo.attendance.modules.tenant.TenantRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionPaymentIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var packages: SubscriptionPackageRepository
    @Autowired private lateinit var payments: PaymentRecordRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `tenant admin can create mock payment without Midtrans secret`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val pkg = packages.findByCode("PRO_MONTHLY") ?: error("package not seeded")

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/subscription-payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "packageId" to pkg.id.toString(),
                            "billingPeriod" to pkg.billingPeriod.name,
                            "customerName" to "Admin User",
                            "customerEmail" to ctx.email,
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.provider").value("MOCK"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.paymentUrl").value(containsString("/mock-payments/")))
            .andExpect(jsonPath("$.data.grossAmount").value(99000.00))

        val payment = payments.findAllByTenantIdOrderByCreatedAtDesc(ctx.tenantId).first()
        assertThat(payment.provider).isEqualTo(PaymentProvider.MOCK)
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.paymentUrl).contains(payment.providerOrderId)
        assertThat(auditLogs.findAll().filter { it.tenantId == ctx.tenantId }.map { it.action })
            .contains("PAYMENT_CREATED")
    }

    @Test
    fun `missing package returns human readable error`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val missingPackageId = UUID.randomUUID()

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/subscription-payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("packageId" to missingPackageId.toString())))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("Paket subscription tidak ditemukan"))
    }

    @ParameterizedTest
    @EnumSource(value = Role::class, names = ["EMPLOYEE", "STUDENT", "PARENT"])
    fun `non admin tenant roles cannot create payment`(role: Role) {
        val ctx = seedTenantWithUser(role)
        val pkg = packages.findByCode("PRO_MONTHLY") ?: error("package not seeded")

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/subscription-payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("packageId" to pkg.id.toString())))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
    }

    @Test
    fun `cross tenant create list and detail access are rejected`() {
        val tenantA = seedTenantWithUser(Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(Role.TENANT_ADMIN)
        val pkg = packages.findByCode("PRO_MONTHLY") ?: error("package not seeded")
        val payment = payments.save(
            PaymentRecord(
                tenantId = tenantB.tenantId,
                packageId = pkg.id,
                provider = PaymentProvider.MOCK,
                providerOrderId = "HADIVO-TEST-${UUID.randomUUID().toString().take(8)}",
                grossAmount = pkg.grossAmount,
                status = PaymentStatus.PENDING,
                paymentUrl = "http://localhost:8080/mock-payments/test",
                createdBy = tenantB.userId,
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${tenantB.tenantId}/subscription-payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("packageId" to pkg.id.toString())))
        )
            .andExpect(status().isForbidden)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/subscription-payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
        )
            .andExpect(status().isForbidden)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/subscription-payments/${payment.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `list and detail payment do not expose raw webhook json`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val pkg = packages.findByCode("PRO_MONTHLY") ?: error("package not seeded")
        val payment = payments.save(
            PaymentRecord(
                tenantId = ctx.tenantId,
                packageId = pkg.id,
                provider = PaymentProvider.MOCK,
                providerOrderId = "HADIVO-TEST-${UUID.randomUUID().toString().take(8)}",
                grossAmount = pkg.grossAmount,
                status = PaymentStatus.PENDING,
                paymentUrl = "http://localhost:8080/mock-payments/test",
                rawWebhookJson = """{"signature_key":"[redacted]","server_key":"[redacted]"}""",
                createdBy = ctx.userId,
            )
        )

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/subscription-payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("rawWebhookJson"))))
            .andExpect(content().string(not(containsString("signature_key"))))
            .andExpect(content().string(not(containsString("server_key"))))

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/subscription-payments/${payment.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("rawWebhookJson"))))
            .andExpect(content().string(not(containsString("signature_key"))))
            .andExpect(content().string(not(containsString("server_key"))))
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Payment Test Tenant",
                slug = "payment-${UUID.randomUUID().toString().take(8)}",
                mode = TenantMode.COMPANY,
            )
        )
        val tenantId = tenant.id!!
        subscriptions.save(
            Subscription(
                tenantId = tenantId,
                plan = SubscriptionPlan.FREE,
                maxMembers = SubscriptionPlan.FREE.maxMembers,
                startedAt = Instant.now(),
                status = SubscriptionStatus.ACTIVE,
            )
        )
        settings.save(TenantAttendanceSettings(tenantId = tenantId))
        val user = users.save(
            User(
                email = "payment-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Payment Test User",
            )
        )
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))
        return TestContext(
            tenantId = tenantId,
            userId = user.id!!,
            email = user.email,
            token = jwt.issueAccessToken(user.id!!, user.email).token,
        )
    }

    private data class TestContext(
        val tenantId: UUID,
        val userId: UUID,
        val email: String,
        val token: String,
    )
}
