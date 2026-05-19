"use client";

import { useMemo, useState } from "react";
import { AttendanceStatusBadge } from "@/components/attendance/status-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useDailyReport } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { displayEmail, displayName, formatDateTime, formatMinutes } from "@/lib/utils";
import type { AttendanceStatus } from "@/types/api";

const statuses: Array<"ALL" | AttendanceStatus> = ["ALL", "ON_TIME", "LATE", "COMPLETED", "EARLY_LEAVE"];

export default function AttendancePage() {
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [status, setStatus] = useState<"ALL" | AttendanceStatus>("ALL");
  const report = useDailyReport(date);

  const rows = useMemo(() => {
    const data = report.data?.rows ?? [];
    return status === "ALL" ? data : data.filter((row) => row.status === status);
  }, [report.data?.rows, status]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Attendance</h1>
        <p className="text-sm text-muted-foreground">Daily attendance records by status.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="date">Date</Label>
            <Input id="date" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="status">Status</Label>
            <select
              id="status"
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={status}
              onChange={(event) => setStatus(event.target.value as "ALL" | AttendanceStatus)}
            >
              {statuses.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </div>
        </CardContent>
      </Card>
      {report.isLoading ? <LoadingState label="Loading attendance" /> : null}
      {report.isError ? <ErrorState message={getErrorMessage(report.error)} /> : null}
      {report.isSuccess && !rows.length ? (
        <EmptyState title="No attendance records" description="Records will appear after members clock in." />
      ) : null}
      {report.isSuccess && rows.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>User</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Clock in</TableHead>
                <TableHead>Clock out</TableHead>
                <TableHead>Work duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={`${row.userId}-${row.clockInAt ?? date}`}>
                  <TableCell>
                    <div className="font-medium">{displayName(row.fullName)}</div>
                    <div className="text-xs text-muted-foreground">{displayEmail(row.email, row.userId)}</div>
                  </TableCell>
                  <TableCell>
                    <AttendanceStatusBadge status={row.status} />
                  </TableCell>
                  <TableCell>{formatDateTime(row.clockInAt)}</TableCell>
                  <TableCell>{formatDateTime(row.clockOutAt)}</TableCell>
                  <TableCell>{formatMinutes(row.workDurationMinutes)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}
