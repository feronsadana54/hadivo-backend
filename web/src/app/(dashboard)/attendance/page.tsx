"use client";

import { useMemo, useState } from "react";
import { Download } from "lucide-react";
import { AttendanceStatusBadge } from "@/components/attendance/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useDailyReport } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { downloadCsvReport } from "@/lib/api/services";
import { defaultTenantId } from "@/lib/config/env";
import { displayEmail, displayName, formatDateTime, formatMinutes } from "@/lib/utils";
import type { AttendanceStatus } from "@/types/api";

const statuses: Array<"ALL" | AttendanceStatus> = ["ALL", "ON_TIME", "LATE", "COMPLETED", "EARLY_LEAVE"];
const currentDate = new Date().toISOString().slice(0, 10);

export default function AttendancePage() {
  const [fromDate, setFromDate] = useState(currentDate);
  const [toDate, setToDate] = useState(currentDate);
  const [status, setStatus] = useState<"ALL" | AttendanceStatus>("ALL");
  const [isExporting, setIsExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const reportDate = fromDate || currentDate;
  const report = useDailyReport(reportDate);

  const rows = useMemo(() => {
    const data = report.data?.rows ?? [];
    return status === "ALL" ? data : data.filter((row) => row.status === status);
  }, [report.data?.rows, status]);

  async function handleExport() {
    setExportError(null);
    if (!fromDate || !toDate) {
      setExportError("From date and to date are required.");
      return;
    }
    if (fromDate > toDate) {
      setExportError("From date cannot be after to date.");
      return;
    }

    try {
      setIsExporting(true);
      await downloadCsvReport(defaultTenantId, fromDate, toDate);
    } catch (error) {
      setExportError(getErrorMessage(error));
    } finally {
      setIsExporting(false);
    }
  }

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
        <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto] xl:items-end">
          <div className="space-y-2">
            <Label htmlFor="fromDate">From Date</Label>
            <Input
              id="fromDate"
              type="date"
              value={fromDate}
              onChange={(event) => setFromDate(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="toDate">To Date</Label>
            <Input id="toDate" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
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
          <Button className="w-full gap-2 xl:w-auto" disabled={isExporting} onClick={handleExport}>
            <Download className="h-4 w-4" aria-hidden="true" />
            {isExporting ? "Exporting" : "Export CSV"}
          </Button>
          {exportError ? <p className="text-sm text-destructive sm:col-span-2 xl:col-span-4">{exportError}</p> : null}
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
                <TableRow key={`${row.userId}-${row.clockInAt ?? reportDate}`}>
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
