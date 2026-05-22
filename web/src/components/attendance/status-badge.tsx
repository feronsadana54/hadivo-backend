import { Badge } from "@/components/ui/badge";
import type { AttendanceStatus, AttemptReason, Role, SubscriptionStatus } from "@/types/api";

export function AttendanceStatusBadge({ status }: { status: AttendanceStatus }) {
  const variant = status === "ON_TIME" || status === "COMPLETED" ? "success" : status === "LATE" ? "warning" : "info";
  return <Badge variant={variant}>{attendanceStatusLabel(status)}</Badge>;
}

export function AttemptReasonBadge({ reason }: { reason: AttemptReason }) {
  const variant = reason === "OUT_OF_RADIUS" || reason === "FACE_MISMATCH" ? "danger" : "warning";
  return <Badge variant={variant}>{attemptReasonLabel(reason)}</Badge>;
}

export function RoleBadge({ role }: { role: Role }) {
  const variant = role === "SUPER_ADMIN" || role === "TENANT_ADMIN" ? "info" : "muted";
  return <Badge variant={variant}>{roleLabel(role)}</Badge>;
}

export function ActiveBadge({ active }: { active: boolean }) {
  return <Badge variant={active ? "success" : "muted"}>{active ? "Aktif" : "Nonaktif"}</Badge>;
}

export function SubscriptionStatusBadge({ status }: { status: SubscriptionStatus }) {
  return <Badge variant={status === "ACTIVE" ? "success" : "warning"}>{subscriptionStatusLabel(status)}</Badge>;
}

export function attendanceStatusLabel(status: AttendanceStatus) {
  const labels: Record<AttendanceStatus, string> = {
    ON_TIME: "Tepat waktu",
    LATE: "Terlambat",
    COMPLETED: "Selesai",
    EARLY_LEAVE: "Pulang awal",
  };
  return labels[status];
}

export function attemptReasonLabel(reason: AttemptReason) {
  const labels: Record<AttemptReason, string> = {
    OUT_OF_RADIUS: "Di luar area absensi",
    FACE_MISMATCH: "Verifikasi wajah gagal",
    INVALID_LOCATION: "Lokasi tidak valid",
    DEVICE_MISMATCH: "Perangkat tidak sesuai",
    DUPLICATE_CLOCK_IN: "Sudah clock-in hari ini",
    NO_CLOCK_IN: "Belum melakukan clock-in",
    ALREADY_CLOCKED_OUT: "Sudah melakukan clock-out",
    LATE_NOT_ALLOWED: "Clock-in terlambat tidak diizinkan",
  };
  return labels[reason];
}

export function roleLabel(role: Role) {
  const labels: Record<Role, string> = {
    SUPER_ADMIN: "Super Admin",
    TENANT_ADMIN: "Admin Tenant",
    MANAGER: "Manajer",
    TEACHER: "Guru",
    EMPLOYEE: "Karyawan",
    STUDENT: "Siswa",
    PARENT: "Orang Tua",
  };
  return labels[role];
}

export function subscriptionStatusLabel(status: SubscriptionStatus) {
  const labels: Record<SubscriptionStatus, string> = {
    ACTIVE: "Aktif",
    EXPIRED: "Kedaluwarsa",
    CANCELED: "Dibatalkan",
  };
  return labels[status];
}
