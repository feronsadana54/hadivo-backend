"use client";

import { RefreshCw } from "lucide-react";
import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useNotificationDeliveries } from "@/hooks/use-api";
import { getErrorMessage, isForbiddenError } from "@/lib/api/client";
import { formatDateTime, shortId } from "@/lib/utils";
import type {
  NotificationChannel,
  NotificationDeliveryFilters,
  NotificationDeliveryLog,
  NotificationDeliveryStatus,
  NotificationEventType,
} from "@/types/api";

const eventTypes: NotificationEventType[] = [
  "CLOCK_IN_SUCCESS",
  "CLOCK_OUT_SUCCESS",
  "ATTENDANCE_OUT_OF_RADIUS",
  "DEVICE_MISMATCH",
  "ATTENDANCE_FAILED_ATTEMPT",
];

const channels: NotificationChannel[] = ["IN_APP", "EMAIL", "PUSH"];
const statuses: NotificationDeliveryStatus[] = ["PENDING", "SENT", "FAILED", "SKIPPED"];

export default function NotificationsPage() {
  const [eventType, setEventType] = useState<NotificationEventType | "">("");
  const [channel, setChannel] = useState<NotificationChannel | "">("");
  const [status, setStatus] = useState<NotificationDeliveryStatus | "">("");

  const filters = useMemo<NotificationDeliveryFilters>(
    () => ({
      eventType: eventType || undefined,
      channel: channel || undefined,
      status: status || undefined,
      page: 0,
      size: 20,
    }),
    [channel, eventType, status],
  );
  const deliveries = useNotificationDeliveries(filters);
  const rows = deliveries.data?.items ?? [];

  function resetFilters() {
    setEventType("");
    setChannel("");
    setStatus("");
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Notifikasi</h1>
        <p className="text-sm text-muted-foreground">Riwayat delivery gateway untuk event absensi tenant.</p>
      </div>

      <Card className="p-4">
        <div className="grid gap-4 md:grid-cols-4">
          <FilterSelect
            id="notification-event-type"
            label="Event"
            value={eventType}
            onChange={(value) => setEventType(value as NotificationEventType | "")}
            options={eventTypes.map((value) => ({ value, label: eventTypeLabel(value) }))}
          />
          <FilterSelect
            id="notification-channel"
            label="Channel"
            value={channel}
            onChange={(value) => setChannel(value as NotificationChannel | "")}
            options={channels.map((value) => ({ value, label: channelLabel(value) }))}
          />
          <FilterSelect
            id="notification-status"
            label="Status"
            value={status}
            onChange={(value) => setStatus(value as NotificationDeliveryStatus | "")}
            options={statuses.map((value) => ({ value, label: statusLabel(value) }))}
          />
          <div className="flex items-end">
            <Button type="button" variant="outline" onClick={resetFilters}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Reset
            </Button>
          </div>
        </div>
      </Card>

      {deliveries.isLoading ? <LoadingState label="Memuat riwayat notifikasi..." /> : null}
      {deliveries.isError ? (
        <ErrorState
          title="Riwayat notifikasi belum dapat dimuat"
          message={
            isForbiddenError(deliveries.error)
              ? "Anda tidak memiliki akses ke riwayat notifikasi."
              : getErrorMessage(deliveries.error)
          }
        />
      ) : null}
      {deliveries.isSuccess && !rows.length ? (
        <EmptyState
          title="Belum ada riwayat notifikasi."
          description="Delivery log akan tampil setelah event absensi diproses oleh notification gateway."
        />
      ) : null}
      {deliveries.isSuccess && rows.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Waktu</TableHead>
                <TableHead>Event</TableHead>
                <TableHead>Channel</TableHead>
                <TableHead>Recipient</TableHead>
                <TableHead>Judul</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Provider</TableHead>
                <TableHead>Delivery</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((delivery) => (
                <NotificationDeliveryRow key={delivery.id} delivery={delivery} />
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}

function FilterSelect({
  id,
  label,
  value,
  onChange,
  options,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: Array<{ value: string; label: string }>;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
      >
        <option value="">Semua {label.toLowerCase()}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function NotificationDeliveryRow({ delivery }: { delivery: NotificationDeliveryLog }) {
  return (
    <TableRow>
      <TableCell className="min-w-36">{formatDateTime(delivery.createdAt)}</TableCell>
      <TableCell>
        <div className="min-w-44 font-medium">{eventTypeLabel(delivery.eventType)}</div>
        <div className="text-xs text-muted-foreground">{delivery.eventType}</div>
      </TableCell>
      <TableCell>
        <Badge variant="info">{channelLabel(delivery.channel)}</Badge>
      </TableCell>
      <TableCell>
        <div className="min-w-36 text-sm">{shortId(delivery.recipientUserId)}</div>
        <div className="max-w-44 truncate text-xs text-muted-foreground">{delivery.destination ?? "-"}</div>
      </TableCell>
      <TableCell className="min-w-44 font-medium">{delivery.title}</TableCell>
      <TableCell>
        <NotificationStatusBadge status={delivery.status} />
      </TableCell>
      <TableCell>{delivery.provider ?? "-"}</TableCell>
      <TableCell>
        {delivery.errorMessage ? (
          <div className="max-w-60 text-sm text-red-700">{delivery.errorMessage}</div>
        ) : (
          <div className="text-sm text-muted-foreground">{delivery.sentAt ? formatDateTime(delivery.sentAt) : "-"}</div>
        )}
      </TableCell>
    </TableRow>
  );
}

function NotificationStatusBadge({ status }: { status: NotificationDeliveryStatus }) {
  const variant =
    status === "SENT" ? "success" : status === "FAILED" ? "danger" : status === "PENDING" ? "warning" : "muted";
  return <Badge variant={variant}>{statusLabel(status)}</Badge>;
}

function eventTypeLabel(type: NotificationEventType) {
  const labels: Record<NotificationEventType, string> = {
    CLOCK_IN_SUCCESS: "Clock-in berhasil",
    CLOCK_OUT_SUCCESS: "Clock-out berhasil",
    ATTENDANCE_OUT_OF_RADIUS: "Di luar area absensi",
    DEVICE_MISMATCH: "Perangkat tidak sesuai",
    ATTENDANCE_FAILED_ATTEMPT: "Percobaan absensi gagal",
  };
  return labels[type];
}

function channelLabel(channel: NotificationChannel) {
  const labels: Record<NotificationChannel, string> = {
    IN_APP: "In-app",
    EMAIL: "Email",
    PUSH: "Push",
  };
  return labels[channel];
}

function statusLabel(status: NotificationDeliveryStatus) {
  const labels: Record<NotificationDeliveryStatus, string> = {
    PENDING: "Pending",
    SENT: "Terkirim",
    FAILED: "Gagal",
    SKIPPED: "Dilewati",
  };
  return labels[status];
}
