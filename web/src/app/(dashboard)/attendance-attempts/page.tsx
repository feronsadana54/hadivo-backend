"use client";

import { useState } from "react";
import { AttemptReasonBadge } from "@/components/attendance/status-badge";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAttempts } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { displayEmail, displayName, formatDateTime } from "@/lib/utils";

function toStart(date: string) {
  return new Date(`${date}T00:00:00`).toISOString();
}

function toEnd(date: string) {
  return new Date(`${date}T23:59:59`).toISOString();
}

function typeLabel(type: string) {
  return type === "CLOCK_IN" ? "Clock-in" : "Clock-out";
}

export default function AttendanceAttemptsPage() {
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const attempts = useAttempts(toStart(date), toEnd(date));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Percobaan Absensi</h1>
        <p className="text-sm text-muted-foreground">Absensi yang belum berhasil, misalnya karena lokasi di luar area.</p>
      </div>
      <div className="max-w-xs space-y-2">
        <Label htmlFor="date">Tanggal</Label>
        <Input id="date" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
      </div>
      {attempts.isLoading ? <LoadingState label="Memuat percobaan absensi..." /> : null}
      {attempts.isError ? <ErrorState message={getErrorMessage(attempts.error)} /> : null}
      {attempts.isSuccess && !attempts.data.length ? (
        <EmptyState title="Belum ada percobaan gagal" description="Jika ada absensi yang ditolak sistem, datanya akan tampil di sini." />
      ) : null}
      {attempts.isSuccess && attempts.data.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Waktu</TableHead>
                <TableHead>User</TableHead>
                <TableHead>Jenis</TableHead>
                <TableHead>Alasan</TableHead>
                <TableHead>Perangkat</TableHead>
                <TableHead>Lokasi</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {attempts.data.map((attempt) => (
                <TableRow key={attempt.attemptId}>
                  <TableCell>{formatDateTime(attempt.createdAt)}</TableCell>
                  <TableCell>
                    <div className="font-medium">{displayName(attempt.fullName)}</div>
                    <div className="text-xs text-muted-foreground">{displayEmail(attempt.email, attempt.userId)}</div>
                  </TableCell>
                  <TableCell>{typeLabel(attempt.type)}</TableCell>
                  <TableCell>
                    <div className="flex flex-col items-start gap-1">
                      <AttemptReasonBadge reason={attempt.reason} />
                      <Badge variant="muted">{attempt.reason}</Badge>
                    </div>
                  </TableCell>
                  <TableCell>{attempt.deviceId ?? "-"}</TableCell>
                  <TableCell>
                    {attempt.latitude != null && attempt.longitude != null
                      ? `${attempt.latitude.toFixed(5)}, ${attempt.longitude.toFixed(5)}`
                      : "-"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}
