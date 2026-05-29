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
export type BillingPeriod = "MONTHLY" | "YEARLY";
export type PaymentProvider = "MOCK" | "MIDTRANS";
export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "EXPIRED" | "CANCELLED";
export type NotificationEventType =
  | "CLOCK_IN_SUCCESS"
  | "CLOCK_OUT_SUCCESS"
  | "ATTENDANCE_OUT_OF_RADIUS"
  | "DEVICE_MISMATCH"
  | "ATTENDANCE_FAILED_ATTEMPT"
  | "LEAVE_REQUEST_CREATED"
  | "LEAVE_REQUEST_APPROVED"
  | "LEAVE_REQUEST_REJECTED"
  | "LEAVE_REQUEST_CANCELLED";
export type LeaveRequestType =
  | "SICK"
  | "PERMISSION"
  | "ANNUAL_LEAVE"
  | "BUSINESS_TRIP"
  | "ATTENDANCE_CORRECTION";
export type LeaveRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
export type NotificationChannel = "IN_APP" | "EMAIL" | "PUSH";
export type NotificationDeliveryStatus = "PENDING" | "SENT" | "FAILED" | "SKIPPED";

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
  status: AttendanceStatus | null;
  clockInAt?: string | null;
  clockOutAt?: string | null;
  workDurationMinutes?: number | null;
  clockOutOutsideRadius: boolean;
  shiftId?: string | null;
  shiftName?: string | null;
  scheduledStartTime?: string | null;
  scheduledEndTime?: string | null;
  lateThresholdMinutes?: number | null;
  leaveRequestId?: string | null;
  leaveType?: LeaveRequestType | null;
  leaveStatus?: LeaveRequestStatus | null;
  correctionApplied?: boolean;
  correctionRequestId?: string | null;
  correctedAt?: string | null;
};

export type DailyReport = {
  date: string;
  tenantId: string;
  totals: Partial<Record<AttendanceStatus, number>>;
  leaveTotals: Partial<Record<LeaveRequestType, number>>;
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

export type NotificationDeliveryLog = {
  id: string;
  eventType: NotificationEventType;
  channel: NotificationChannel;
  recipientUserId?: string | null;
  destination?: string | null;
  title: string;
  status: NotificationDeliveryStatus;
  provider?: string | null;
  createdAt: string;
  sentAt?: string | null;
  errorMessage?: string | null;
};

export type NotificationDeliveryFilters = {
  eventType?: NotificationEventType;
  channel?: NotificationChannel;
  status?: NotificationDeliveryStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
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

export type ShiftTemplate = {
  id: string;
  tenantId: string;
  name: string;
  startTime: string;
  endTime: string;
  lateThresholdMinutes: number;
  allowsOvertime: boolean;
  active: boolean;
  overnight: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateShiftTemplateRequest = {
  name: string;
  startTime: string;
  endTime: string;
  lateThresholdMinutes: number;
  allowsOvertime: boolean;
  active: boolean;
};

export type UpdateShiftTemplateRequest = Partial<CreateShiftTemplateRequest>;

export type MemberShiftAssignment = {
  id: string;
  tenantId: string;
  userId: string;
  shiftTemplateId: string;
  shiftName?: string | null;
  shiftStartTime?: string | null;
  shiftEndTime?: string | null;
  effectiveFrom: string;
  effectiveTo?: string | null;
  active: boolean;
  current: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateMemberShiftAssignmentRequest = {
  shiftTemplateId: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  active: boolean;
};

export type UpdateMemberShiftAssignmentRequest = Partial<CreateMemberShiftAssignmentRequest>;

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

export type SubscriptionPackage = {
  id: string;
  code: string;
  name: string;
  plan: SubscriptionPlan;
  billingPeriod: BillingPeriod;
  grossAmount: number;
  currency: string;
  durationMonths: number;
};

export type CreateSubscriptionPaymentRequest = {
  packageId: string;
  billingPeriod?: BillingPeriod;
  customerName?: string;
  customerEmail?: string;
};

export type SubscriptionPayment = {
  paymentId: string;
  provider: PaymentProvider;
  providerOrderId: string;
  status: PaymentStatus;
  grossAmount: number;
  currency: string;
  paymentUrl?: string | null;
  paidAt?: string | null;
  expiredAt?: string | null;
  createdAt: string;
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

export type LeaveRequest = {
  id: string;
  tenantId: string;
  requesterUserId: string;
  requesterFullName?: string | null;
  requesterEmail?: string | null;
  requestType: LeaveRequestType;
  startDate: string;
  endDate: string;
  reason?: string | null;
  status: LeaveRequestStatus;
  reviewerUserId?: string | null;
  reviewerFullName?: string | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  attachmentUrl?: string | null;
  requestedClockInTime?: string | null;
  requestedClockOutTime?: string | null;
  correctionNote?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateLeaveRequestRequest = {
  requestType: LeaveRequestType;
  startDate: string;
  endDate: string;
  reason?: string;
  requestedClockInTime?: string;
  requestedClockOutTime?: string;
  correctionNote?: string;
};

export type ReviewLeaveRequestRequest = {
  reviewNote?: string;
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
