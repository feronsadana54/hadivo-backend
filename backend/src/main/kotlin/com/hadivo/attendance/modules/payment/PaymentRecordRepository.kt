package com.hadivo.attendance.modules.payment

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PaymentRecordRepository : JpaRepository<PaymentRecord, UUID> {
    fun findAllByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<PaymentRecord>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): PaymentRecord?
    fun findByProviderOrderId(providerOrderId: String): PaymentRecord?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentRecord payment where payment.providerOrderId = :providerOrderId")
    fun findByProviderOrderIdForUpdate(@Param("providerOrderId") providerOrderId: String): PaymentRecord?
}
