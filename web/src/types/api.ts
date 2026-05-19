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
  | "DEVICE_MISMATCH"
  | "DUPLICATE_CLOCK_IN"
  | "NO_CLOCK_IN"
  | "ALREADY_CLOCKED_OUT"
  | "LATE_NOT_ALLOWED";
export type AttendanceType = "CLOCK_IN" | "CLOCK_OUT";
export type Role = "SUPER_ADMIN" | "TENANT_ADMIN" | "MANAGER" | "TEACHER" | "EMPLOYEE" | "STUDENT" | "PARENT";
export type SubscriptionPlan = "FREE" | "PRO" | "BUSINESS" | "ENTERPRISE";
export type SubscriptionStatus = "ACTIVE" | "EXPIRED" | "CANCELED";

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
  mode: "SCHOOL" | "COMPANY";
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
