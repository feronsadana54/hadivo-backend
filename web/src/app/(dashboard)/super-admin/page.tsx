"use client";

import { AlertTriangle, Building2, CalendarClock, ShieldCheck, Users, WalletCards } from "lucide-react";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { subscriptionStatusLabel } from "@/components/attendance/status-badge";
import { SummaryCard } from "@/components/dashboard/summary-card";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorState, LoadingState } from "@/components/ui/state";
import { useSuperAdminOverview } from "@/hooks/use-api";
import { getErrorMessage, isForbiddenError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";
import type { SubscriptionStatus } from "@/types/api";

const subscriptionColors: Record<SubscriptionStatus, string> = {
  ACTIVE: "#047857",
  EXPIRED: "#B45309",
  CANCELLED: "#B91C1C",
};

export default function SuperAdminOverviewPage() {
  const overview = useSuperAdminOverview();

  if (overview.isLoading) {
    return <LoadingState label="Memuat Super Admin Console..." />;
  }

  if (overview.isError) {
    return (
      <ErrorState
        title="Akses Super Admin ditolak"
        message={
          isForbiddenError(overview.error)
            ? "Anda tidak memiliki akses ke halaman Super Admin."
            : getErrorMessage(overview.error)
        }
      />
    );
  }

  const data = overview.data;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-normal">Super Admin Console</h1>
          <p className="text-sm text-muted-foreground">Pantauan read-only seluruh tenant Hadivo.</p>
        </div>
        {data?.generatedAt ? (
          <p className="text-sm text-muted-foreground">Diperbarui {formatDateTime(data.generatedAt)}</p>
        ) : null}
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
        <SummaryCard title="Total tenants" value={data?.totalTenants ?? 0} icon={Building2} />
        <SummaryCard title="Tenant aktif" value={data?.activeTenants ?? 0} icon={ShieldCheck} />
        <SummaryCard title="Total anggota" value={data?.totalMembers ?? 0} icon={Users} />
        <SummaryCard title="Absensi hari ini" value={data?.attendanceToday ?? 0} icon={CalendarClock} />
        <SummaryCard title="Percobaan gagal" value={data?.failedAttemptsToday ?? 0} icon={AlertTriangle} />
        <SummaryCard title="Subscription aktif" value={data?.activeSubscriptions ?? 0} icon={WalletCards} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <TenantTypeChart company={data?.companyTenants ?? 0} school={data?.schoolTenants ?? 0} />
        <SubscriptionChart counts={data?.subscriptionStatusCounts ?? {}} />
      </div>
    </div>
  );
}

function TenantTypeChart({ company, school }: { company: number; school: number }) {
  const data = [
    { name: "Perusahaan", value: company, color: "#0F766E" },
    { name: "Sekolah", value: school, color: "#2563EB" },
  ];
  const hasData = data.some((item) => item.value > 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Jenis tenant</CardTitle>
        <CardDescription>Distribusi tenant perusahaan dan sekolah.</CardDescription>
      </CardHeader>
      <CardContent className="h-72">
        {!hasData ? (
          <ChartEmptyState title="Belum ada data tenant" description="Grafik akan tampil setelah tenant tersedia." />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} layout="vertical" margin={{ left: 16, right: 16 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
              <YAxis dataKey="name" type="category" width={96} tickLine={false} axisLine={false} />
              <Tooltip />
              <Bar dataKey="value" name="Tenant" radius={[0, 4, 4, 0]}>
                {data.map((item) => (
                  <Cell key={item.name} fill={item.color} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}

function SubscriptionChart({ counts }: { counts: Partial<Record<SubscriptionStatus, number>> }) {
  const statuses: SubscriptionStatus[] = ["ACTIVE", "EXPIRED", "CANCELLED"];
  const data = statuses.map((status) => ({
    name: subscriptionStatusLabel(status),
    status,
    value: counts[status] ?? 0,
    color: subscriptionColors[status],
  }));
  const hasData = data.some((item) => item.value > 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Status subscription</CardTitle>
        <CardDescription>Distribusi status subscription tenant.</CardDescription>
      </CardHeader>
      <CardContent className="h-72">
        {!hasData ? (
          <ChartEmptyState title="Belum ada data subscription" description="Grafik akan tampil setelah subscription tersedia." />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} layout="vertical" margin={{ left: 16, right: 16 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
              <YAxis dataKey="name" type="category" width={112} tickLine={false} axisLine={false} />
              <Tooltip />
              <Bar dataKey="value" name="Subscription" radius={[0, 4, 4, 0]}>
                {data.map((item) => (
                  <Cell key={item.status} fill={item.color} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}

function ChartEmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center text-center">
      <p className="text-base font-semibold">{title}</p>
      <p className="mt-2 max-w-sm text-sm leading-6 text-muted-foreground">{description}</p>
    </div>
  );
}
