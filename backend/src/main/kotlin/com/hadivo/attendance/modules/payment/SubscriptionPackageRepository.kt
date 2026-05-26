package com.hadivo.attendance.modules.payment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SubscriptionPackageRepository : JpaRepository<SubscriptionPackage, UUID> {
    fun findAllByActiveOrderByGrossAmountAsc(active: Boolean = true): List<SubscriptionPackage>
    fun findByCode(code: String): SubscriptionPackage?
}
