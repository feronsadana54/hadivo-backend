"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { CalendarClock, Pencil, Plus, Save, UserPlus, X } from "lucide-react";
import { ActiveBadge } from "@/components/attendance/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  useCreateMemberShiftAssignment,
  useCreateShift,
  useMemberShiftAssignments,
  useMemberships,
  useShifts,
  useUpdateMemberShiftAssignment,
  useUpdateShift,
} from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import { displayEmail, displayName, formatDateTime } from "@/lib/utils";
import type { ShiftTemplate } from "@/types/api";

const today = new Date().toISOString().slice(0, 10);

type ShiftFormState = {
  name: string;
  startTime: string;
  endTime: string;
  lateThresholdMinutes: string;
  allowsOvertime: boolean;
  active: boolean;
};

type AssignmentFormState = {
  userId: string;
  shiftTemplateId: string;
  effectiveFrom: string;
  effectiveTo: string;
  active: boolean;
};

const defaultShiftForm: ShiftFormState = {
  name: "",
  startTime: "08:00",
  endTime: "17:00",
  lateThresholdMinutes: "15",
  allowsOvertime: false,
  active: true,
};

const defaultAssignmentForm: AssignmentFormState = {
  userId: "",
  shiftTemplateId: "",
  effectiveFrom: today,
  effectiveTo: "",
  active: true,
};

