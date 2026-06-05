import { apiClient, unwrap } from "@/lib/api/client";
import { endpoints } from "@/lib/api/endpoints";
import type {
  AdjustLeaveBalanceRequest,
  AttendanceAttempt,
  AttendanceSettings,
  CreateHolidayRequest,
  CreateLeaveRequestRequest,
  CreateMemberShiftAssignmentRequest,
  CreateShiftTemplateRequest,
  DailyReport,
  EnrollFaceRequest,
  FaceProfile,
  Holiday,
  LeaveBalance,
  LeavePolicy,
  LeaveRequest,
  Location,
  Membership,
  MemberShiftAssignment,
  MonthlyReport,
  NotificationDeliveryFilters,
  NotificationDeliveryLog,
  PageResponse,
  CreateSubscriptionPaymentRequest,
  ReviewLeaveRequestRequest,
  ShiftTemplate,
  SuperAdminOverview,
  SuperAdminTenantDetail,
  SuperAdminTenantFilters,
  SuperAdminTenantListItem,
  Subscription,
  SubscriptionPackage,
  SubscriptionPayment,
  Tenant,
  TokenPair,
  UpdateHolidayRequest,
  UpdateLeavePolicyRequest,
  UpdateMemberShiftAssignmentRequest,
  UpdateShiftTemplateRequest,
  UpdateWorkdaySettingsRequest,
  UserDevice,
  WorkdaySettings,
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
  async getLeaveRequests(tenantId: string) {
    return unwrap<LeaveRequest[]>(await apiClient.get(endpoints.leaveRequests(tenantId)));
  },
  async createLeaveRequest(tenantId: string, payload: CreateLeaveRequestRequest) {
    return unwrap<LeaveRequest>(await apiClient.post(endpoints.leaveRequests(tenantId), payload));
  },
  async approveLeaveRequest(tenantId: string, requestId: string, payload: ReviewLeaveRequestRequest = {}) {
    return unwrap<LeaveRequest>(
      await apiClient.post(endpoints.leaveRequestApprove(tenantId, requestId), payload),
    );
  },
  async rejectLeaveRequest(tenantId: string, requestId: string, payload: ReviewLeaveRequestRequest = {}) {
    return unwrap<LeaveRequest>(
      await apiClient.post(endpoints.leaveRequestReject(tenantId, requestId), payload),
    );
  },
  async cancelLeaveRequest(tenantId: string, requestId: string) {
    return unwrap<LeaveRequest>(await apiClient.post(endpoints.leaveRequestCancel(tenantId, requestId)));
  },
  async getLeavePolicy(tenantId: string) {
    return unwrap<LeavePolicy>(await apiClient.get(endpoints.leavePolicy(tenantId)));
  },
  async updateLeavePolicy(tenantId: string, payload: UpdateLeavePolicyRequest) {
    return unwrap<LeavePolicy>(await apiClient.put(endpoints.leavePolicy(tenantId), payload));
  },
  async getLeaveBalances(tenantId: string, year?: number) {
    return unwrap<LeaveBalance[]>(
      await apiClient.get(endpoints.leaveBalances(tenantId), { params: year ? { year } : undefined }),
    );
  },
  async getMemberLeaveBalance(tenantId: string, userId: string, year?: number) {
    return unwrap<LeaveBalance>(
      await apiClient.get(endpoints.memberLeaveBalance(tenantId, userId), {
        params: year ? { year } : undefined,
      }),
    );
  },
  async adjustLeaveBalance(tenantId: string, userId: string, payload: AdjustLeaveBalanceRequest) {
    return unwrap<LeaveBalance>(
      await apiClient.post(endpoints.memberLeaveBalanceAdjust(tenantId, userId), payload),
    );
  },
  async getMemberFaceProfile(tenantId: string, userId: string) {
    return unwrap<FaceProfile>(await apiClient.get(endpoints.memberFaceProfile(tenantId, userId)));
  },
  async enrollMemberFace(tenantId: string, userId: string, payload: EnrollFaceRequest) {
    return unwrap<FaceProfile>(
      await apiClient.post(endpoints.memberFaceProfileEnroll(tenantId, userId), payload),
    );
  },
  async resetMemberFaceProfile(tenantId: string, userId: string) {
    return unwrap<FaceProfile>(
      await apiClient.post(endpoints.memberFaceProfileReset(tenantId, userId)),
    );
  },
  async getWorkdaySettings(tenantId: string) {
    return unwrap<WorkdaySettings>(await apiClient.get(endpoints.workdaySettings(tenantId)));
  },
  async updateWorkdaySettings(tenantId: string, payload: UpdateWorkdaySettingsRequest) {
    return unwrap<WorkdaySettings>(await apiClient.put(endpoints.workdaySettings(tenantId), payload));
  },
  async listHolidays(tenantId: string, from?: string, to?: string) {
    return unwrap<Holiday[]>(
      await apiClient.get(endpoints.holidays(tenantId), {
        params: { from, to },
      }),
    );
  },
  async createHoliday(tenantId: string, payload: CreateHolidayRequest) {
    return unwrap<Holiday>(await apiClient.post(endpoints.holidays(tenantId), payload));
  },
  async updateHoliday(tenantId: string, holidayId: string, payload: UpdateHolidayRequest) {
    return unwrap<Holiday>(await apiClient.patch(endpoints.holiday(tenantId, holidayId), payload));
  },
  async getShifts(tenantId: string) {
    return unwrap<ShiftTemplate[]>(await apiClient.get(endpoints.shifts(tenantId)));
  },
  async createShift(tenantId: string, payload: CreateShiftTemplateRequest) {
    return unwrap<ShiftTemplate>(await apiClient.post(endpoints.shifts(tenantId), payload));
  },
  async updateShift(tenantId: string, shiftId: string, payload: UpdateShiftTemplateRequest) {
    return unwrap<ShiftTemplate>(await apiClient.patch(endpoints.shift(tenantId, shiftId), payload));
  },
  async getMemberShiftAssignments(tenantId: string, userId: string) {
    return unwrap<MemberShiftAssignment[]>(await apiClient.get(endpoints.memberShiftAssignments(tenantId, userId)));
  },
  async createMemberShiftAssignment(tenantId: string, userId: string, payload: CreateMemberShiftAssignmentRequest) {
    return unwrap<MemberShiftAssignment>(await apiClient.post(endpoints.memberShiftAssignments(tenantId, userId), payload));
  },
  async updateMemberShiftAssignment(
    tenantId: string,
    userId: string,
    assignmentId: string,
    payload: UpdateMemberShiftAssignmentRequest,
  ) {
    return unwrap<MemberShiftAssignment>(
      await apiClient.patch(endpoints.memberShiftAssignment(tenantId, userId, assignmentId), payload),
    );
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
  async getSubscriptionPackages(tenantId: string) {
    return unwrap<SubscriptionPackage[]>(await apiClient.get(endpoints.subscriptionPackages(tenantId)));
  },
  async getSubscriptionPayments(tenantId: string) {
    return unwrap<SubscriptionPayment[]>(await apiClient.get(endpoints.subscriptionPayments(tenantId)));
  },
  async createSubscriptionPayment(tenantId: string, payload: CreateSubscriptionPaymentRequest) {
    return unwrap<SubscriptionPayment>(await apiClient.post(endpoints.subscriptionPayments(tenantId), payload));
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
