export type ApiResponse<T> = {
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: unknown;
  };
};

export type AttendanceStatus = "ON_TIME" | "LATE" | "COMPLETED" | "EARLY_LEAVE";
export type AttemptReason =
  | "OUT_OF_RADIUS"
  | "FACE_MISMATCH"
  | "INVALID_LOCATION"
  | "INVALID_DEVICE"
  | "DEVICE_MISMATCH"
  | "DUPLICATE_CLOCK_IN"
  | "NO_CLOCK_IN"
  | "ALREADY_CLOCKED_OUT"
  | "LATE_NOT_ALLOWED";
export type AttendanceType = "CLOCK_IN" | "CLOCK_OUT";
export type Role = "SUPER_ADMIN" | "TENANT_ADMIN" | "MANAGER" | "TEACHER" | "EMPLOYEE" | "STUDENT" | "PARENT";
export type TenantMode = "SCHOOL" | "COMPANY";
export type TenantStatus = "ACTIVE" | "INACTIVE";
export type SubscriptionPlan = "FREE" | "PRO" | "BUSINESS" | "ENTERPRISE";
export type SubscriptionStatus = "ACTIVE" | "EXPIRED" | "CANCELLED";

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type TokenPair = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
};

export type Tenant = {
  id: string;
  name: string;
  slug: string;
  mode: TenantMode;
  timezone: string;
  active: boolean;
};

export type Membership = {
  membershipId: string;
  userId: string;
  fullName?: string | null;
  email?: string | null;
  phone?: string | null;
  role: Role;
  active: boolean;
  createdAt: string;
};

export type UserDevice = {
  deviceId: string;
  deviceName?: string | null;
  platform?: string | null;
  trusted: boolean;
  active: boolean;
  firstSeenAt: string;
  lastSeenAt: string;
};

export type DailyReportRow = {
  userId: string;
  fullName?: string | null;
  email?: string | null;
  status: AttendanceStatus;
  clockInAt?: string | null;
  clockOutAt?: string | null;
  workDurationMinutes?: number | null;
  clockOutOutsideRadius: boolean;
};

export type DailyReport = {
  date: string;
  tenantId: string;
  totals: Partial<Record<AttendanceStatus, number>>;
  rows: DailyReportRow[];
};

export type MonthlyReportRow = {
  date: string;
  total: number;
  byStatus: Partial<Record<AttendanceStatus, number>>;
};

export type MonthlyReport = {
  month: string;
  tenantId: string;
  days: MonthlyReportRow[];
};

export type AttendanceAttempt = {
  attemptId: string;
  userId: string;
  fullName?: string | null;
  email?: string | null;
  type: AttendanceType;
  reason: AttemptReason;
  latitude?: number | null;
  longitude?: number | null;
  deviceId?: string | null;
  createdAt: string;
};

export type AttendanceSettings = {
  tenantId: string;
  requireFaceClockIn: boolean;
  requireFaceClockOut: boolean;
  allowClockOutOutsideRadius: boolean;
  allowLateClockIn: boolean;
  workStartTime: string;
  workEndTime: string;
  lateThresholdMinutes: number;
  timezone: string;
};

export type Location = {
  id: string;
  tenantId: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  active: boolean;
};

export type Subscription = {
  id: string;
  tenantId: string;
  plan: SubscriptionPlan;
  maxMembers: number;
  startedAt: string;
  expiresAt?: string | null;
  status: SubscriptionStatus;
};

export type SuperAdminOverview = {
  totalTenants: number;
  activeTenants: number;
  companyTenants: number;
  schoolTenants: number;
  totalMembers: number;
  attendanceToday: number;
  failedAttemptsToday: number;
  activeSubscriptions: number;
  expiredSubscriptions: number;
  subscriptionStatusCounts: Partial<Record<SubscriptionStatus, number>>;
  generatedAt: string;
};

export type SuperAdminTenantListItem = {
  tenantId: string;
  tenantName: string;
  tenantType: TenantMode;
  active: boolean;
  status: TenantStatus;
  memberCount: number;
  attendanceToday: number;
  failedAttemptsToday: number;
  subscriptionPlan?: SubscriptionPlan | null;
  subscriptionStatus?: SubscriptionStatus | null;
  createdAt: string;
};

export type SuperAdminTenantFilters = {
  type?: TenantMode;
  status?: TenantStatus;
  subscriptionStatus?: SubscriptionStatus;
  search?: string;
  page?: number;
  size?: number;
};

export type SuperAdminSubscriptionSummary = {
  plan: SubscriptionPlan;
  status: SubscriptionStatus;
  maxMembers: number;
  startedAt: string;
  expiresAt?: string | null;
};

export type SuperAdminFailedAttempt = {
  attemptId: string;
  userId: string;
  fullName?: string | null;
  email?: string | null;
  type: AttendanceType;
  reason: AttemptReason;
  createdAt: string;
};

export type SuperAdminTenantDetail = {
  tenantId: string;
  tenantName: string;
  tenantSlug: string;
  tenantType: TenantMode;
  timezone: string;
  active: boolean;
  status: TenantStatus;
  memberCount: number;
  activeMemberCount: number;
  attendanceToday: number;
  failedAttemptsToday: number;
  subscriptionCurrent?: SuperAdminSubscriptionSummary | null;
  recentFailedAttempts: SuperAdminFailedAttempt[];
  createdAt: string;
};
