"use client";

import { useMemo, useState } from "react";
import { Download, FileSpreadsheet, FileText } from "lucide-react";
import { AttendanceStatusBadge, attendanceStatusLabel } from "@/components/attendance/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useDailyReport } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { downloadAttendanceExcelReport, downloadAttendancePdfReport, downloadCsvReport } from "@/lib/api/services";
import { defaultTenantId } from "@/lib/config/env";
import { Badge } from "@/components/ui/badge";
import { displayEmail, displayName, formatDateTime, formatMinutes } from "@/lib/utils";
import type { AttendanceStatus, LeaveRequestType } from "@/types/api";

const LEAVE_TYPE_LABEL: Record<LeaveRequestType, string> = {
  SICK: "Sakit",
  PERMISSION: "Izin",
  ANNUAL_LEAVE: "Cuti",
  BUSINESS_TRIP: "Dinas luar",
  ATTENDANCE_CORRECTION: "Koreksi absensi",
};

const statuses: Array<"ALL" | AttendanceStatus> = ["ALL", "ON_TIME", "LATE", "COMPLETED", "EARLY_LEAVE"];
const currentDate = new Date().toISOString().slice(0, 10);
type ExportFormat = "csv" | "excel" | "pdf";

const maxExportRangeDays = 31;

export default function AttendancePage() {
  const [fromDate, setFromDate] = useState(currentDate);
  const [toDate, setToDate] = useState(currentDate);
  const [status, setStatus] = useState<"ALL" | AttendanceStatus>("ALL");
  const [exportingFormat, setExportingFormat] = useState<ExportFormat | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const reportDate = fromDate || currentDate;
  const report = useDailyReport(reportDate);

  const rows = useMemo(() => {
    const data = report.data?.rows ?? [];
    return status === "ALL" ? data : data.filter((row) => row.status === status);
  }, [report.data?.rows, status]);

  async function handleExport(format: ExportFormat) {
    setExportError(null);
    if (!fromDate || !toDate) {
      setExportError("Pilih tanggal awal dan tanggal akhir terlebih dahulu.");
      return;
    }
    if (fromDate > toDate) {
      setExportError("Tanggal awal tidak boleh lebih baru dari tanggal akhir.");
      return;
    }
    if (getInclusiveDateRangeDays(fromDate, toDate) > maxExportRangeDays) {
      setExportError("Range laporan maksimal 31 hari.");
      return;
    }

    try {
      setExportingFormat(format);
      if (format === "excel") {
        await downloadAttendanceExcelReport(defaultTenantId, fromDate, toDate);
      } else if (format === "pdf") {
        await downloadAttendancePdfReport(defaultTenantId, fromDate, toDate);
      } else {
        await downloadCsvReport(defaultTenantId, fromDate, toDate);
      }
    } catch {
      setExportError("Laporan belum bisa diunduh. Coba lagi beberapa saat.");
    } finally {
      setExportingFormat(null);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Data Absensi</h1>
        <p className="text-sm text-muted-foreground">Lihat absensi harian dan unduh laporan CSV, Excel, atau PDF.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Filter data</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2 xl:grid-cols-4 xl:items-end">
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
          <div className="space-y-2 md:col-span-2 xl:col-span-4">
            <p className="text-sm font-medium">Unduh laporan</p>
            <div className="grid gap-2 sm:grid-cols-3 xl:flex xl:flex-wrap">
              <Button
                className="w-full gap-2 xl:w-auto"
                disabled={Boolean(exportingFormat)}
                onClick={() => handleExport("csv")}
              >
                <Download className="h-4 w-4" aria-hidden="true" />
                {exportingFormat === "csv" ? "Menyiapkan CSV..." : "Unduh CSV"}
              </Button>
              <Button
                className="w-full gap-2 xl:w-auto"
                disabled={Boolean(exportingFormat)}
                onClick={() => handleExport("excel")}
                variant="outline"
              >
                <FileSpreadsheet className="h-4 w-4" aria-hidden="true" />
                {exportingFormat === "excel" ? "Menyiapkan Excel..." : "Unduh Excel"}
              </Button>
              <Button
                className="w-full gap-2 xl:w-auto"
                disabled={Boolean(exportingFormat)}
                onClick={() => handleExport("pdf")}
                variant="outline"
              >
                <FileText className="h-4 w-4" aria-hidden="true" />
                {exportingFormat === "pdf" ? "Menyiapkan PDF..." : "Unduh PDF"}
              </Button>
            </div>
          </div>
          {exportError ? <p className="text-sm text-destructive md:col-span-2 xl:col-span-4">{exportError}</p> : null}
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
                <TableHead>Shift</TableHead>
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
                    <div className="flex flex-col gap-1">
                      {row.status ? (
                        <AttendanceStatusBadge status={row.status} />
                      ) : (
                        <span className="text-xs text-muted-foreground">Tanpa absensi</span>
                      )}
                      {row.leaveType ? (
                        <Badge variant="info">{LEAVE_TYPE_LABEL[row.leaveType]}</Badge>
                      ) : null}
                      {row.correctionApplied ? (
                        <Badge variant="warning">Dikoreksi</Badge>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="font-medium">{row.shiftName ?? "Jadwal tenant"}</div>
                    <div className="text-xs text-muted-foreground">
                      {timeLabel(row.scheduledStartTime)} - {timeLabel(row.scheduledEndTime)}
                    </div>
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

function timeLabel(value?: string | null) {
  return value ? value.slice(0, 5) : "-";
}

function getInclusiveDateRangeDays(from: string, to: string) {
  const fromTime = Date.parse(`${from}T00:00:00Z`);
  const toTime = Date.parse(`${to}T00:00:00Z`);
  return Math.floor((toTime - fromTime) / 86_400_000) + 1;
}
