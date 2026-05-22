import axios from "axios";
import { tokenStorage } from "@/lib/auth/token-storage";
import type { ApiResponse } from "@/types/api";

export const apiClient = axios.create({
  baseURL: "/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
});

apiClient.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      tokenStorage.clear();
      if (typeof window !== "undefined" && window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);

export function unwrap<T>(response: { data: ApiResponse<T> }): T {
  if (response.data.error) {
    throw new Error(response.data.error.message || response.data.error.code);
  }
  return response.data.data as T;
}

export function getErrorMessage(error: unknown) {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    const code = error.response?.data?.error?.code;
    if (code) {
      return errorMessageByCode(code);
    }
    if (!error.response) {
      return "Tidak dapat terhubung ke server. Periksa koneksi atau pastikan backend berjalan.";
    }
    return error.response?.data?.error?.message ?? "Permintaan belum berhasil. Silakan coba lagi.";
  }
  return error instanceof Error ? error.message : "Terjadi kesalahan. Silakan coba lagi.";
}

function errorMessageByCode(code: string) {
  const messages: Record<string, string> = {
    OUT_OF_RADIUS: "User berada di luar area absensi.",
    DUPLICATE_CLOCK_IN: "User sudah melakukan clock-in hari ini.",
    NO_CLOCK_IN: "User belum melakukan clock-in.",
    ALREADY_CLOCKED_OUT: "User sudah melakukan clock-out hari ini.",
    LATE_NOT_ALLOWED: "Clock-in terlambat tidak diizinkan.",
    FACE_MISMATCH: "Verifikasi wajah gagal.",
    UNAUTHORIZED: "Sesi Anda sudah berakhir. Silakan login kembali.",
    FORBIDDEN: "Anda tidak memiliki izin untuk membuka data ini.",
    VALIDATION_FAILED: "Periksa kembali data yang diisi.",
    NETWORK_ERROR: "Tidak dapat terhubung ke server. Periksa koneksi atau pastikan backend berjalan.",
  };

  return messages[code] ?? "Permintaan belum berhasil. Silakan coba lagi.";
}
