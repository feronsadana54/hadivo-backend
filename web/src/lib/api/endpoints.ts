export const endpoints = {
  auth: {
    login: "/auth/login",
    logout: "/auth/logout",
  },
  tenant: (tenantId: string) => `/tenants/${tenantId}`,
  memberships: (tenantId: string) => `/tenants/${tenantId}/memberships`,
  memberDevices: (tenantId: string, userId: string) => `/tenants/${tenantId}/members/${userId}/devices`,
  resetMemberDevices: (tenantId: string, userId: string) => `/tenants/${tenantId}/members/${userId}/devices/reset`,
  dailyReport: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/daily`,
  monthlyReport: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/monthly`,
  attendanceReportCsv: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/export.csv`,
  attendanceReportExcel: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/export.xlsx`,
  attendanceReportPdf: (tenantId: string) => `/tenants/${tenantId}/reports/attendance/export.pdf`,
  attempts: (tenantId: string) => `/tenants/${tenantId}/attendance-attempts`,
  notificationDeliveries: (tenantId: string) => `/tenants/${tenantId}/notification-deliveries`,
  settings: (tenantId: string) => `/tenants/${tenantId}/attendance-settings`,
  shifts: (tenantId: string) => `/tenants/${tenantId}/shifts`,
  shift: (tenantId: string, shiftId: string) => `/tenants/${tenantId}/shifts/${shiftId}`,
  memberShiftAssignments: (tenantId: string, userId: string) =>
    `/tenants/${tenantId}/members/${userId}/shift-assignments`,
  memberShiftAssignment: (tenantId: string, userId: string, assignmentId: string) =>
    `/tenants/${tenantId}/members/${userId}/shift-assignments/${assignmentId}`,
  locations: (tenantId: string) => `/tenants/${tenantId}/locations`,
  location: (tenantId: string, locationId: string) => `/tenants/${tenantId}/locations/${locationId}`,
  subscription: (tenantId: string) => `/tenants/${tenantId}/subscriptions/current`,
  subscriptionPackages: (tenantId: string) => `/tenants/${tenantId}/subscription-packages`,
  subscriptionPayments: (tenantId: string) => `/tenants/${tenantId}/subscription-payments`,
  subscriptionPayment: (tenantId: string, paymentId: string) =>
    `/tenants/${tenantId}/subscription-payments/${paymentId}`,
  superAdmin: {
    overview: "/super-admin/overview",
    tenants: "/super-admin/tenants",
    tenant: (tenantId: string) => `/super-admin/tenants/${tenantId}`,
  },
};
