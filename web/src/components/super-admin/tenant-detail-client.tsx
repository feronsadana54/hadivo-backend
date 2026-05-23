"use client";

import Link from "next/link";
import { ArrowLeft, AlertTriangle, CalendarClock, Users, WalletCards } from "lucide-react";
import {
  AttemptReasonBadge,
  SubscriptionStatusBadge,
  TenantStatusBadge,
  TenantTypeBadge,
} from "@/components/attendance/status-badge";
import { SummaryCard } from "@/components/dashboard/summary-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useSuperAdminTenantDetail } from "@/hooks/use-api";
import { getErrorMessage, isForbiddenError } from "@/lib/api/client";
import { displayEmail, displayName, formatDateTime } from "@/lib/utils";

export function SuperAdminTenantDetailClient({ tenantId }: { tenantId: string }) {
  const detail = useSuperAdminTenantDetail(tenantId);

  if (detail.isLoading) {
    return <LoadingState label="Memuat detail tenant..." />;
  }

  if (detail.isError) {
    return (
      <ErrorState
        title="Akses Super Admin ditolak"
        message={
          isForbiddenError(detail.error)
            ? "Anda tidak memiliki akses ke halaman Super Admin."
            : getErrorMessage(detail.error)
        }
      />
    );
  }

  const tenant = detail.data;

  if (!tenant) {
    return <ErrorState title="Tenant tidak ditemukan" message="Data tenant tidak tersedia." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-3">
          <Button asChild variant="ghost" size="sm" className="px-0">
            <Link href="/super-admin/tenants">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Kembali ke tenant
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-semibold tracking-normal">{tenant.tenantName}</h1>
            <p className="text-sm text-muted-foreground">Detail read-only tenant dan ringkasan aktivitas hari ini.</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <TenantTypeBadge type={tenant.tenantType} />
          <TenantStatusBadge status={tenant.status} />
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SummaryCard title="Total anggota" value={tenant.memberCount} icon={Users} />
        <SummaryCard title="Anggota aktif" value={tenant.activeMemberCount} icon={Users} />
        <SummaryCard title="Absensi hari ini" value={tenant.attendanceToday} icon={CalendarClock} />
        <SummaryCard title="Percobaan gagal" value={tenant.failedAttemptsToday} icon={AlertTriangle} />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <Card>
          <CardHeader>
            <CardTitle>Profil tenant</CardTitle>
            <CardDescription>Identitas tenant yang terdaftar di platform.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 text-sm sm:grid-cols-2">
            <DetailItem label="Nama" value={tenant.tenantName} />
            <DetailItem label="Slug" value={tenant.tenantSlug} />
            <DetailItem label="Tenant ID" value={tenant.tenantId} />
            <DetailItem label="Timezone" value={tenant.timezone} />
            <DetailItem label="Dibuat" value={formatDateTime(tenant.createdAt)} />
            <DetailItem label="Status" value={tenant.active ? "Aktif" : "Nonaktif"} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Subscription saat ini</CardTitle>
            <CardDescription>Informasi subscription tenant.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 text-sm">
            {tenant.subscriptionCurrent ? (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="default">{tenant.subscriptionCurrent.plan}</Badge>
                  <SubscriptionStatusBadge status={tenant.subscriptionCurrent.status} />
                </div>
                <DetailItem label="Batas anggota" value={String(tenant.subscriptionCurrent.maxMembers)} />
                <DetailItem label="Mulai" value={formatDateTime(tenant.subscriptionCurrent.startedAt)} />
                <DetailItem
                  label="Berakhir"
                  value={tenant.subscriptionCurrent.expiresAt ? formatDateTime(tenant.subscriptionCurrent.expiresAt) : "Tidak ada tanggal"}
                />
              </>
            ) : (
              <p className="text-sm text-muted-foreground">Tenant ini belum memiliki data subscription.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Percobaan gagal terbaru</CardTitle>
          <CardDescription>Maksimal 10 percobaan absensi yang ditolak sistem.</CardDescription>
        </CardHeader>
        <CardContent>
          {!tenant.recentFailedAttempts.length ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Belum ada percobaan gagal untuk tenant ini.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Waktu</TableHead>
                  <TableHead>User</TableHead>
                  <TableHead>Jenis</TableHead>
                  <TableHead>Alasan</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenant.recentFailedAttempts.map((attempt) => (
                  <TableRow key={attempt.attemptId}>
                    <TableCell>{formatDateTime(attempt.createdAt)}</TableCell>
                    <TableCell>
                      <div className="font-medium">{displayName(attempt.fullName)}</div>
                      <div className="text-xs text-muted-foreground">{displayEmail(attempt.email, attempt.userId)}</div>
                    </TableCell>
                    <TableCell>{attempt.type === "CLOCK_IN" ? "Clock-in" : "Clock-out"}</TableCell>
                    <TableCell>
                      <AttemptReasonBadge reason={attempt.reason} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-xs font-medium uppercase text-muted-foreground">{label}</p>
      <p className="mt-1 break-words font-medium">{value}</p>
    </div>
  );
}
