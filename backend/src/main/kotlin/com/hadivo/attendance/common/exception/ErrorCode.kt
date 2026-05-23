package com.hadivo.attendance.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Sesi tidak valid atau sudah berakhir"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Akses ditolak"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Data tidak ditemukan"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Permintaan tidak valid"),
    CONFLICT(HttpStatus.CONFLICT, "Konflik data"),
    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Tidak dapat diproses"),
    SUBSCRIPTION_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Batas anggota subscription telah tercapai"),
    OUT_OF_RADIUS(HttpStatus.UNPROCESSABLE_ENTITY, "Lokasi berada di luar radius absensi"),
    FACE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Verifikasi wajah gagal"),
    INVALID_DEVICE(HttpStatus.UNPROCESSABLE_ENTITY, "Perangkat tidak valid untuk absensi"),
    DEVICE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Perangkat ini belum terdaftar untuk absensi"),
    DUPLICATE_CLOCK_IN(HttpStatus.UNPROCESSABLE_ENTITY, "Sudah melakukan clock-in hari ini"),
    NO_CLOCK_IN(HttpStatus.UNPROCESSABLE_ENTITY, "Belum melakukan clock-in"),
    ALREADY_CLOCKED_OUT(HttpStatus.UNPROCESSABLE_ENTITY, "Sudah melakukan clock-out hari ini"),
    LATE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Clock-in terlambat tidak diizinkan"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan pada server"),
}
