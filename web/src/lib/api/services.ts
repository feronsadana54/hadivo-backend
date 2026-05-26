import { apiClient, unwrap } from "@/lib/api/client";
import { endpoints } from "@/lib/api/endpoints";
import type {
  AttendanceAttempt,
  AttendanceSettings,
  DailyReport,
  Location,
  Membership,
  MonthlyReport,
  NotificationDeliveryFilters,
  NotificationDeliveryLog,
  PageResponse,
  SuperAdminOverview,
  SuperAdminTenantDetail,
  SuperAdminTenantFilters,
  SuperAdminTenantListItem,
  Subscription,
  Tenant,
  TokenPair,
  UserDevice,
} from "@/types/api";

export const api = {
  async login(email: string, password: string) {
    return unwrap<TokenPair>(await apiClient.post(endpoints.auth.login, { email, password }));
  },
  async getTenant(tenantId: string) {
    return unwrap<Tenant>(await apiClient.get(endpoints.tenant(tenantId)));
  },
  async getMemberships(tenantId: string) {
    return unwrap<Membership[]>(await apiClient.get(endpoints.memberships(tenantId)));
  },
  async getMemberDevices(tenantId: string, userId: string) {
    return unwrap<UserDevice[]>(await apiClient.get(endpoints.memberDevices(tenantId, userId)));
  },
  async resetMemberDevices(tenantId: string, userId: string) {
    return unwrap<UserDevice[]>(await apiClient.post(endpoints.resetMemberDevices(tenantId, userId)));
  },
  async getDailyReport(tenantId: string, date: string) {
    return unwrap<DailyReport>(await apiClient.get(endpoints.dailyReport(tenantId), { params: { date } }));
  },
  async getMonthlyReport(tenantId: string, month: string) {
    return unwrap<MonthlyReport>(await apiClient.get(endpoints.monthlyReport(tenantId), { params: { month } }));
  },
  async getAttempts(tenantId: string, from: string, to: string, userId?: string) {
    return unwrap<AttendanceAttempt[]>(
      await apiClient.get(endpoints.attempts(tenantId), { params: { from, to, userId } }),
    );
  },
  async getNotificationDeliveries(tenantId: string, filters: NotificationDeliveryFilters) {
    return unwrap<PageResponse<NotificationDeliveryLog>>(
      await apiClient.get(endpoints.notificationDeliveries(tenantId), { params: filters }),
    );
  },
  async getSettings(tenantId: string) {
    return unwrap<AttendanceSettings>(await apiClient.get(endpoints.settings(tenantId)));
  },
  async updateSettings(tenantId: string, payload: Partial<AttendanceSettings>) {
    return unwrap<AttendanceSettings>(await apiClient.patch(endpoints.settings(tenantId), payload));
  },
  async getLocations(tenantId: string) {
    return unwrap<Location[]>(await apiClient.get(endpoints.locations(tenantId)));
  },
  async createLocation(tenantId: string, payload: Pick<Location, "name" | "latitude" | "longitude" | "radiusMeters">) {
    return unwrap<Location>(await apiClient.post(endpoints.locations(tenantId), payload));
  },
  async updateLocation(tenantId: string, locationId: string, payload: Partial<Location>) {
    return unwrap<Location>(await apiClient.patch(endpoints.location(tenantId, locationId), payload));
  },
  async getSubscription(tenantId: string) {
    return unwrap<Subscription>(await apiClient.get(endpoints.subscription(tenantId)));
  },
  async getSuperAdminOverview() {
    return unwrap<SuperAdminOverview>(await apiClient.get(endpoints.superAdmin.overview));
  },
  async getSuperAdminTenants(filters: SuperAdminTenantFilters) {
    return unwrap<PageResponse<SuperAdminTenantListItem>>(
      await apiClient.get(endpoints.superAdmin.tenants, { params: filters }),
    );
  },
  async getSuperAdminTenantDetail(tenantId: string) {
    return unwrap<SuperAdminTenantDetail>(await apiClient.get(endpoints.superAdmin.tenant(tenantId)));
  },
};

export async function downloadCsvReport(tenantId: string, from: string, to: string) {
  await downloadAttendanceReport(endpoints.attendanceReportCsv(tenantId), from, to, "csv");
}

export async function downloadAttendanceExcelReport(tenantId: string, from: string, to: string) {
  await downloadAttendanceReport(endpoints.attendanceReportExcel(tenantId), from, to, "xlsx");
}

export async function downloadAttendancePdfReport(tenantId: string, from: string, to: string) {
  await downloadAttendanceReport(endpoints.attendanceReportPdf(tenantId), from, to, "pdf");
}

async function downloadAttendanceReport(endpoint: string, from: string, to: string, extension: "csv" | "xlsx" | "pdf") {
  try {
    const response = await apiClient.get(endpoint, {
      params: { from, to },
      responseType: "blob",
    });
    const fallbackFilename = `hadivo-attendance-report-${from}-to-${to}.${extension}`;
    const filename = parseFilename(response.headers["content-disposition"]) ?? fallbackFilename;
    const url = window.URL.createObjectURL(response.data);
    const link = document.createElement("a");

    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error) {
    throw await normalizeDownloadError(error);
  }
}

function parseFilename(contentDisposition?: string) {
  if (!contentDisposition) return null;
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1]);
  const quotedMatch = contentDisposition.match(/filename="([^"]+)"/i);
  if (quotedMatch?.[1]) return quotedMatch[1];
  const plainMatch = contentDisposition.match(/filename=([^;]+)/i);
  return plainMatch?.[1]?.trim() ?? null;
}

async function normalizeDownloadError(error: unknown) {
  const responseData = (error as { response?: { data?: unknown } })?.response?.data;

  if (responseData instanceof Blob) {
    const text = await responseData.text();
    try {
      const parsed = JSON.parse(text) as { error?: { message?: string; code?: string } };
      return new Error(parsed.error?.message ?? parsed.error?.code ?? "Failed to export report");
    } catch {
      return new Error(text || "Failed to export report");
    }
  }

  return error;
}
