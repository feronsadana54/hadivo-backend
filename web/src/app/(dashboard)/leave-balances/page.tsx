"use client";

import { FormEvent, useMemo, useState } from "react";
import { Sparkles, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAdjustLeaveBalance, useLeaveBalances } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { displayName } from "@/lib/utils";
import type { LeaveBalance } from "@/types/api";

const currentYear = new Date().getFullYear();
const yearOptions = [currentYear - 1, currentYear, currentYear + 1];

type AdjustForm = {
  userId: string;
  userLabel: string;
  year: number;
  days: string;
  note: string;
};

const emptyForm: AdjustForm = {
  userId: "",
  userLabel: "",
  year: currentYear,
  days: "",
  note: "",
};

function remainingVariant(remaining: number): "success" | "warning" | "danger" {
  if (remaining <= 0) return "danger";
  if (remaining < 3) return "warning";
  return "success";
}

function formatDays(value: number): string {
  return value.toFixed(2).replace(/\.00$/, "");
}

export default function LeaveBalancesPage() {
  const [year, setYear] = useState(currentYear);
  const [search, setSearch] = useState("");
  const [form, setForm] = useState<AdjustForm>(emptyForm);
  const [showModal, setShowModal] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const balances = useLeaveBalances(year);
  const adjust = useAdjustLeaveBalance();

  const filtered = useMemo(() => {
    const rows = balances.data ?? [];
    if (!search.trim()) return rows;
    const term = search.trim().toLowerCase();
    return rows.filter((row: LeaveBalance) =>
      (row.userFullName ?? "").toLowerCase().includes(term) ||
      (row.userEmail ?? "").toLowerCase().includes(term),
    );
  }, [balances.data, search]);

  function openAdjust(row: LeaveBalance) {
    setForm({
      userId: row.userId,
      userLabel: displayName(row.userFullName) || row.userEmail || row.userId,
      year: row.year,
      days: "",
      note: "",
    });
    setFormError(null);
    setShowModal(true);
  }

  function closeAdjust() {
    setShowModal(false);
    setForm(emptyForm);
    setFormError(null);
  }

  async function submitAdjust(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const daysNumber = Number(form.days);
    if (!form.days.trim() || Number.isNaN(daysNumber) || daysNumber === 0) {
      setFormError("Jumlah hari penyesuaian wajib diisi dan tidak boleh nol.");
      return;
    }
    if (!form.note.trim()) {
      setFormError("Catatan penyesuaian wajib diisi.");
      return;
    }

    try {
      await adjust.mutateAsync({
        userId: form.userId,
        payload: { year: form.year, days: daysNumber, note: form.note.trim() },
      });
      setSuccessMessage(`Saldo cuti ${form.userLabel} berhasil diperbarui.`);
      closeAdjust();
      setTimeout(() => setSuccessMessage(null), 4000);
    } catch (error) {
      setFormError(getErrorMessage(error));
    }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <Sparkles className="h-5 w-5 text-primary" />
          <h1 className="text-xl font-semibold">Saldo Cuti</h1>
        </div>
        <p className="text-sm text-muted-foreground">
          Lihat dan sesuaikan saldo cuti tahunan anggota tenant. Penyesuaian akan tercatat di riwayat audit.
        </p>
      </header>

      {successMessage && (
        <div className="rounded-md border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800">
          {successMessage}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Daftar saldo</CardTitle>
          <CardDescription>Saldo cuti tahunan per anggota untuk tahun yang dipilih.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-[1fr,160px]">
            <div className="space-y-1">
              <Label htmlFor="balance-search">Cari anggota</Label>
              <Input
                id="balance-search"
                placeholder="Cari nama atau email"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="balance-year">Tahun</Label>
              <select
                id="balance-year"
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

          {balances.isLoading && <LoadingState />}
          {balances.error && <ErrorState message={getErrorMessage(balances.error)} />}
          {!balances.isLoading && !balances.error && filtered.length === 0 && (
            <EmptyState
              title="Belum ada data saldo cuti."
              description="Saldo akan dibuat otomatis saat anggota pertama kali mengakses cuti."
            />
          )}

          {!balances.isLoading && !balances.error && filtered.length > 0 && (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Anggota</TableHead>
                    <TableHead>Tahun</TableHead>
                    <TableHead className="text-right">Kuota</TableHead>
                    <TableHead className="text-right">Terpakai</TableHead>
                    <TableHead className="text-right">Penyesuaian</TableHead>
                    <TableHead className="text-right">Sisa</TableHead>
                    <TableHead className="text-right">Aksi</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.map((row: LeaveBalance) => (
                    <TableRow key={row.id}>
                      <TableCell>
                        <div className="font-medium">{displayName(row.userFullName) || "Tanpa nama"}</div>
                        {row.userEmail && (
                          <div className="text-xs text-muted-foreground">{row.userEmail}</div>
                        )}
                      </TableCell>
                      <TableCell>{row.year}</TableCell>
                      <TableCell className="text-right">{formatDays(row.annualQuotaDays)}</TableCell>
                      <TableCell className="text-right">{formatDays(row.usedDays)}</TableCell>
                      <TableCell className="text-right">{formatDays(row.adjustedDays)}</TableCell>
                      <TableCell className="text-right">
                        <Badge variant={remainingVariant(row.remainingDays)}>
                          {formatDays(row.remainingDays)} hari
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button size="sm" variant="outline" onClick={() => openAdjust(row)}>
                          Sesuaikan
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

      {showModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
          role="dialog"
          aria-modal="true"
        >
          <Card className="w-full max-w-md">
            <CardHeader className="flex flex-row items-start justify-between gap-2 space-y-0">
              <div>
                <CardTitle>Penyesuaian saldo</CardTitle>
                <CardDescription>{form.userLabel}</CardDescription>
              </div>
              <Button variant="ghost" size="sm" onClick={closeAdjust} aria-label="Tutup">
                <X className="h-4 w-4" />
              </Button>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={submitAdjust}>
                <div className="space-y-1">
                  <Label htmlFor="adjust-year">Tahun</Label>
                  <select
                    id="adjust-year"
                    value={form.year}
                    onChange={(e) => setForm((f) => ({ ...f, year: Number(e.target.value) }))}
                    className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    {yearOptions.map((y) => (
                      <option key={y} value={y}>
                        {y}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="adjust-days">Hari (positif untuk menambah, negatif untuk mengurangi)</Label>
                  <Input
                    id="adjust-days"
                    type="number"
                    step="0.5"
                    value={form.days}
                    onChange={(e) => setForm((f) => ({ ...f, days: e.target.value }))}
                    placeholder="contoh: 2 atau -1"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="adjust-note">Catatan</Label>
                  <Input
                    id="adjust-note"
                    value={form.note}
                    onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
                    placeholder="Alasan penyesuaian"
                  />
                </div>
                {formError && (
                  <p className="text-sm text-destructive">{formError}</p>
                )}
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="outline" onClick={closeAdjust}>
                    Batal
                  </Button>
                  <Button type="submit" disabled={adjust.isPending}>
                    {adjust.isPending ? "Menyimpan..." : "Simpan"}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
