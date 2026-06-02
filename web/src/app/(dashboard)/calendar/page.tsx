"use client";

import { FormEvent, useMemo, useState } from "react";
import { CalendarDays } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  useCreateHoliday,
  useHolidays,
  useUpdateHoliday,
  useUpdateWorkdaySettings,
  useWorkdaySettings,
} from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import type { Holiday, HolidayType, UpdateWorkdaySettingsRequest } from "@/types/api";

const HOLIDAY_TYPES: HolidayType[] = ["CUSTOM", "NATIONAL", "COMPANY", "SCHOOL"];
const HOLIDAY_TYPE_LABEL: Record<HolidayType, string> = {
  CUSTOM: "Kustom",
  NATIONAL: "Nasional",
  COMPANY: "Perusahaan",
  SCHOOL: "Sekolah",
};

const DAY_FIELDS: Array<{ key: keyof UpdateWorkdaySettingsRequest; label: string }> = [
  { key: "mondayWorkday", label: "Senin" },
  { key: "tuesdayWorkday", label: "Selasa" },
  { key: "wednesdayWorkday", label: "Rabu" },
  { key: "thursdayWorkday", label: "Kamis" },
  { key: "fridayWorkday", label: "Jumat" },
  { key: "saturdayWorkday", label: "Sabtu" },
  { key: "sundayWorkday", label: "Minggu" },
];

const currentYear = new Date().getFullYear();
const yearOptions = [currentYear - 1, currentYear, currentYear + 1];

type HolidayForm = {
  holidayDate: string;
  name: string;
  type: HolidayType;
};

const emptyHolidayForm: HolidayForm = {
  holidayDate: "",
  name: "",
  type: "CUSTOM",
};

