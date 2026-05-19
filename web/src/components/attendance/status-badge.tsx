import { Badge } from "@/components/ui/badge";
import type { AttendanceStatus, AttemptReason, Role, SubscriptionStatus } from "@/types/api";

export function AttendanceStatusBadge({ status }: { status: AttendanceStatus }) {
  const variant = status === "ON_TIME" || status === "COMPLETED" ? "success" : status === "LATE" ? "warning" : "info";
  return <Badge variant={variant}>{status}</Badge>;
}

export function AttemptReasonBadge({ reason }: { reason: AttemptReason }) {
  const variant = reason === "OUT_OF_RADIUS" || reason === "FACE_MISMATCH" ? "danger" : "warning";
  return <Badge variant={variant}>{reason}</Badge>;
}

export function RoleBadge({ role }: { role: Role }) {
  const variant = role === "SUPER_ADMIN" || role === "TENANT_ADMIN" ? "info" : "muted";
  return <Badge variant={variant}>{role}</Badge>;
}

export function ActiveBadge({ active }: { active: boolean }) {
  return <Badge variant={active ? "success" : "muted"}>{active ? "ACTIVE" : "INACTIVE"}</Badge>;
}

export function SubscriptionStatusBadge({ status }: { status: SubscriptionStatus }) {
  return <Badge variant={status === "ACTIVE" ? "success" : "warning"}>{status}</Badge>;
}
