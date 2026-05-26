"use client";

import { useEffect, useMemo, useState } from "react";
import { CalendarDays, ExternalLink, Plus, Users, WalletCards } from "lucide-react";
import { PaymentStatusBadge, SubscriptionStatusBadge } from "@/components/attendance/status-badge";
import { SummaryCard } from "@/components/dashboard/summary-card";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  useCreateSubscriptionPayment,
  useSubscription,
  useSubscriptionPackages,
  useSubscriptionPayments,
} from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";
import type { SubscriptionPackage, SubscriptionPayment } from "@/types/api";

export default function SubscriptionPage() {
  const subscription = useSubscription();
  const packages = useSubscriptionPackages();
  const payments = useSubscriptionPayments();
  const createPayment = useCreateSubscriptionPayment();
  const [selectedPackageId, setSelectedPackageId] = useState("");
  const [createdPayment, setCreatedPayment] = useState<SubscriptionPayment | null>(null);
  const [paymentError, setPaymentError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedPackageId && packages.data?.length) {
      setSelectedPackageId(packages.data[0].id);
    }
  }, [packages.data, selectedPackageId]);

  const selectedPackage = useMemo(
    () => packages.data?.find((item) => item.id === selectedPackageId) ?? null,
    [packages.data, selectedPackageId],
  );

  async function handleCreatePayment() {
    setPaymentError(null);
    setCreatedPayment(null);
    if (!selectedPackage) {
      setPaymentError("Pilih paket subscription terlebih dahulu.");
      return;
    }

    try {
      const payment = await createPayment.mutateAsync({
        packageId: selectedPackage.id,
        billingPeriod: selectedPackage.billingPeriod,
      });
      setCreatedPayment(payment);
    } catch {
      setPaymentError("Pembayaran belum bisa dibuat. Coba lagi beberapa saat.");
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Subscription</h1>
        <p className="text-sm text-muted-foreground">Kelola paket tenant dan riwayat pembayaran.</p>
      </div>
      {subscription.isLoading ? <LoadingState label="Loading subscription" /> : null}
      {subscription.isError ? <ErrorState message={getErrorMessage(subscription.error)} /> : null}
      {subscription.isSuccess && !subscription.data ? <EmptyState title="No active subscription" /> : null}
      {subscription.data ? (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            <SummaryCard title="Plan" value={subscription.data.plan} icon={WalletCards} />
            <SummaryCard title="Max members" value={subscription.data.maxMembers} icon={Users} />
            <SummaryCard title="Started" value={formatDateTime(subscription.data.startedAt)} icon={CalendarDays} />
          </div>
          <Card>
            <CardHeader>
              <CardTitle>Plan status</CardTitle>
              <CardDescription>Read-only subscription information for this MVP.</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-wrap items-center gap-4 text-sm">
              <SubscriptionStatusBadge status={subscription.data.status} />
              <span className="text-muted-foreground">
                Expires: {subscription.data.expiresAt ? formatDateTime(subscription.data.expiresAt) : "No expiry date"}
              </span>
            </CardContent>
          </Card>
        </>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Pembayaran subscription</CardTitle>
          <CardDescription>Pilih paket yang tersedia dari backend.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {packages.isLoading ? <LoadingState label="Memuat paket subscription..." /> : null}
          {packages.isError ? <ErrorState message={getErrorMessage(packages.error)} /> : null}
          {packages.isSuccess && !packages.data.length ? <EmptyState title="Belum ada paket subscription aktif." /> : null}
          {packages.data?.length ? (
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end">
              <div className="space-y-2">
                <Label htmlFor="subscription-package">Paket</Label>
                <select
                  id="subscription-package"
                  className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={selectedPackageId}
                  onChange={(event) => setSelectedPackageId(event.target.value)}
                >
                  {packages.data.map((item) => (
                    <option key={item.id} value={item.id}>
                      {packageLabel(item)}
                    </option>
                  ))}
                </select>
              </div>
              <Button className="w-full gap-2 lg:w-auto" disabled={createPayment.isPending} onClick={handleCreatePayment}>
                <Plus className="h-4 w-4" aria-hidden="true" />
                {createPayment.isPending ? "Membuat pembayaran..." : "Buat Pembayaran"}
              </Button>
            </div>
          ) : null}
          {paymentError ? <p className="text-sm text-destructive">{paymentError}</p> : null}
          {createdPayment?.paymentUrl ? (
            <Button asChild variant="outline" className="w-full gap-2 sm:w-auto">
              <a href={createdPayment.paymentUrl} target="_blank" rel="noreferrer">
                <ExternalLink className="h-4 w-4" aria-hidden="true" />
                Buka Halaman Pembayaran
              </a>
            </Button>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Riwayat pembayaran</CardTitle>
          <CardDescription>Status pembayaran subscription tenant.</CardDescription>
        </CardHeader>
        <CardContent>
          {payments.isLoading ? <LoadingState label="Memuat riwayat pembayaran..." /> : null}
          {payments.isError ? <ErrorState message={getErrorMessage(payments.error)} /> : null}
          {payments.isSuccess && !payments.data.length ? <EmptyState title="Belum ada riwayat pembayaran." /> : null}
          {payments.data?.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Dibuat</TableHead>
                  <TableHead>Order ID</TableHead>
                  <TableHead>Provider</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Kedaluwarsa</TableHead>
                  <TableHead>Aksi</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {payments.data.map((payment) => (
                  <TableRow key={payment.paymentId}>
                    <TableCell className="min-w-36">{formatDateTime(payment.createdAt)}</TableCell>
                    <TableCell className="min-w-48 font-medium">{payment.providerOrderId}</TableCell>
                    <TableCell>{payment.provider}</TableCell>
                    <TableCell>
                      <PaymentStatusBadge status={payment.status} />
                    </TableCell>
                    <TableCell>{formatCurrency(payment.grossAmount, payment.currency)}</TableCell>
                    <TableCell>{formatDateTime(payment.expiredAt)}</TableCell>
                    <TableCell>
                      {payment.paymentUrl && payment.status === "PENDING" ? (
                        <Button asChild variant="outline" size="sm" className="gap-2">
                          <a href={payment.paymentUrl} target="_blank" rel="noreferrer">
                            <ExternalLink className="h-4 w-4" aria-hidden="true" />
                            Buka
                          </a>
                        </Button>
                      ) : (
                        <span className="text-sm text-muted-foreground">-</span>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

function packageLabel(item: SubscriptionPackage) {
  const period = item.billingPeriod === "MONTHLY" ? "Bulanan" : "Tahunan";
  return `${item.name} (${period}) - ${formatCurrency(item.grossAmount, item.currency)}`;
}

function formatCurrency(amount: number, currency: string) {
  return new Intl.NumberFormat("id-ID", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}
