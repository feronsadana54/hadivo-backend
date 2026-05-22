package com.hadivo.attendance.modules.auth

import com.hadivo.attendance.config.AppProperties
import com.hadivo.attendance.modules.membership.Membership
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
@Profile("local", "dev")
class DataSeeder(
    private val users: UserRepository,
    private val memberships: MembershipRepository,
    private val passwordEncoder: PasswordEncoder,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun seed() {
        seedSuperAdmin()
        seedAttendanceDemoUser("employee@hadivo.local", "Demo Employee", Role.EMPLOYEE)
        seedAttendanceDemoUser("student@hadivo.local", "Demo Student", Role.STUDENT)
    }

    private fun seedSuperAdmin() {
        val email = props.seed.superAdminEmail.lowercase()
        if (users.existsByEmail(email)) {
            log.debug("Super admin already present, skipping seed")
            return
        }
        val user = users.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(props.seed.superAdminPassword),
                fullName = "Super Admin",
            )
        )
        memberships.save(
            Membership(
                tenantId = DEMO_TENANT_ID,
                userId = user.id ?: error("super admin id null"),
                role = Role.SUPER_ADMIN,
            )
        )
        log.info("Seeded super admin user={} with role SUPER_ADMIN on demo tenant", email)
    }

    private fun seedAttendanceDemoUser(email: String, fullName: String, role: Role) {
        val normalizedEmail = email.lowercase()
        val user = users.findByEmail(normalizedEmail) ?: users.save(
            User(
                email = normalizedEmail,
                passwordHash = passwordEncoder.encode(DEMO_MOBILE_PASSWORD),
                fullName = fullName,
            )
        ).also {
            log.info("Seeded demo mobile user={} with role {}", normalizedEmail, role)
        }

        val userId = user.id ?: error("demo mobile user id null")
        if (memberships.findByTenantIdAndUserId(DEMO_TENANT_ID, userId) == null) {
            memberships.save(
                Membership(
                    tenantId = DEMO_TENANT_ID,
                    userId = userId,
                    role = role,
                )
            )
            log.info("Seeded demo mobile membership user={} role={} tenant={}", normalizedEmail, role, DEMO_TENANT_ID)
        }
    }

    private companion object {
        val DEMO_TENANT_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        const val DEMO_MOBILE_PASSWORD: String = "ChangeMe123!"
    }
}
