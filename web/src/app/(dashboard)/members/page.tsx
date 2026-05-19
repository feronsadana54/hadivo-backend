"use client";

import { ActiveBadge, RoleBadge } from "@/components/attendance/status-badge";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useMemberships } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { displayEmail, displayName, shortId } from "@/lib/utils";

export default function MembersPage() {
  const memberships = useMemberships();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Members</h1>
        <p className="text-sm text-muted-foreground">Tenant membership roster and role status.</p>
      </div>
      {memberships.isLoading ? <LoadingState label="Loading members" /> : null}
      {memberships.isError ? <ErrorState message={getErrorMessage(memberships.error)} /> : null}
      {memberships.isSuccess && !memberships.data.length ? (
        <EmptyState title="No members yet" description="Memberships will appear after users join this tenant." />
      ) : null}
      {memberships.isSuccess && memberships.data.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Active status</TableHead>
                <TableHead>Member ID</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {memberships.data.map((membership) => (
                <TableRow key={membership.membershipId}>
                  <TableCell className="font-medium">{displayName(membership.fullName)}</TableCell>
                  <TableCell>{displayEmail(membership.email, membership.userId)}</TableCell>
                  <TableCell>
                    <RoleBadge role={membership.role} />
                  </TableCell>
                  <TableCell>
                    <ActiveBadge active={membership.active} />
                  </TableCell>
                  <TableCell>Member ID: {shortId(membership.membershipId)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}
