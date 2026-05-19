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
    return <LoadingState label="Loading dashboard" />;
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
        <p className="text-sm text-muted-foreground">Tenant attendance overview for today.</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <SummaryCard title="Total members" value={members.data?.length ?? 0} icon={Users} />
        <SummaryCard title="Attendance today" value={daily.data?.rows.length ?? 0} icon={Clock3} />
        <SummaryCard title="On time" value={totals.ON_TIME ?? 0} icon={CheckCircle2} />
        <SummaryCard title="Late" value={totals.LATE ?? 0} icon={Timer} />
        <SummaryCard title="Failed attempts" value={attempts.data?.length ?? 0} icon={AlertTriangle} />
      </div>
      <AttendanceChart report={monthly.data} />
    </div>
  );
}
