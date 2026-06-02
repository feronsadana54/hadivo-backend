"use client";

import { FormEvent, useMemo, useState } from "react";
import { CheckCircle2, ClipboardCheck, FilterX, Plus, X, XCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  useApproveLeaveRequest,
  useCancelLeaveRequest,
  useCreateLeaveRequest,
  useLeaveRequests,
  useRejectLeaveRequest,
} from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { displayName, formatDateTime } from "@/lib/utils";
import type { LeaveRequest, LeaveRequestStatus, LeaveRequestType } from "@/types/api";

const TYPE_LABEL: Record<LeaveRequestType, string> = {
  SICK: "Sakit",
  PERMISSION: "Izin",
  ANNUAL_LEAVE: "Cuti",
  BUSINESS_TRIP: "Dinas luar",
  ATTENDANCE_CORRECTION: "Koreksi absensi",
};

const STATUS_LABEL: Record<LeaveRequestStatus, string> = {
  PENDING: "Menunggu",
  APPROVED: "Disetujui",
  REJECTED: "Ditolak",
  CANCELLED: "Dibatalkan",
};

const STATUS_VARIANT: Record<LeaveRequestStatus, "default" | "muted" | "success" | "warning" | "danger" | "info"> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger",
  CANCELLED: "muted",
};

const TYPE_VARIANT: Record<LeaveRequestType, "default" | "muted" | "success" | "warning" | "danger" | "info"> = {
  SICK: "danger",
  PERMISSION: "info",
  ANNUAL_LEAVE: "success",
  BUSINESS_TRIP: "default",
  ATTENDANCE_CORRECTION: "warning",
};

const today = new Date().toISOString().slice(0, 10);

type Filters = {
  status: LeaveRequestStatus | "";
  type: LeaveRequestType | "";
  from: string;
  to: string;
};

const defaultFilters: Filters = { status: "", type: "", from: "", to: "" };

type FormState = {
  requestType: LeaveRequestType;
  startDate: string;
  endDate: string;
  reason: string;
  requestedClockInTime: string;
  requestedClockOutTime: string;
};

const defaultForm: FormState = {
  requestType: "PERMISSION",
  startDate: today,
  endDate: today,
  reason: "",
  requestedClockInTime: "",
  requestedClockOutTime: "",
};

