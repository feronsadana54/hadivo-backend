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
        <h1 className="text-2xl font-semibold tracking-normal">Tenant settings</h1>
        <p className="text-sm text-muted-foreground">Attendance policy and operating timezone.</p>
      </div>
      {settings.isLoading ? <LoadingState label="Loading settings" /> : null}
      {settings.isError ? <ErrorState message={getErrorMessage(settings.error)} /> : null}
      {settings.isSuccess && !settings.data ? <EmptyState title="Settings are not configured" /> : null}
      {settings.data ? (
        <Card>
          <CardHeader>
            <CardTitle>Attendance settings</CardTitle>
            <CardDescription>Changes are saved to the backend attendance settings endpoint.</CardDescription>
          </CardHeader>
          <CardContent>
            <form
              className="space-y-5"
              onSubmit={form.handleSubmit((values) => update.mutate(values))}
            >
              <div className="grid gap-4 md:grid-cols-2">
                <Field label="Work start time" htmlFor="workStartTime">
                  <Input id="workStartTime" type="time" step="1" {...form.register("workStartTime")} />
                </Field>
                <Field label="Work end time" htmlFor="workEndTime">
                  <Input id="workEndTime" type="time" step="1" {...form.register("workEndTime")} />
                </Field>
                <Field label="Late threshold minutes" htmlFor="lateThresholdMinutes">
                  <Input id="lateThresholdMinutes" type="number" {...form.register("lateThresholdMinutes")} />
                </Field>
                <Field label="Timezone" htmlFor="timezone">
                  <Input id="timezone" {...form.register("timezone")} />
                </Field>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <Checkbox label="Require face clock-in" {...form.register("requireFaceClockIn")} />
                <Checkbox label="Require face clock-out" {...form.register("requireFaceClockOut")} />
                <Checkbox label="Allow clock-out outside radius" {...form.register("allowClockOutOutsideRadius")} />
                <Checkbox label="Allow late clock-in" {...form.register("allowLateClockIn")} />
              </div>
              {update.isError ? <p className="text-sm text-red-600">{getErrorMessage(update.error)}</p> : null}
              {update.isSuccess ? <p className="text-sm text-emerald-700">Settings saved successfully.</p> : null}
              <Button type="submit" disabled={update.isPending}>
                {update.isPending ? "Saving..." : "Save settings"}
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

function Checkbox({ label, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="flex items-center gap-3 rounded-md border px-3 py-3 text-sm">
      <input type="checkbox" className="h-4 w-4 accent-primary" {...props} />
      {label}
    </label>
  );
}
