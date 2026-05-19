import { apiClient, unwrap } from "@/lib/api/client";
import { endpoints } from "@/lib/api/endpoints";
import type {
  AttendanceAttempt,
  AttendanceSettings,
  DailyReport,
  Location,
  Membership,
  MonthlyReport,
  Subscription,
  Tenant,
  TokenPair,
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
};
