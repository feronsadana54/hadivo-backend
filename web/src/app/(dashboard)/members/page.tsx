"use client";

import { RotateCcw } from "lucide-react";
import { useState } from "react";
import { ActiveBadge, RoleBadge } from "@/components/attendance/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useMemberDevices, useMemberShiftAssignments, useMemberships, useResetMemberDevices } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { tokenStorage } from "@/lib/auth/token-storage";
import { displayEmail, displayName, formatDateTime, shortId } from "@/lib/utils";
import type { Role, UserDevice } from "@/types/api";

export default function MembersPage() {
  const memberships = useMemberships();
  const currentUserId = readCurrentUserId();
  const currentRole = memberships.data?.find((membership) => membership.userId === currentUserId)?.role;
  const canManageDevices = canManageMemberDevices(currentRole);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Anggota</h1>
        <p className="text-sm text-muted-foreground">Daftar user yang terhubung ke tenant ini.</p>
      </div>
      {memberships.isLoading ? <LoadingState label="Memuat anggota..." /> : null}
      {memberships.isError ? <ErrorState message={getErrorMessage(memberships.error)} /> : null}
      {memberships.isSuccess && !memberships.data.length ? (
        <EmptyState title="Belum ada anggota" description="Anggota tenant akan tampil di sini setelah user ditambahkan." />
      ) : null}
      {memberships.isSuccess && memberships.data.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nama dan email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Status akun</TableHead>
                <TableHead>Shift</TableHead>
                <TableHead>Perangkat absensi</TableHead>
                <TableHead>Aksi</TableHead>
                <TableHead>Member ID</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {memberships.data.map((membership) => (
                <TableRow key={membership.membershipId}>
                  <TableCell>
                    <div className="font-semibold">{displayName(membership.fullName)}</div>
                    <div className="text-sm text-muted-foreground">{displayEmail(membership.email, membership.userId)}</div>
                  </TableCell>
                  <TableCell>
                    <RoleBadge role={membership.role} />
                  </TableCell>
                  <TableCell>
                    <ActiveBadge active={membership.active} />
                  </TableCell>
                  <MemberShiftCell userId={membership.userId} canViewShift={canManageDevices} />
                  <MemberDeviceCells
                    userId={membership.userId}
                    fullName={membership.fullName}
                    canManageDevices={canManageDevices}
                  />
                  <TableCell className="text-sm text-muted-foreground">{shortId(membership.membershipId)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}

function MemberShiftCell({ userId, canViewShift }: { userId: string; canViewShift: boolean }) {
  const assignments = useMemberShiftAssignments(userId, canViewShift);
  const current = assignments.data?.find((assignment) => assignment.current && assignment.active);

  return (
    <TableCell>
      {!canViewShift ? (
        <span className="text-sm text-muted-foreground">Hanya admin</span>
      ) : assignments.isLoading ? (
        <span className="text-sm text-muted-foreground">Memuat shift...</span>
      ) : assignments.isError ? (
        <span className="text-sm text-muted-foreground">{getErrorMessage(assignments.error)}</span>
      ) : current ? (
        <div className="space-y-1">
          <Badge variant="info">{current.shiftName ?? "Shift"}</Badge>
          <p className="text-xs text-muted-foreground">
            {timeLabel(current.shiftStartTime)} - {timeLabel(current.shiftEndTime)}
          </p>
        </div>
      ) : (
        <Badge variant="muted">Jadwal tenant</Badge>
      )}
    </TableCell>
  );
}

function MemberDeviceCells({
  userId,
  fullName,
  canManageDevices,
}: {
  userId: string;
  fullName?: string | null;
  canManageDevices: boolean;
}) {
  const devices = useMemberDevices(userId, canManageDevices);
  const resetDevices = useResetMemberDevices();
  const [message, setMessage] = useState<string | null>(null);
  const activeTrusted = devices.data?.find((device) => device.active && device.trusted);

  async function resetDevice() {
    const target = displayName(fullName);
    const confirmed = window.confirm(
      `Reset perangkat ${target}? Reset perangkat akan mengizinkan user mendaftarkan perangkat baru saat absensi berikutnya.`,
    );
    if (!confirmed) return;

    setMessage(null);
    try {
      await resetDevices.mutateAsync(userId);
      setMessage("Perangkat berhasil direset.");
    } catch (error) {
      setMessage(getErrorMessage(error));
    }
  }

  return (
    <>
      <TableCell>
        {!canManageDevices ? (
          <span className="text-sm text-muted-foreground">Hanya admin</span>
        ) : devices.isLoading ? (
          <span className="text-sm text-muted-foreground">Memuat perangkat...</span>
        ) : devices.isError ? (
          <span className="text-sm text-muted-foreground">{getErrorMessage(devices.error)}</span>
        ) : activeTrusted ? (
          <DeviceSummary device={activeTrusted} />
        ) : (
          <div className="space-y-1">
            <Badge variant="muted">Belum terdaftar</Badge>
            <p className="text-xs text-muted-foreground">Perangkat akan terdaftar saat absensi berikutnya.</p>
          </div>
        )}
      </TableCell>
      <TableCell>
        {!canManageDevices ? (
          <span className="text-sm text-muted-foreground">Tidak tersedia</span>
        ) : (
          <div className="space-y-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={resetDevice}
              disabled={resetDevices.isPending}
            >
              <RotateCcw className="mr-2 h-4 w-4" />
              Reset Device
            </Button>
            {message ? <p className="max-w-48 text-xs text-muted-foreground">{message}</p> : null}
          </div>
        )}
      </TableCell>
    </>
  );
}

function DeviceSummary({ device }: { device: UserDevice }) {
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="success">Trusted</Badge>
        {device.platform ? <Badge variant="muted">{device.platform}</Badge> : null}
      </div>
      <div className="text-xs text-muted-foreground">
        <p>{device.deviceName ?? "Perangkat absensi"}</p>
        <p>Terakhir aktif {formatDateTime(device.lastSeenAt)}</p>
      </div>
    </div>
  );
}

function readCurrentUserId() {
  const token = tokenStorage.getAccessToken();
  const payload = token?.split(".")[1];
  if (!payload) return null;
  try {
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), "=");
    const decoded = JSON.parse(window.atob(padded)) as { sub?: string };
    return decoded.sub ?? null;
  } catch {
    return null;
  }
}

function canManageMemberDevices(role?: Role) {
  return role === "TENANT_ADMIN" || role === "SUPER_ADMIN";
}

function timeLabel(value?: string | null) {
  return value ? value.slice(0, 5) : "-";
}
