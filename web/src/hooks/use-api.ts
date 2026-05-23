"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/services";
import { defaultTenantId } from "@/lib/config/env";
import type { AttendanceSettings, Location, SuperAdminTenantFilters } from "@/types/api";

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
