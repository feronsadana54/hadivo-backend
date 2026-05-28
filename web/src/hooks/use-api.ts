"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/services";
import { defaultTenantId } from "@/lib/config/env";
import type {
  AttendanceSettings,
  CreateMemberShiftAssignmentRequest,
  CreateShiftTemplateRequest,
  CreateSubscriptionPaymentRequest,
  Location,
  NotificationDeliveryFilters,
  SuperAdminTenantFilters,
  UpdateMemberShiftAssignmentRequest,
  UpdateShiftTemplateRequest,
} from "@/types/api";

export function useTenant() {
  return useQuery({
    queryKey: ["tenant", defaultTenantId],
    queryFn: () => api.getTenant(defaultTenantId),
  });
}

export function useMemberships() {
  return useQuery({
    queryKey: ["memberships", defaultTenantId],
    queryFn: () => api.getMemberships(defaultTenantId),
  });
}

export function useMemberDevices(userId: string, enabled = true) {
  return useQuery({
    queryKey: ["member-devices", defaultTenantId, userId],
    queryFn: () => api.getMemberDevices(defaultTenantId, userId),
    enabled: Boolean(userId) && enabled,
  });
}

export function useResetMemberDevices() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => api.resetMemberDevices(defaultTenantId, userId),
    onSuccess: (_devices, userId) => {
      queryClient.invalidateQueries({ queryKey: ["member-devices", defaultTenantId, userId] });
    },
  });
}

export function useDailyReport(date: string) {
  return useQuery({
    queryKey: ["daily-report", defaultTenantId, date],
    queryFn: () => api.getDailyReport(defaultTenantId, date),
  });
}

export function useMonthlyReport(month: string) {
  return useQuery({
    queryKey: ["monthly-report", defaultTenantId, month],
    queryFn: () => api.getMonthlyReport(defaultTenantId, month),
  });
}

export function useAttempts(from: string, to: string, userId?: string) {
  return useQuery({
    queryKey: ["attempts", defaultTenantId, from, to, userId],
    queryFn: () => api.getAttempts(defaultTenantId, from, to, userId),
  });
}

export function useNotificationDeliveries(filters: NotificationDeliveryFilters) {
  return useQuery({
    queryKey: ["notification-deliveries", defaultTenantId, filters],
    queryFn: () => api.getNotificationDeliveries(defaultTenantId, filters),
  });
}

export function useSettings() {
  return useQuery({
    queryKey: ["settings", defaultTenantId],
    queryFn: () => api.getSettings(defaultTenantId),
  });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Partial<AttendanceSettings>) => api.updateSettings(defaultTenantId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["settings", defaultTenantId] }),
  });
}

export function useShifts() {
  return useQuery({
    queryKey: ["shifts", defaultTenantId],
    queryFn: () => api.getShifts(defaultTenantId),
  });
}

export function useCreateShift() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateShiftTemplateRequest) => api.createShift(defaultTenantId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["shifts", defaultTenantId] }),
  });
}

export function useUpdateShift() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ shiftId, payload }: { shiftId: string; payload: UpdateShiftTemplateRequest }) =>
      api.updateShift(defaultTenantId, shiftId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["shifts", defaultTenantId] }),
  });
}

export function useMemberShiftAssignments(userId: string, enabled = true) {
  return useQuery({
    queryKey: ["member-shift-assignments", defaultTenantId, userId],
    queryFn: () => api.getMemberShiftAssignments(defaultTenantId, userId),
    enabled: Boolean(userId) && enabled,
  });
}

export function useCreateMemberShiftAssignment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, payload }: { userId: string; payload: CreateMemberShiftAssignmentRequest }) =>
      api.createMemberShiftAssignment(defaultTenantId, userId, payload),
    onSuccess: (_assignment, variables) => {
      queryClient.invalidateQueries({ queryKey: ["member-shift-assignments", defaultTenantId, variables.userId] });
    },
  });
}

export function useUpdateMemberShiftAssignment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      userId,
      assignmentId,
      payload,
    }: {
      userId: string;
      assignmentId: string;
      payload: UpdateMemberShiftAssignmentRequest;
    }) => api.updateMemberShiftAssignment(defaultTenantId, userId, assignmentId, payload),
    onSuccess: (_assignment, variables) => {
      queryClient.invalidateQueries({ queryKey: ["member-shift-assignments", defaultTenantId, variables.userId] });
    },
  });
}

export function useLocations() {
  return useQuery({
    queryKey: ["locations", defaultTenantId],
    queryFn: () => api.getLocations(defaultTenantId),
  });
}

export function useCreateLocation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Pick<Location, "name" | "latitude" | "longitude" | "radiusMeters">) =>
      api.createLocation(defaultTenantId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["locations", defaultTenantId] }),
  });
}

export function useUpdateLocation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ locationId, payload }: { locationId: string; payload: Partial<Location> }) =>
      api.updateLocation(defaultTenantId, locationId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["locations", defaultTenantId] }),
  });
}

export function useSubscription() {
  return useQuery({
    queryKey: ["subscription", defaultTenantId],
    queryFn: () => api.getSubscription(defaultTenantId),
  });
}

export function useSubscriptionPackages() {
  return useQuery({
    queryKey: ["subscription-packages", defaultTenantId],
    queryFn: () => api.getSubscriptionPackages(defaultTenantId),
  });
}

export function useSubscriptionPayments() {
  return useQuery({
    queryKey: ["subscription-payments", defaultTenantId],
    queryFn: () => api.getSubscriptionPayments(defaultTenantId),
  });
}

export function useCreateSubscriptionPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateSubscriptionPaymentRequest) => api.createSubscriptionPayment(defaultTenantId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["subscription-payments", defaultTenantId] });
      queryClient.invalidateQueries({ queryKey: ["subscription", defaultTenantId] });
    },
  });
}

export function useSuperAdminOverview() {
  return useQuery({
    queryKey: ["super-admin", "overview"],
    queryFn: () => api.getSuperAdminOverview(),
  });
}

export function useSuperAdminTenants(filters: SuperAdminTenantFilters) {
  return useQuery({
    queryKey: ["super-admin", "tenants", filters],
    queryFn: () => api.getSuperAdminTenants(filters),
  });
}

export function useSuperAdminTenantDetail(tenantId: string) {
  return useQuery({
    queryKey: ["super-admin", "tenants", tenantId],
    queryFn: () => api.getSuperAdminTenantDetail(tenantId),
    enabled: Boolean(tenantId),
  });
}