export default function CalendarPage() {
  const workday = useWorkdaySettings();
  const updateWorkday = useUpdateWorkdaySettings();
  const [year, setYear] = useState(currentYear);
  const holidays = useHolidays(`${year}-01-01`, `${year}-12-31`);
  const createHoliday = useCreateHoliday();
  const updateHoliday = useUpdateHoliday();

  const [workdayDirty, setWorkdayDirty] = useState<UpdateWorkdaySettingsRequest>({});
  const [workdayError, setWorkdayError] = useState<string | null>(null);
  const [workdaySuccess, setWorkdaySuccess] = useState<string | null>(null);

  const [form, setForm] = useState<HolidayForm>(emptyHolidayForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [holidaySuccess, setHolidaySuccess] = useState<string | null>(null);

  const dayValues = useMemo(() => {
    const base = workday.data;
    if (!base) return {} as Record<string, boolean>;
    return {
      mondayWorkday: base.mondayWorkday,
      tuesdayWorkday: base.tuesdayWorkday,
      wednesdayWorkday: base.wednesdayWorkday,
      thursdayWorkday: base.thursdayWorkday,
      fridayWorkday: base.fridayWorkday,
      saturdayWorkday: base.saturdayWorkday,
      sundayWorkday: base.sundayWorkday,
      ...(workdayDirty as Record<string, boolean>),
    } as Record<string, boolean>;
  }, [workday.data, workdayDirty]);

  function toggleDay(key: keyof UpdateWorkdaySettingsRequest, value: boolean) {
    setWorkdayDirty((prev) => ({ ...prev, [key]: value }));
    setWorkdayError(null);
  }

  async function submitWorkday() {
    setWorkdayError(null);
    setWorkdaySuccess(null);
    if (Object.keys(workdayDirty).length === 0) {
      setWorkdayError("Tidak ada perubahan untuk disimpan.");
      return;
    }
    try {
      await updateWorkday.mutateAsync(workdayDirty);
      setWorkdayDirty({});
      setWorkdaySuccess("Hari kerja diperbarui.");
      setTimeout(() => setWorkdaySuccess(null), 3000);
    } catch (error) {
      setWorkdayError(getErrorMessage(error));
    }
  }

  async function submitHoliday(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (!form.holidayDate) {
      setFormError("Tanggal hari libur wajib diisi.");
      return;
    }
    if (!form.name.trim()) {
      setFormError("Nama hari libur wajib diisi.");
      return;
    }
    try {
      await createHoliday.mutateAsync({
        holidayDate: form.holidayDate,
        name: form.name.trim(),
        type: form.type,
      });
      setForm(emptyHolidayForm);
      setHolidaySuccess("Hari libur ditambahkan.");
      setTimeout(() => setHolidaySuccess(null), 3000);
    } catch (error) {
      setFormError(getErrorMessage(error));
    }
  }

  async function toggleHolidayActive(row: Holiday) {
    try {
      await updateHoliday.mutateAsync({
        holidayId: row.id,
        payload: { active: !row.active },
      });
    } catch (error) {
      setFormError(getErrorMessage(error));
    }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <CalendarDays className="h-5 w-5 text-primary" />
          <h1 className="text-xl font-semibold">Kalender Kerja</h1>
        </div>
        <p className="text-sm text-muted-foreground">
          Atur hari kerja dan daftar hari libur tenant. Hari kerja dipakai untuk menghitung pemotongan saldo cuti.
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Hari Kerja</CardTitle>
            <CardDescription>
              Centang hari yang dianggap sebagai hari kerja tenant. Minimal satu hari harus aktif.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {workday.isLoading && <LoadingState />}
            {workday.isError && <ErrorState message={getErrorMessage(workday.error)} />}
            {workday.data && (
              <>
                <div className="grid gap-2 sm:grid-cols-2">
                  {DAY_FIELDS.map(({ key, label }) => (
                    <label
                      key={key}
                      className="flex items-center gap-3 rounded-md border px-3 py-2 text-sm"
                    >
                      <input
                        type="checkbox"
                        checked={Boolean(dayValues[key as string])}
                        onChange={(e) => toggleDay(key, e.target.checked)}
                        className="h-4 w-4"
                      />
                      <span className="font-medium">{label}</span>
                    </label>
                  ))}
                </div>
                {workdayError && <p className="text-sm text-destructive">{workdayError}</p>}
                {workdaySuccess && <p className="text-sm text-emerald-700">{workdaySuccess}</p>}
                <div className="flex justify-end">
                  <Button onClick={submitWorkday} disabled={updateWorkday.isPending}>
                    {updateWorkday.isPending ? "Menyimpan..." : "Simpan"}
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Tambah Hari Libur</CardTitle>
            <CardDescription>
              Hari libur aktif akan dikurangkan dari perhitungan hari kerja saat cuti tahunan disetujui.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={submitHoliday}>
              <div className="space-y-1">
                <Label htmlFor="holiday-date">Tanggal</Label>
                <Input
                  id="holiday-date"
                  type="date"
                  value={form.holidayDate}
                  onChange={(e) => setForm((f) => ({ ...f, holidayDate: e.target.value }))}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="holiday-name">Nama</Label>
                <Input
                  id="holiday-name"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder="Mis. Cuti Bersama"
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="holiday-type">Tipe</Label>
                <select
                  id="holiday-type"
                  value={form.type}
                  onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as HolidayType }))}
                  className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                >
                  {HOLIDAY_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {HOLIDAY_TYPE_LABEL[t]}
                    </option>
                  ))}
                </select>
              </div>
              {formError && <p className="text-sm text-destructive">{formError}</p>}
              {holidaySuccess && <p className="text-sm text-emerald-700">{holidaySuccess}</p>}
              <div className="flex justify-end">
                <Button type="submit" disabled={createHoliday.isPending}>
                  {createHoliday.isPending ? "Menyimpan..." : "Tambah"}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Daftar Hari Libur</CardTitle>
          <CardDescription>Pilih tahun untuk melihat hari libur yang sudah terdaftar.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-[200px]">
            <div className="space-y-1">
              <Label htmlFor="year-filter">Tahun</Label>
              <select
                id="year-filter"
                value={year}
                onChange={(e) => setYear(Number(e.target.value))}
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              >
                {yearOptions.map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {holidays.isLoading && <LoadingState />}
          {holidays.isError && <ErrorState message={getErrorMessage(holidays.error)} />}
          {!holidays.isLoading && !holidays.isError && (holidays.data ?? []).length === 0 && (
            <EmptyState
              title="Belum ada hari libur."
              description="Tambahkan hari libur lewat form di atas."
            />
          )}

          {!holidays.isLoading && !holidays.isError && (holidays.data ?? []).length > 0 && (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tanggal</TableHead>
                    <TableHead>Nama</TableHead>
                    <TableHead>Tipe</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Aksi</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(holidays.data ?? []).map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="whitespace-nowrap">{row.holidayDate}</TableCell>
                      <TableCell>{row.name}</TableCell>
                      <TableCell>
                        <Badge variant="info">{HOLIDAY_TYPE_LABEL[row.type]}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={row.active ? "success" : "muted"}>
                          {row.active ? "Aktif" : "Nonaktif"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => toggleHolidayActive(row)}
                          disabled={updateHoliday.isPending}
                        >
                          {row.active ? "Nonaktifkan" : "Aktifkan"}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
