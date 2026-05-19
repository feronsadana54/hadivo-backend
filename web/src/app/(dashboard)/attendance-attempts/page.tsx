"use client";

import { useState } from "react";
import { AttemptReasonBadge } from "@/components/attendance/status-badge";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAttempts } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { formatDateTime, shortId } from "@/lib/utils";

function toStart(date: string) {
  return new Date(`${date}T00:00:00`).toISOString();
}

function toEnd(date: string) {
  return new Date(`${date}T23:59:59`).toISOString();
}

export default function AttendanceAttemptsPage() {
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const attempts = useAttempts(toStart(date), toEnd(date));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Attendance attempts</h1>
        <p className="text-sm text-muted-foreground">Failed clock-in and clock-out attempts for audit review.</p>
      </div>
      <div className="max-w-xs space-y-2">
        <Label htmlFor="date">Date</Label>
        <Input id="date" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
      </div>
      {attempts.isLoading ? <LoadingState label="Loading attempts" /> : null}
      {attempts.isError ? <ErrorState message={getErrorMessage(attempts.error)} /> : null}
      {attempts.isSuccess && !attempts.data.length ? (
        <EmptyState title="No failed attempts" description="Out-of-radius and other rejected attempts will appear here." />
      ) : null}
      {attempts.isSuccess && attempts.data.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Time</TableHead>
                <TableHead>User</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Device</TableHead>
                <TableHead>Location</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {attempts.data.map((attempt) => (
                <TableRow key={attempt.id}>
                  <TableCell>{formatDateTime(attempt.createdAt)}</TableCell>
                  <TableCell>User ID: {shortId(attempt.userId)}</TableCell>
                  <TableCell>{attempt.type}</TableCell>
                  <TableCell>
                    <AttemptReasonBadge reason={attempt.reason} />
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
