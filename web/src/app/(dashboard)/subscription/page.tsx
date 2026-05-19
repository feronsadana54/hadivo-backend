"use client";

import { CalendarDays, Users, WalletCards } from "lucide-react";
import { SubscriptionStatusBadge } from "@/components/attendance/status-badge";
import { SummaryCard } from "@/components/dashboard/summary-card";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { useSubscription } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";

export default function SubscriptionPage() {
  const subscription = useSubscription();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Subscription</h1>
        <p className="text-sm text-muted-foreground">Current tenant plan and capacity.</p>
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
    </div>
  );
}
