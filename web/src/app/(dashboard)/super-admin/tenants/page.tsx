"use client";

import Link from "next/link";
import { Eye, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { SubscriptionStatusBadge, TenantStatusBadge, TenantTypeBadge } from "@/components/attendance/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useSuperAdminTenants } from "@/hooks/use-api";
import { getErrorMessage, isForbiddenError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";
import type { SubscriptionStatus, TenantMode } from "@/types/api";

const pageSize = 10;

export default function SuperAdminTenantsPage() {
  const [search, setSearch] = useState("");
  const [type, setType] = useState<"" | TenantMode>("");
  const [subscriptionStatus, setSubscriptionStatus] = useState<"" | SubscriptionStatus>("");
  const [page, setPage] = useState(0);

  const filters = useMemo(
    () => ({
      search: search.trim() || undefined,
      type: type || undefined,
      subscriptionStatus: subscriptionStatus || undefined,
      page,
      size: pageSize,
    }),
    [page, search, subscriptionStatus, type],
  );
  const tenants = useSuperAdminTenants(filters);

  function resetPage(next: () => void) {
    setPage(0);
    next();
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Tenant</h1>
        <p className="text-sm text-muted-foreground">Daftar tenant Hadivo untuk pemantauan read-only.</p>
      </div>

      <Card className="p-4">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_180px_220px]">
          <div className="space-y-2">
            <Label htmlFor="tenant-search">Cari tenant</Label>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-3.5 h-4 w-4 text-muted-foreground" />
              <Input
                id="tenant-search"
                value={search}
                onChange={(event) => resetPage(() => setSearch(event.target.value))}
                placeholder="Nama atau slug tenant"
                className="pl-9"
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="tenant-type">Jenis</Label>
            <select
              id="tenant-type"
              value={type}
              onChange={(event) => resetPage(() => setType(event.target.value as "" | TenantMode))}
              className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="">Semua jenis</option>
              <option value="COMPANY">Perusahaan</option>
              <option value="SCHOOL">Sekolah</option>
            </select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="subscription-status">Subscription</Label>
            <select
              id="subscription-status"
              value={subscriptionStatus}
              onChange={(event) => resetPage(() => setSubscriptionStatus(event.target.value as "" | SubscriptionStatus))}
              className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="">Semua status</option>
              <option value="ACTIVE">Aktif</option>
              <option value="EXPIRED">Kedaluwarsa</option>
              <option value="CANCELLED">Dibatalkan</option>
            </select>
          </div>
        </div>
      </Card>

      {tenants.isLoading ? <LoadingState label="Memuat tenant..." /> : null}
      {tenants.isError ? (
        <ErrorState
          title="Akses Super Admin ditolak"
          message={
            isForbiddenError(tenants.error)
              ? "Anda tidak memiliki akses ke halaman Super Admin."
              : getErrorMessage(tenants.error)
          }
        />
      ) : null}
      {tenants.isSuccess && !tenants.data.items.length ? (
        <EmptyState title="Tenant tidak ditemukan" description="Ubah kata kunci atau filter untuk melihat tenant lain." />
      ) : null}
      {tenants.isSuccess && tenants.data.items.length ? (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nama Tenant</TableHead>
                  <TableHead>Jenis</TableHead>
                  <TableHead>Anggota</TableHead>
                  <TableHead>Absensi Hari Ini</TableHead>
                  <TableHead>Percobaan Gagal</TableHead>
                  <TableHead>Subscription</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Dibuat</TableHead>
                  <TableHead>Detail</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.data.items.map((tenant) => (
                  <TableRow key={tenant.tenantId}>
                    <TableCell>
                      <div className="font-semibold">{tenant.tenantName}</div>
                      <div className="text-xs text-muted-foreground">{tenant.tenantId}</div>
                    </TableCell>
                    <TableCell>
                      <TenantTypeBadge type={tenant.tenantType} />
                    </TableCell>
                    <TableCell>{tenant.memberCount}</TableCell>
                    <TableCell>{tenant.attendanceToday}</TableCell>
                    <TableCell>{tenant.failedAttemptsToday}</TableCell>
                    <TableCell>
                      <div className="flex flex-col items-start gap-2">
                        <Badge variant="muted">{tenant.subscriptionPlan ?? "Tidak ada"}</Badge>
                        {tenant.subscriptionStatus ? (
                          <SubscriptionStatusBadge status={tenant.subscriptionStatus} />
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell>
                      <TenantStatusBadge status={tenant.status} />
                    </TableCell>
                    <TableCell>{formatDateTime(tenant.createdAt)}</TableCell>
                    <TableCell>
                      <Button asChild variant="outline" size="sm">
                        <Link href={`/super-admin/tenants/${tenant.tenantId}`}>
                          <Eye className="mr-2 h-4 w-4" />
                          View
                        </Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          <div className="flex flex-col gap-3 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <p>
              Halaman {tenants.data.page + 1} dari {Math.max(tenants.data.totalPages, 1)} · {tenants.data.totalItems} tenant
            </p>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>
                Sebelumnya
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= tenants.data.totalPages}
                onClick={() => setPage((value) => value + 1)}
              >
                Berikutnya
              </Button>
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
