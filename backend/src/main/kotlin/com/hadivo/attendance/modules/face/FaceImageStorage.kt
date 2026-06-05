package com.hadivo.attendance.modules.face

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

/**
 * Stores enrollment images on local disk. The directory must be `.gitignore`-d
 * because it holds biometric sample data.
 *
 * v1.6.0 scope: format / size validation only. We DO NOT perform face detection,
 * face matching, liveness check, or anti-spoofing. Magic bytes only confirm the
 * payload is a JPEG or PNG, not that it contains a real face.
 */
@Component
class FaceImageStorage(
    @Value("\${hadivo.face.storage-dir:backend/storage/face}") private val storageDir: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun store(tenantId: UUID, userId: UUID, imageBase64: String?): StoredImage {
        val sanitized = sanitizeBase64(imageBase64)
        val decoded = decode(sanitized)
        val extension = detectExtensionFromMagicBytes(decoded)

        val tenantDir = Paths.get(storageDir, tenantId.toString())
        Files.createDirectories(tenantDir)

        val timestamp = TIMESTAMP_FORMATTER.format(Instant.now())
        val filename = "$userId-$timestamp.$extension"
        val absolute = tenantDir.resolve(filename)
        Files.write(absolute, decoded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        // Reference is relative to the storage root so it stays portable and we
        // never write absolute paths into the database.
        val relative = "${tenantId}/$filename"
        return StoredImage(reference = relative, byteCount = decoded.size)
    }

    /**
     * Best-effort delete. Returns true if a file was removed, false otherwise.
     * Never throws — caller may already be in a reset flow we must not abort.
     */
    fun delete(reference: String?): Boolean {
        if (reference.isNullOrBlank()) return false
        return try {
            val resolved = resolveSafe(reference) ?: return false
            Files.deleteIfExists(resolved)
        } catch (ex: Exception) {
            // Do not log full path — reference may contain user identifiers.
            log.warn("Failed to delete face enrollment file (suppressed): {}", ex.javaClass.simpleName)
            false
        }
    }

    private fun resolveSafe(reference: String): Path? {
        val root = Paths.get(storageDir).toAbsolutePath().normalize()
        val candidate = root.resolve(reference).normalize()
        // Defence against `..` traversal coming from a tampered DB row.
        if (!candidate.startsWith(root)) {
            log.warn("Refusing to delete file outside storage root")
            return null
        }
        return candidate
    }

    private fun sanitizeBase64(raw: String?): String {
        if (raw.isNullOrBlank()) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Foto wajah wajib diisi")
        }
        // Accept both `data:image/...;base64,XYZ` and raw base64.
        val comma = raw.indexOf(',')
        val payload = if (raw.startsWith("data:") && comma > 0) raw.substring(comma + 1) else raw
        val stripped = payload.replace("\n", "").replace("\r", "").trim()
        if (stripped.length > MAX_BASE64_CHARS) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Ukuran foto melebihi batas maksimum",
            )
        }
        return stripped
    }

    private fun decode(payload: String): ByteArray =
        try {
            Base64.getDecoder().decode(payload)
        } catch (ex: IllegalArgumentException) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Format foto tidak valid (base64 rusak)")
        }

    private fun detectExtensionFromMagicBytes(bytes: ByteArray): String {
        if (bytes.size < 4) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Foto terlalu kecil untuk divalidasi")
        }
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "jpg"
        }
        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "png"
        }
        throw DomainException(
            ErrorCode.VALIDATION_FAILED,
            "Hanya format JPEG atau PNG yang diterima",
        )
    }

    data class StoredImage(val reference: String, val byteCount: Int)

    companion object {
        // 5 MiB worth of bytes ~ 6.7M base64 chars. We cap a bit higher for safety
        // margin but well below DoS territory.
        private const val MAX_BASE64_CHARS = 7_500_000
        private val TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(ZoneOffset.UTC)
    }
}
