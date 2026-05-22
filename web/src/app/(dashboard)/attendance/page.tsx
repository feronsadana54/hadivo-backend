"use client";

import { useMemo, useState } from "react";
import { Download } from "lucide-react";
import { AttendanceStatusBadge, attendanceStatusLabel } from "@/components/attendance/status-badge";
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
      setExportError("Pilih tanggal awal dan tanggal akhir terlebih dahulu.");
      return;
    }
    if (fromDate > toDate) {
      setExportError("Tanggal awal tidak boleh lebih baru dari tanggal akhir.");
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
        <h1 className="text-2xl font-semibold tracking-normal">Data Absensi</h1>
        <p className="text-sm text-muted-foreground">Lihat absensi harian dan unduh laporan CSV saat dibutuhkan.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Filter data</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto] xl:items-end">
          <div className="space-y-2">
            <Label htmlFor="fromDate">Tanggal awal</Label>
            <Input
              id="fromDate"
              type="date"
              value={fromDate}
              onChange={(event) => setFromDate(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="toDate">Tanggal akhir</Label>
            <Input id="toDate" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="status">Status</Label>
            <select
              id="status"
              className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={status}
              onChange={(event) => setStatus(event.target.value as "ALL" | AttendanceStatus)}
            >
              {statuses.map((item) => (
                <option key={item} value={item}>
                  {item === "ALL" ? "Semua status" : attendanceStatusLabel(item)}
                </option>
              ))}
            </select>
          </div>
          <Button className="w-full gap-2 xl:w-auto" disabled={isExporting} onClick={handleExport}>
            <Download className="h-4 w-4" aria-hidden="true" />
            {isExporting ? "Menyiapkan CSV..." : "Unduh CSV"}
          </Button>
          {exportError ? <p className="text-sm text-destructive sm:col-span-2 xl:col-span-4">{exportError}</p> : null}
        </CardContent>
      </Card>
      {report.isLoading ? <LoadingState label="Memuat data absensi..." /> : null}
      {report.isError ? <ErrorState message={getErrorMessage(report.error)} /> : null}
      {report.isSuccess && !rows.length ? (
        <EmptyState title="Belum ada data absensi untuk tanggal ini." description="Data akan muncul setelah karyawan atau siswa melakukan clock-in." />
      ) : null}
      {report.isSuccess && rows.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>User</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Jam masuk</TableHead>
                <TableHead>Jam keluar</TableHead>
                <TableHead>Durasi kerja</TableHead>
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
