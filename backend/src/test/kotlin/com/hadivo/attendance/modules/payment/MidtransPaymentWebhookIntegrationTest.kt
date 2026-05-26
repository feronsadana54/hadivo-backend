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
import org.junit.jupiter.api.Test
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
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = ["hadivo.payment.midtrans.server-key=test-midtrans-server-key"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MidtransPaymentWebhookIntegrationTest {

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
    fun `paid webhook activates subscription and sanitizes raw webhook`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val payment = seedPendingPayment(ctx)

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(midtransPayload(payment, "settlement")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.ignored").value(false))

        val stored = payments.findByProviderOrderId(payment.providerOrderId)!!
        assertThat(stored.status).isEqualTo(PaymentStatus.PAID)
        assertThat(stored.paidAt).isNotNull()
        assertThat(stored.subscriptionId).isNotNull()
        assertThat(stored.rawWebhookJson).doesNotContain("test-midtrans-server-key")
        assertThat(stored.rawWebhookJson).doesNotContain(stored.providerOrderId + "200" + amountString(stored.grossAmount))
        assertThat(stored.rawWebhookJson).contains("\"signature_key\":\"[redacted]\"")

        val activeSubscriptions = subscriptions.findAll()
            .filter { it.tenantId == ctx.tenantId && it.status == SubscriptionStatus.ACTIVE }
        assertThat(activeSubscriptions).hasSize(1)
        assertThat(activeSubscriptions.first().plan).isEqualTo(SubscriptionPlan.PRO)

        val actions = auditLogs.findAll().filter { it.tenantId == ctx.tenantId }.map { it.action }
        assertThat(actions).contains("PAYMENT_WEBHOOK_RECEIVED", "PAYMENT_STATUS_UPDATED", "SUBSCRIPTION_ACTIVATED")
    }

    @Test
    fun `duplicate paid webhook is idempotent`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val payment = seedPendingPayment(ctx)
        val payload = midtransPayload(payment, "settlement")

        repeat(2) {
            mvc.perform(
                post("/api/v1/payments/webhooks/midtrans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(payload))
            )
                .andExpect(status().isOk)
        }

        val stored = payments.findByProviderOrderId(payment.providerOrderId)!!
        val activeSubscriptions = subscriptions.findAll()
            .filter { it.tenantId == ctx.tenantId && it.status == SubscriptionStatus.ACTIVE }
        assertThat(stored.status).isEqualTo(PaymentStatus.PAID)
        assertThat(activeSubscriptions).hasSize(1)
        assertThat(auditLogs.findAll().filter { it.tenantId == ctx.tenantId }.map { it.action })
            .contains("PAYMENT_WEBHOOK_IGNORED")
    }

    @Test
    fun `failed and expired webhook do not activate subscription`() {
        val failedCtx = seedTenantWithUser(Role.TENANT_ADMIN)
        val failedPayment = seedPendingPayment(failedCtx)
        val expiredCtx = seedTenantWithUser(Role.TENANT_ADMIN)
        val expiredPayment = seedPendingPayment(expiredCtx)

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(midtransPayload(failedPayment, "failure", statusCode = "202")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("FAILED"))

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(midtransPayload(expiredPayment, "expire", statusCode = "407")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("EXPIRED"))

        assertFreeSubscriptionStillActive(failedCtx.tenantId)
        assertFreeSubscriptionStillActive(expiredCtx.tenantId)
        assertThat(payments.findByProviderOrderId(failedPayment.providerOrderId)!!.subscriptionId).isNull()
        assertThat(payments.findByProviderOrderId(expiredPayment.providerOrderId)!!.subscriptionId).isNull()
    }

    @Test
    fun `late webhook cannot downgrade paid payment`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val payment = seedPendingPayment(ctx)

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(midtransPayload(payment, "settlement")))
        )
            .andExpect(status().isOk)

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(midtransPayload(payment, "expire", statusCode = "407")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.ignored").value(true))

        val stored = payments.findByProviderOrderId(payment.providerOrderId)!!
        assertThat(stored.status).isEqualTo(PaymentStatus.PAID)
        assertThat(subscriptions.findAll().filter { it.tenantId == ctx.tenantId && it.status == SubscriptionStatus.ACTIVE })
            .hasSize(1)
    }

    @Test
    fun `invalid signature is rejected`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val payment = seedPendingPayment(ctx)
        val payload = midtransPayload(payment, "settlement").toMutableMap()
        payload["signature_key"] = "invalid-signature"

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

        assertThat(payments.findByProviderOrderId(payment.providerOrderId)!!.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(auditLogs.findAll().filter { it.tenantId == ctx.tenantId }.map { it.action })
            .contains("PAYMENT_WEBHOOK_IGNORED")
    }

    @Test
    fun `amount mismatch is rejected`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val payment = seedPendingPayment(ctx)
        val wrongAmount = "1.00"

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        midtransPayload(payment, "settlement", grossAmount = wrongAmount)
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

        assertThat(payments.findByProviderOrderId(payment.providerOrderId)!!.status).isEqualTo(PaymentStatus.PENDING)
    }

    @Test
    fun `raw webhook is not exposed in list or detail response`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)
        val payment = seedPendingPayment(ctx)

        mvc.perform(
            post("/api/v1/payments/webhooks/midtrans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(midtransPayload(payment, "settlement")))
        )
            .andExpect(status().isOk)

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/subscription-payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rawWebhookJson"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("signature_key"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-midtrans-server-key"))))

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/subscription-payments/${payment.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rawWebhookJson"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("signature_key"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-midtrans-server-key"))))
    }

    private fun seedPendingPayment(ctx: TestContext): PaymentRecord {
        val pkg = packages.findByCode("PRO_MONTHLY") ?: error("package not seeded")
        return payments.save(
            PaymentRecord(
                tenantId = ctx.tenantId,
                packageId = pkg.id,
                provider = PaymentProvider.MIDTRANS,
                providerOrderId = "HADIVO-TEST-${UUID.randomUUID().toString().replace("-", "").take(10)}",
                grossAmount = pkg.grossAmount,
                currency = pkg.currency,
                status = PaymentStatus.PENDING,
                paymentUrl = "https://app.sandbox.midtrans.com/snap/v2/vtweb/test-token",
                createdBy = ctx.userId,
            )
        )
    }

    private fun midtransPayload(
        payment: PaymentRecord,
        transactionStatus: String,
        statusCode: String = "200",
        grossAmount: String = amountString(payment.grossAmount),
    ): Map<String, String> =
        mapOf(
            "order_id" to payment.providerOrderId,
            "transaction_status" to transactionStatus,
            "status_code" to statusCode,
            "gross_amount" to grossAmount,
            "transaction_id" to "midtrans-${UUID.randomUUID()}",
            "fraud_status" to "accept",
            "signature_key" to signature(payment.providerOrderId, statusCode, grossAmount),
        )

    private fun signature(orderId: String, statusCode: String, grossAmount: String): String =
        sha512(orderId + statusCode + grossAmount + MIDTRANS_TEST_SERVER_KEY)

    private fun sha512(value: String): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun amountString(amount: BigDecimal): String = amount.setScale(2).toPlainString()

    private fun assertFreeSubscriptionStillActive(tenantId: UUID) {
        val activeSubscriptions = subscriptions.findAll()
            .filter { it.tenantId == tenantId && it.status == SubscriptionStatus.ACTIVE }
        assertThat(activeSubscriptions).hasSize(1)
        assertThat(activeSubscriptions.first().plan).isEqualTo(SubscriptionPlan.FREE)
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Webhook Test Tenant",
                slug = "webhook-${UUID.randomUUID().toString().take(8)}",
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
                email = "webhook-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Webhook Test User",
            )
        )
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))
        return TestContext(
            tenantId = tenantId,
            userId = user.id!!,
            token = jwt.issueAccessToken(user.id!!, user.email).token,
        )
    }

    private data class TestContext(
        val tenantId: UUID,
        val userId: UUID,
        val token: String,
    )

    private companion object {
        const val MIDTRANS_TEST_SERVER_KEY = "test-midtrans-server-key"
    }
}
