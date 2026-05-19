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

    private companion object {
        val DEMO_TENANT_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
