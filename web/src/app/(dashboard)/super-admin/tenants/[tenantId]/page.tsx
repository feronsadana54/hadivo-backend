import { SuperAdminTenantDetailClient } from "@/components/super-admin/tenant-detail-client";

export default async function SuperAdminTenantDetailPage({
  params,
}: {
  params: Promise<{ tenantId: string }>;
}) {
  const { tenantId } = await params;
  return <SuperAdminTenantDetailClient tenantId={tenantId} />;
}
