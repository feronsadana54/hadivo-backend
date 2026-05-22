"use client";

import { AlertTriangle, CheckCircle2, Clock3, Timer, Users } from "lucide-react";
import { AttendanceChart } from "@/components/dashboard/attendance-chart";
import { SummaryCard } from "@/components/dashboard/summary-card";
import { ErrorState, LoadingState } from "@/components/ui/state";
import { useAttempts, useDailyReport, useMemberships, useMonthlyReport } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";

function today() {
  return new Date().toISOString().slice(0, 10);
}

function month() {
  return new Date().toISOString().slice(0, 7);
}

function dayRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date();
  end.setHours(23, 59, 59, 999);
  return { from: start.toISOString(), to: end.toISOString() };
}

export default function DashboardPage() {
  const date = today();
  const range = dayRange();
  const members = useMemberships();
  const daily = useDailyReport(date);
  const monthly = useMonthlyReport(month());
  const attempts = useAttempts(range.from, range.to);

  if (members.isLoading || daily.isLoading || monthly.isLoading || attempts.isLoading) {
    return <LoadingState label="Memuat dashboard..." />;
  }

  if (members.isError || daily.isError || monthly.isError || attempts.isError) {
    return (
      <ErrorState
        message={getErrorMessage(members.error ?? daily.error ?? monthly.error ?? attempts.error)}
      />
    );
  }

  const totals = daily.data?.totals ?? {};

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Dashboard</h1>
        <p className="text-sm text-muted-foreground">Ringkasan absensi hari ini dan bulan berjalan.</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <SummaryCard title="Total anggota" value={members.data?.length ?? 0} icon={Users} note="Semua user yang terdaftar di tenant ini." />
        <SummaryCard title="Sudah absen hari ini" value={daily.data?.rows.length ?? 0} icon={Clock3} note="Jumlah user dengan record absensi hari ini." />
        <SummaryCard title="Tepat waktu" value={totals.ON_TIME ?? 0} icon={CheckCircle2} note="Clock-in sesuai aturan jam masuk." />
        <SummaryCard title="Terlambat" value={totals.LATE ?? 0} icon={Timer} note="Clock-in melewati batas toleransi." />
        <SummaryCard title="Percobaan gagal" value={attempts.data?.length ?? 0} icon={AlertTriangle} note="Absensi yang ditolak sistem hari ini." />
      </div>
      <AttendanceChart report={monthly.data} />
    </div>
  );
}
