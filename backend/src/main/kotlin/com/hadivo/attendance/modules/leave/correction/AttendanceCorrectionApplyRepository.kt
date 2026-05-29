package com.hadivo.attendance.modules.leave.correction

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AttendanceCorrectionApplyRepository : JpaRepository<AttendanceCorrectionApply, UUID> {
    fun findByLeaveRequestId(leaveRequestId: UUID): AttendanceCorrectionApply?
}