export default function LeaveRequestsPage() {
  const list = useLeaveRequests();
  const createMutation = useCreateLeaveRequest();
  const approveMutation = useApproveLeaveRequest();
  const rejectMutation = useRejectLeaveRequest();
  const cancelMutation = useCancelLeaveRequest();

  const [filters, setFilters] = useState<Filters>(defaultFilters);
  const [form, setForm] = useState<FormState>(defaultForm);
  const [reviewNote, setReviewNote] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);

  const rows = useMemo(() => applyFilters(list.data ?? [], filters), [list.data, filters]);

  const isForbidden =
    forbidden || (list.isError && (list.error as { response?: { status?: number } })?.response?.status === 403);

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(null);
    try {
      const payload = buildCreatePayload(form);
      await createMutation.mutateAsync(payload);
      setForm(defaultForm);
      setMessage("Pengajuan berhasil dikirim.");
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status === 403) setForbidden(true);
      setMessage(getErrorMessage(error));
    }
  }

  async function handleApprove(id: string) {
    setMessage(null);
    try {
      await approveMutation.mutateAsync({ requestId: id, payload: { reviewNote: reviewNote[id] || undefined } });
      setMessage("Pengajuan disetujui.");
    } catch (error) {
      setMessage(getErrorMessage(error));
    }
  }

  async function handleReject(id: string) {
    setMessage(null);
    try {
      await rejectMutation.mutateAsync({ requestId: id, payload: { reviewNote: reviewNote[id] || undefined } });
      setMessage("Pengajuan ditolak.");
    } catch (error) {
      setMessage(getErrorMessage(error));
    }
  }

  async function handleCancel(id: string) {
    setMessage(null);
    try {
      await cancelMutation.mutateAsync(id);
      setMessage("Pengajuan dibatalkan.");
    } catch (error) {
      setMessage(getErrorMessage(error));
    }
  }

  if (isForbidden) {
    return (
      <div className="space-y-6">
        <header>
          <h1 className="text-2xl font-semibold tracking-normal">Pengajuan</h1>
          <p className="text-sm text-muted-foreground">Daftar pengajuan izin, sakit, cuti, dan koreksi absensi.</p>
        </header>
        <ErrorState title="Akses ditolak" message="Anda tidak memiliki akses ke halaman pengajuan." />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-normal">Pengajuan</h1>
        <p className="text-sm text-muted-foreground">Daftar pengajuan izin, sakit, cuti, dan koreksi absensi.</p>
      </header>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ClipboardCheck className="h-5 w-5" aria-hidden="true" />
              Daftar pengajuan
            </CardTitle>
            <CardDescription>Filter berdasarkan status, jenis, dan rentang tanggal.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-3 lg:grid-cols-5">
              <div className="space-y-2">
                <Label htmlFor="filter-status">Status</Label>
                <select
                  id="filter-status"
                  className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={filters.status}
                  onChange={(event) =>
                    setFilters((current) => ({ ...current, status: event.target.value as Filters["status"] }))
                  }
                >
                  <option value="">Semua</option>
                  {(Object.keys(STATUS_LABEL) as LeaveRequestStatus[]).map((status) => (
                    <option key={status} value={status}>
                      {STATUS_LABEL[status]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="filter-type">Jenis</Label>
                <select
                  id="filter-type"
                  className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={filters.type}
                  onChange={(event) =>
                    setFilters((current) => ({ ...current, type: event.target.value as Filters["type"] }))
                  }
                >
                  <option value="">Semua</option>
                  {(Object.keys(TYPE_LABEL) as LeaveRequestType[]).map((type) => (
                    <option key={type} value={type}>
                      {TYPE_LABEL[type]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="filter-from">Dari</Label>
                <Input
                  id="filter-from"
                  type="date"
                  value={filters.from}
                  onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="filter-to">Sampai</Label>
                <Input
                  id="filter-to"
                  type="date"
                  value={filters.to}
                  onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))}
                />
              </div>
              <div className="flex items-end">
                <Button type="button" variant="outline" className="w-full" onClick={() => setFilters(defaultFilters)}>
                  <FilterX className="mr-2 h-4 w-4" />
                  Reset
                </Button>
              </div>
            </div>

            {message ? <p className="text-sm text-muted-foreground">{message}</p> : null}
            {list.isLoading ? <LoadingState label="Memuat pengajuan..." /> : null}
            {list.isError && !isForbidden ? <ErrorState message={getErrorMessage(list.error)} /> : null}
            {list.isSuccess && !rows.length ? (
              <EmptyState
                title="Belum ada pengajuan izin."
                description="Buat pengajuan baru atau ubah filter untuk melihat data lain."
              />
            ) : null}

            {rows.length ? (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Pemohon</TableHead>
                      <TableHead>Jenis</TableHead>
                      <TableHead>Periode</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Catatan</TableHead>
                      <TableHead>Aksi</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>
                          <div className="font-medium">{displayName(row.requesterFullName)}</div>
                          <div className="text-xs text-muted-foreground">{row.requesterEmail ?? "-"}</div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={TYPE_VARIANT[row.requestType]}>{TYPE_LABEL[row.requestType]}</Badge>
                        </TableCell>
                        <TableCell className="whitespace-nowrap">
                          <div>{row.startDate}</div>
                          <div className="text-xs text-muted-foreground">s.d. {row.endDate}</div>
                          {row.requestType === "ANNUAL_LEAVE" ? (
                            <div className="text-xs text-muted-foreground">
                              {typeof row.leaveDays === "number"
                                ? `${row.leaveDays} hari kerja`
                                : `${leaveDays(row.startDate, row.endDate)} hari`}
                            </div>
                          ) : null}
                        </TableCell>
                        <TableCell>
                          <Badge variant={STATUS_VARIANT[row.status]}>{STATUS_LABEL[row.status]}</Badge>
                          {row.reviewedAt ? (
                            <div className="mt-1 text-xs text-muted-foreground">
                              oleh {displayName(row.reviewerFullName)}
                            </div>
                          ) : null}
                        </TableCell>
                        <TableCell className="max-w-xs">
                          {row.reason ? (
                            <p className="text-sm" title={row.reason}>
                              {row.reason}
                            </p>
                          ) : (
                            <span className="text-xs text-muted-foreground">-</span>
                          )}
                          {row.reviewNote ? (
                            <p className="mt-1 text-xs text-muted-foreground">Catatan: {row.reviewNote}</p>
                          ) : null}
                          {row.requestType === "ATTENDANCE_CORRECTION" && row.status === "APPROVED" ? (
                            <p className="mt-1 text-xs text-emerald-700">
                              Koreksi ini sudah diterapkan ke data absensi.
                            </p>
                          ) : null}
                        </TableCell>
                        <TableCell>
                          {row.status === "PENDING" ? (
                            <div className="flex flex-col gap-2">
                              <Input
                                value={reviewNote[row.id] ?? ""}
                                onChange={(event) =>
                                  setReviewNote((current) => ({ ...current, [row.id]: event.target.value }))
                                }
                                placeholder="Catatan reviewer (opsional)"
                                className="h-9"
                              />
                              <div className="flex flex-wrap gap-2">
                                <Button
                                  type="button"
                                  size="sm"
                                  onClick={() => handleApprove(row.id)}
                                  disabled={approveMutation.isPending}
                                >
                                  <CheckCircle2 className="mr-2 h-4 w-4" />
                                  Setujui
                                </Button>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  onClick={() => handleReject(row.id)}
                                  disabled={rejectMutation.isPending}
                                >
                                  <XCircle className="mr-2 h-4 w-4" />
                                  Tolak
                                </Button>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  onClick={() => handleCancel(row.id)}
                                  disabled={cancelMutation.isPending}
                                >
                                  <X className="mr-2 h-4 w-4" />
                                  Batalkan
                                </Button>
                              </div>
                            </div>
                          ) : (
                            <span className="text-xs text-muted-foreground">{formatDateTime(row.reviewedAt)}</span>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Buat pengajuan baru</CardTitle>
            <CardDescription>Untuk koreksi absensi, isi minimal salah satu jam.</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={submitCreate}>
              <div className="space-y-2">
                <Label htmlFor="form-type">Jenis</Label>
                <select
                  id="form-type"
                  className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={form.requestType}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, requestType: event.target.value as LeaveRequestType }))
                  }
                >
                  {(Object.keys(TYPE_LABEL) as LeaveRequestType[]).map((type) => (
                    <option key={type} value={type}>
                      {TYPE_LABEL[type]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="form-start">Tanggal mulai</Label>
                  <Input
                    id="form-start"
                    type="date"
                    value={form.startDate}
                    onChange={(event) => setForm((current) => ({ ...current, startDate: event.target.value }))}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="form-end">Tanggal selesai</Label>
                  <Input
                    id="form-end"
                    type="date"
                    value={form.endDate}
                    onChange={(event) => setForm((current) => ({ ...current, endDate: event.target.value }))}
                    required
                  />
                </div>
              </div>
              {form.requestType === "ATTENDANCE_CORRECTION" ? (
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="form-clock-in">Clock-in (opsional)</Label>
                    <Input
                      id="form-clock-in"
                      type="time"
                      value={form.requestedClockInTime}
                      onChange={(event) =>
                        setForm((current) => ({ ...current, requestedClockInTime: event.target.value }))
                      }
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="form-clock-out">Clock-out (opsional)</Label>
                    <Input
                      id="form-clock-out"
                      type="time"
                      value={form.requestedClockOutTime}
                      onChange={(event) =>
                        setForm((current) => ({ ...current, requestedClockOutTime: event.target.value }))
                      }
                    />
                  </div>
                </div>
              ) : null}
              <div className="space-y-2">
                <Label htmlFor="form-reason">Alasan</Label>
                <textarea
                  id="form-reason"
                  className="min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  value={form.reason}
                  onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))}
                  placeholder="Tulis alasan pengajuan"
                  required={form.requestType !== "ATTENDANCE_CORRECTION"}
                />
              </div>
              <Button type="submit" className="w-full" disabled={createMutation.isPending}>
                <Plus className="mr-2 h-4 w-4" />
                Kirim pengajuan
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function applyFilters(rows: LeaveRequest[], filters: Filters): LeaveRequest[] {
  return rows.filter((row) => {
    if (filters.status && row.status !== filters.status) return false;
    if (filters.type && row.requestType !== filters.type) return false;
    if (filters.from && row.endDate < filters.from) return false;
    if (filters.to && row.startDate > filters.to) return false;
    return true;
  });
}

function buildCreatePayload(form: FormState) {
  const payload: {
    requestType: LeaveRequestType;
    startDate: string;
    endDate: string;
    reason?: string;
    requestedClockInTime?: string;
    requestedClockOutTime?: string;
  } = {
    requestType: form.requestType,
    startDate: form.startDate,
    endDate: form.endDate,
  };
  if (form.reason.trim()) payload.reason = form.reason.trim();
  if (form.requestType === "ATTENDANCE_CORRECTION") {
    if (form.requestedClockInTime) {
      payload.requestedClockInTime = combineDateTime(form.startDate, form.requestedClockInTime);
    }
    if (form.requestedClockOutTime) {
      payload.requestedClockOutTime = combineDateTime(form.startDate, form.requestedClockOutTime);
    }
  }
  return payload;
}

function combineDateTime(date: string, time: string): string {
  return new Date(`${date}T${time}:00`).toISOString();
}

function leaveDays(start: string, end: string): number {
  const startMs = Date.parse(start);
  const endMs = Date.parse(end);
  if (Number.isNaN(startMs) || Number.isNaN(endMs)) return 1;
  const diff = Math.round((endMs - startMs) / (1000 * 60 * 60 * 24)) + 1;
  return Math.max(1, diff);
}
