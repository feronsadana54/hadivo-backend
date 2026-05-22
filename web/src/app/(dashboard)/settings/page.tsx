"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { useSettings, useUpdateSettings } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";

const schema = z.object({
  workStartTime: z.string().min(1),
  workEndTime: z.string().min(1),
  lateThresholdMinutes: z.coerce.number().int().min(0).max(240),
  timezone: z.string().min(1),
  requireFaceClockIn: z.boolean(),
  requireFaceClockOut: z.boolean(),
  allowClockOutOutsideRadius: z.boolean(),
  allowLateClockIn: z.boolean(),
});

type SettingsForm = z.infer<typeof schema>;

export default function SettingsPage() {
  const settings = useSettings();
  const update = useUpdateSettings();
  const form = useForm<SettingsForm>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    if (settings.data) {
      form.reset(settings.data);
    }
  }, [form, settings.data]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Pengaturan Absensi</h1>
        <p className="text-sm text-muted-foreground">Atur jam kerja, toleransi terlambat, dan aturan lokasi.</p>
      </div>
      {settings.isLoading ? <LoadingState label="Memuat pengaturan..." /> : null}
      {settings.isError ? <ErrorState message={getErrorMessage(settings.error)} /> : null}
      {settings.isSuccess && !settings.data ? <EmptyState title="Pengaturan belum tersedia" /> : null}
      {settings.data ? (
        <Card>
          <CardHeader>
            <CardTitle>Aturan absensi</CardTitle>
            <CardDescription>Gunakan label berikut sebagai panduan saat mengubah aturan tenant.</CardDescription>
          </CardHeader>
          <CardContent>
            <form
              className="space-y-5"
              onSubmit={form.handleSubmit((values) => update.mutate(values))}
            >
              <div className="grid gap-4 md:grid-cols-2">
                <Field label="Jam mulai kerja/sekolah" htmlFor="workStartTime">
                  <Input id="workStartTime" type="time" step="1" {...form.register("workStartTime")} />
                </Field>
                <Field label="Jam selesai kerja/sekolah" htmlFor="workEndTime">
                  <Input id="workEndTime" type="time" step="1" {...form.register("workEndTime")} />
                </Field>
                <Field label="Batas toleransi terlambat (menit)" htmlFor="lateThresholdMinutes">
                  <Input id="lateThresholdMinutes" type="number" {...form.register("lateThresholdMinutes")} />
                </Field>
                <Field label="Zona waktu tenant" htmlFor="timezone">
                  <Input id="timezone" {...form.register("timezone")} />
                </Field>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <Checkbox
                  label="Wajib verifikasi wajah saat masuk"
                  description="User harus mengirim data wajah ketika clock-in."
                  {...form.register("requireFaceClockIn")}
                />
                <Checkbox
                  label="Wajib verifikasi wajah saat keluar"
                  description="User harus mengirim data wajah ketika clock-out."
                  {...form.register("requireFaceClockOut")}
                />
                <Checkbox
                  label="Izinkan clock-out di luar area"
                  description="Jika aktif, user tetap bisa clock-out walau berada di luar radius lokasi."
                  {...form.register("allowClockOutOutsideRadius")}
                />
                <Checkbox
                  label="Izinkan clock-in terlambat"
                  description="Jika nonaktif, clock-in setelah batas toleransi akan ditolak."
                  {...form.register("allowLateClockIn")}
                />
              </div>
              {update.isError ? <p className="text-sm text-red-600">{getErrorMessage(update.error)}</p> : null}
              {update.isSuccess ? <p className="text-sm text-emerald-700">Pengaturan berhasil disimpan.</p> : null}
              <Button type="submit" disabled={update.isPending}>
                {update.isPending ? "Menyimpan..." : "Simpan pengaturan"}
              </Button>
            </form>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}

function Checkbox({
  label,
  description,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { label: string; description?: string }) {
  return (
    <label className="flex min-h-16 items-start gap-3 rounded-md border px-3 py-3 text-sm">
      <input type="checkbox" className="mt-1 h-5 w-5 accent-primary" {...props} />
      <span>
        <span className="block font-medium">{label}</span>
        {description ? <span className="mt-1 block text-muted-foreground">{description}</span> : null}
      </span>
    </label>
  );
}
