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
        <h1 className="text-2xl font-semibold tracking-normal">Anggota</h1>
        <p className="text-sm text-muted-foreground">Daftar user yang terhubung ke tenant ini.</p>
      </div>
      {memberships.isLoading ? <LoadingState label="Memuat anggota..." /> : null}
      {memberships.isError ? <ErrorState message={getErrorMessage(memberships.error)} /> : null}
      {memberships.isSuccess && !memberships.data.length ? (
        <EmptyState title="Belum ada anggota" description="Anggota tenant akan tampil di sini setelah user ditambahkan." />
      ) : null}
      {memberships.isSuccess && memberships.data.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nama dan email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Status akun</TableHead>
                <TableHead>Member ID</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {memberships.data.map((membership) => (
                <TableRow key={membership.membershipId}>
                  <TableCell>
                    <div className="font-semibold">{displayName(membership.fullName)}</div>
                    <div className="text-sm text-muted-foreground">{displayEmail(membership.email, membership.userId)}</div>
                  </TableCell>
                  <TableCell>
                    <RoleBadge role={membership.role} />
                  </TableCell>
                  <TableCell>
                    <ActiveBadge active={membership.active} />
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">{shortId(membership.membershipId)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}