export default function ShiftsPage() {
  const shifts = useShifts();
  const memberships = useMemberships();
  const createShift = useCreateShift();
  const updateShift = useUpdateShift();
  const createAssignment = useCreateMemberShiftAssignment();
  const updateAssignment = useUpdateMemberShiftAssignment();
  const [editingShiftId, setEditingShiftId] = useState<string | null>(null);
  const [shiftForm, setShiftForm] = useState<ShiftFormState>(defaultShiftForm);
  const [assignmentForm, setAssignmentForm] = useState<AssignmentFormState>(defaultAssignmentForm);
  const [shiftMessage, setShiftMessage] = useState<string | null>(null);
  const [assignmentMessage, setAssignmentMessage] = useState<string | null>(null);
  const assignments = useMemberShiftAssignments(assignmentForm.userId, Boolean(assignmentForm.userId));

  const activeShifts = useMemo(() => shifts.data?.filter((shift) => shift.active) ?? [], [shifts.data]);
  const selectedMember = memberships.data?.find((member) => member.userId === assignmentForm.userId);

  useEffect(() => {
    if (!assignmentForm.userId && memberships.data?.length) {
      setAssignmentForm((current) => ({ ...current, userId: memberships.data[0].userId }));
    }
  }, [assignmentForm.userId, memberships.data]);

  useEffect(() => {
    if (!assignmentForm.shiftTemplateId && activeShifts.length) {
      setAssignmentForm((current) => ({ ...current, shiftTemplateId: activeShifts[0].id }));
    }
  }, [activeShifts, assignmentForm.shiftTemplateId]);

  async function submitShift(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setShiftMessage(null);
    const payload = shiftPayload(shiftForm);

    try {
      if (editingShiftId) {
        await updateShift.mutateAsync({ shiftId: editingShiftId, payload });
        setShiftMessage("Shift berhasil diperbarui.");
      } else {
        await createShift.mutateAsync(payload);
        setShiftMessage("Shift berhasil dibuat.");
      }
      resetShiftForm();
    } catch (error) {
      setShiftMessage(getErrorMessage(error));
    }
  }

  async function submitAssignment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAssignmentMessage(null);
    if (!assignmentForm.userId || !assignmentForm.shiftTemplateId) {
      setAssignmentMessage("Pilih anggota dan shift terlebih dahulu.");
      return;
    }

    try {
      await createAssignment.mutateAsync({
        userId: assignmentForm.userId,
        payload: {
          shiftTemplateId: assignmentForm.shiftTemplateId,
          effectiveFrom: assignmentForm.effectiveFrom,
          effectiveTo: assignmentForm.effectiveTo || null,
          active: assignmentForm.active,
        },
      });
      setAssignmentMessage("Penugasan shift berhasil dibuat.");
      setAssignmentForm((current) => ({ ...current, effectiveFrom: today, effectiveTo: "", active: true }));
    } catch (error) {
      setAssignmentMessage(getErrorMessage(error));
    }
  }

  async function toggleAssignment(assignmentId: string, active: boolean) {
    setAssignmentMessage(null);
    try {
      await updateAssignment.mutateAsync({
        userId: assignmentForm.userId,
        assignmentId,
        payload: { active: !active },
      });
      setAssignmentMessage(active ? "Penugasan dinonaktifkan." : "Penugasan diaktifkan.");
    } catch (error) {
      setAssignmentMessage(getErrorMessage(error));
    }
  }

  function startEdit(shift: ShiftTemplate) {
    setEditingShiftId(shift.id);
    setShiftForm({
      name: shift.name,
      startTime: shift.startTime.slice(0, 5),
      endTime: shift.endTime.slice(0, 5),
      lateThresholdMinutes: String(shift.lateThresholdMinutes),
      allowsOvertime: shift.allowsOvertime,
      active: shift.active,
    });
    setShiftMessage(null);
  }

  function resetShiftForm() {
    setEditingShiftId(null);
    setShiftForm(defaultShiftForm);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Shift</h1>
        <p className="text-sm text-muted-foreground">Kelola template shift dan penugasan anggota.</p>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_420px]">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CalendarClock className="h-5 w-5" aria-hidden="true" />
              Template shift
            </CardTitle>
            <CardDescription>Jam mulai, jam selesai, dan toleransi telat per shift.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {shifts.isLoading ? <LoadingState label="Memuat shift..." /> : null}
            {shifts.isError ? <ErrorState message={getErrorMessage(shifts.error)} /> : null}
            {shifts.isSuccess && !shifts.data.length ? (
              <EmptyState title="Belum ada shift" description="Buat shift pertama untuk penugasan anggota." />
            ) : null}
            {shifts.data?.length ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nama</TableHead>
                    <TableHead>Jam</TableHead>
                    <TableHead>Toleransi</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Aksi</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {shifts.data.map((shift) => (
                    <TableRow key={shift.id}>
                      <TableCell>
                        <div className="font-medium">{shift.name}</div>
                        <div className="mt-1 flex flex-wrap gap-2">
                          {shift.overnight ? <Badge variant="info">Shift melewati tengah malam</Badge> : null}
                          {shift.allowsOvertime ? <Badge variant="muted">Lembur</Badge> : null}
                        </div>
                      </TableCell>
                      <TableCell className="whitespace-nowrap">
                        {timeLabel(shift.startTime)} - {timeLabel(shift.endTime)}
                      </TableCell>
                      <TableCell>{shift.lateThresholdMinutes} menit</TableCell>
                      <TableCell>
                        <ActiveBadge active={shift.active} />
                      </TableCell>
                      <TableCell>
                        <Button type="button" variant="outline" size="sm" onClick={() => startEdit(shift)}>
                          <Pencil className="mr-2 h-4 w-4" />
                          Edit
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{editingShiftId ? "Edit shift" : "Buat shift"}</CardTitle>
            <CardDescription>{isOvernight(shiftForm) ? "Shift melewati tengah malam." : "Shift dalam hari yang sama."}</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={submitShift}>
              <div className="space-y-2">
                <Label htmlFor="shift-name">Nama shift</Label>
                <Input
                  id="shift-name"
                  value={shiftForm.name}
                  onChange={(event) => setShiftForm((current) => ({ ...current, name: event.target.value }))}
                  placeholder="Shift Pagi"
                  required
                />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="shift-start">Jam mulai</Label>
                  <Input
                    id="shift-start"
                    type="time"
                    value={shiftForm.startTime}
                    onChange={(event) => setShiftForm((current) => ({ ...current, startTime: event.target.value }))}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="shift-end">Jam selesai</Label>
                  <Input
                    id="shift-end"
                    type="time"
                    value={shiftForm.endTime}
                    onChange={(event) => setShiftForm((current) => ({ ...current, endTime: event.target.value }))}
                    required
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="late-threshold">Toleransi telat (menit)</Label>
                <Input
                  id="late-threshold"
                  type="number"
                  min={0}
                  max={240}
                  value={shiftForm.lateThresholdMinutes}
                  onChange={(event) =>
                    setShiftForm((current) => ({ ...current, lateThresholdMinutes: event.target.value }))
                  }
                  required
                />
              </div>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={shiftForm.allowsOvertime}
                  onChange={(event) =>
                    setShiftForm((current) => ({ ...current, allowsOvertime: event.target.checked }))
                  }
                />
                Izinkan lembur
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={shiftForm.active}
                  onChange={(event) => setShiftForm((current) => ({ ...current, active: event.target.checked }))}
                />
                Shift aktif
              </label>
              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={createShift.isPending || updateShift.isPending}>
                  {editingShiftId ? <Save className="mr-2 h-4 w-4" /> : <Plus className="mr-2 h-4 w-4" />}
                  {editingShiftId ? "Simpan Shift" : "Buat Shift"}
                </Button>
                {editingShiftId ? (
                  <Button type="button" variant="outline" onClick={resetShiftForm}>
                    <X className="mr-2 h-4 w-4" />
                    Batal
                  </Button>
                ) : null}
              </div>
              {shiftMessage ? <p className="text-sm text-muted-foreground">{shiftMessage}</p> : null}
            </form>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <UserPlus className="h-5 w-5" aria-hidden="true" />
            Penugasan anggota
          </CardTitle>
          <CardDescription>Penugasan aktif tidak boleh tumpang tindih untuk anggota yang sama.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <form className="grid gap-4 lg:grid-cols-5 lg:items-end" onSubmit={submitAssignment}>
            <div className="space-y-2 lg:col-span-2">
              <Label htmlFor="assignment-member">Anggota</Label>
              <select
                id="assignment-member"
                className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={assignmentForm.userId}
                onChange={(event) => setAssignmentForm((current) => ({ ...current, userId: event.target.value }))}
              >
                {memberships.data?.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {displayName(member.fullName)} - {displayEmail(member.email, member.userId)}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="assignment-shift">Shift</Label>
              <select
                id="assignment-shift"
                className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={assignmentForm.shiftTemplateId}
                onChange={(event) =>
                  setAssignmentForm((current) => ({ ...current, shiftTemplateId: event.target.value }))
                }
              >
                {activeShifts.map((shift) => (
                  <option key={shift.id} value={shift.id}>
                    {shift.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="assignment-from">Mulai berlaku</Label>
              <Input
                id="assignment-from"
                type="date"
                value={assignmentForm.effectiveFrom}
                onChange={(event) =>
                  setAssignmentForm((current) => ({ ...current, effectiveFrom: event.target.value }))
                }
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="assignment-to">Selesai</Label>
              <Input
                id="assignment-to"
                type="date"
                value={assignmentForm.effectiveTo}
                onChange={(event) => setAssignmentForm((current) => ({ ...current, effectiveTo: event.target.value }))}
              />
            </div>
            <div className="flex flex-wrap items-center gap-3 lg:col-span-5">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={assignmentForm.active}
                  onChange={(event) =>
                    setAssignmentForm((current) => ({ ...current, active: event.target.checked }))
                  }
                />
                Penugasan aktif
              </label>
              <Button
                type="submit"
                className="gap-2"
                disabled={createAssignment.isPending || !assignmentForm.userId || !assignmentForm.shiftTemplateId}
              >
                <UserPlus className="h-4 w-4" aria-hidden="true" />
                Tetapkan shift
              </Button>
            </div>
          </form>

          {assignmentMessage ? <p className="text-sm text-muted-foreground">{assignmentMessage}</p> : null}
          {selectedMember ? (
            <p className="text-sm text-muted-foreground">
              Penugasan untuk {displayName(selectedMember.fullName)}.
            </p>
          ) : null}
          {assignments.isLoading ? <LoadingState label="Memuat penugasan shift..." /> : null}
          {assignments.isError ? <ErrorState message={getErrorMessage(assignments.error)} /> : null}
          {assignments.isSuccess && !assignments.data.length ? (
            <EmptyState title="Belum ada penugasan shift" />
          ) : null}
          {assignments.data?.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Shift</TableHead>
                  <TableHead>Periode</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Dibuat</TableHead>
                  <TableHead>Aksi</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {assignments.data.map((assignment) => (
                  <TableRow key={assignment.id}>
                    <TableCell>
                      <div className="font-medium">{assignment.shiftName ?? "Shift"}</div>
                      <div className="text-xs text-muted-foreground">
                        {timeLabel(assignment.shiftStartTime)} - {timeLabel(assignment.shiftEndTime)}
                      </div>
                    </TableCell>
                    <TableCell>
                      {assignment.effectiveFrom} - {assignment.effectiveTo ?? "seterusnya"}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-2">
                        <ActiveBadge active={assignment.active} />
                        {assignment.current ? <Badge variant="info">Saat ini</Badge> : null}
                      </div>
                    </TableCell>
                    <TableCell>{formatDateTime(assignment.createdAt)}</TableCell>
                    <TableCell>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={updateAssignment.isPending}
                        onClick={() => toggleAssignment(assignment.id, assignment.active)}
                      >
                        {assignment.active ? "Nonaktifkan" : "Aktifkan"}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

function shiftPayload(form: ShiftFormState) {
  return {
    name: form.name.trim(),
    startTime: form.startTime,
    endTime: form.endTime,
    lateThresholdMinutes: clampThreshold(form.lateThresholdMinutes),
    allowsOvertime: form.allowsOvertime,
    active: form.active,
  };
}

function clampThreshold(value: string) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0;
  return Math.min(240, Math.max(0, Math.trunc(parsed)));
}

function isOvernight(form: Pick<ShiftFormState, "startTime" | "endTime">) {
  return Boolean(form.startTime && form.endTime && form.endTime <= form.startTime);
}

function timeLabel(value?: string | null) {
  return value ? value.slice(0, 5) : "-";
}
