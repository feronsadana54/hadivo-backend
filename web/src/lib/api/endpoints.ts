export const endpoints = {
  auth: {
    login: "/auth/login",
    logout: "/auth/logout",
  },
  tenant: (tenantId: string) => `/tenants/${tenantId}`,
  memberships: (tenantId: string) => `/tenants/${tenantId}/memberships`,
  dailyReport: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/daily`,
  monthlyReport: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/monthly`,
  attendanceReportCsv: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/export.csv`,
  attempts: (tenantId: string) => `/tenants/${tenantId}/attendance-attempts`,
  settings: (tenantId: string) => `/tenants/${tenantId}/attendance-settings`,
  locations: (tenantId: string) => `/tenants/${tenantId}/locations`,
  location: (tenantId: string, locationId: string) => `/tenants/${tenantId}/locations/${locationId}`,
  subscription: (tenantId: string) => `/tenants/${tenantId}/subscriptions/current`,
};
