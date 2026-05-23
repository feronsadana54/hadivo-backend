"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import dynamic from "next/dynamic";
import { useMemo, useState, type InputHTMLAttributes, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ActiveBadge } from "@/components/attendance/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useCreateLocation, useLocations, useUpdateLocation } from "@/hooks/use-api";
import { getErrorMessage } from "@/lib/api/client";
import type { Location } from "@/types/api";

const schema = z.object({
  name: z.string().trim().min(1, "Nama lokasi wajib diisi."),
  latitude: numberInput(
    z.number({
      required_error: "Pilih titik di peta atau isi latitude.",
      invalid_type_error: "Latitude belum valid.",
    }).min(-90, "Latitude belum valid.").max(90, "Latitude belum valid."),
  ),
  longitude: numberInput(
    z.number({
      required_error: "Pilih titik di peta atau isi longitude.",
      invalid_type_error: "Longitude belum valid.",
    }).min(-180, "Longitude belum valid.").max(180, "Longitude belum valid."),
  ),
  radiusMeters: numberInput(
    z.number({
      required_error: "Radius wajib diisi.",
      invalid_type_error: "Radius wajib berupa angka.",
    }).int("Radius harus berupa angka bulat.").min(20, "Radius minimal 20 meter.").max(5000, "Radius maksimal 5000 meter."),
  ),
  active: z.boolean().default(true),
});

type LocationForm = z.infer<typeof schema>;
type Coordinate = {
  latitude: number;
  longitude: number;
};

const fallbackCenter: Coordinate = {
  latitude: -6.2,
  longitude: 106.816666,
};

const LocationMapPicker = dynamic(
  () => import("@/components/locations/location-map-picker").then((mod) => mod.LocationMapPicker),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[320px] items-center justify-center rounded-md border bg-muted text-sm text-muted-foreground sm:h-[360px] lg:h-[400px]">
        Memuat peta...
      </div>
    ),
  },
);

export default function LocationsPage() {
  const locations = useLocations();
  const create = useCreateLocation();
  const update = useUpdateLocation();
  const [editingLocationId, setEditingLocationId] = useState<string | null>(null);
  const form = useForm<LocationForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      radiusMeters: 100,
      active: true,
    },
  });
  const isEditing = Boolean(editingLocationId);
  const selectedCoordinate = toCoordinate(form.watch("latitude"), form.watch("longitude"));
  const radiusMeters = toPositiveNumber(form.watch("radiusMeters")) ?? 100;
  const mapCenter = useMemo(() => getDefaultMapCenter(locations.data), [locations.data]);

  function resetForm() {
    setEditingLocationId(null);
    create.reset();
    update.reset();
    form.reset({
      name: "",
      radiusMeters: 100,
      active: true,
    });
  }

  function editLocation(location: Location) {
    setEditingLocationId(location.id);
    create.reset();
    update.reset();
    form.reset({
      name: location.name,
      latitude: location.latitude,
      longitude: location.longitude,
      radiusMeters: location.radiusMeters,
      active: location.active,
    });
  }

  function submitLocation(values: LocationForm) {
    if (editingLocationId) {
      update.mutate(
        {
          locationId: editingLocationId,
          payload: {
            name: values.name.trim(),
            latitude: values.latitude,
            longitude: values.longitude,
            radiusMeters: values.radiusMeters,
            active: values.active,
          },
        },
        {
          onSuccess: (location) => {
            form.reset({
              name: location.name,
              latitude: location.latitude,
              longitude: location.longitude,
              radiusMeters: location.radiusMeters,
              active: location.active,
            });
          },
        },
      );
      return;
    }

    create.mutate(
      {
        name: values.name.trim(),
        latitude: values.latitude,
        longitude: values.longitude,
        radiusMeters: values.radiusMeters,
      },
      {
        onSuccess: () => resetForm(),
      },
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Lokasi Absensi</h1>
        <p className="text-sm text-muted-foreground">Atur titik dan radius lokasi yang boleh digunakan untuk clock-in.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{isEditing ? "Edit lokasi" : "Tambah lokasi"}</CardTitle>
          <CardDescription>Pilih titik di peta atau isi koordinat secara manual jika diperlukan.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-5"
            onSubmit={form.handleSubmit(submitLocation)}
          >
            <LocationMapPicker
              center={mapCenter}
              value={selectedCoordinate}
              radiusMeters={radiusMeters}
              onChange={(coordinate) => {
                form.setValue("latitude", coordinate.latitude, { shouldDirty: true, shouldValidate: true });
                form.setValue("longitude", coordinate.longitude, { shouldDirty: true, shouldValidate: true });
              }}
            />
            {!selectedCoordinate ? (
              <p className="text-sm text-muted-foreground">Pilih titik di peta atau isi latitude dan longitude secara manual.</p>
            ) : null}
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <Field label="Nama lokasi" htmlFor="name" error={form.formState.errors.name?.message}>
                <Input id="name" placeholder="Contoh: Kampus utama" {...form.register("name")} />
              </Field>
              <Field label="Latitude" htmlFor="latitude" error={form.formState.errors.latitude?.message}>
                <Input id="latitude" type="number" step="0.000001" inputMode="decimal" {...form.register("latitude")} />
              </Field>
              <Field label="Longitude" htmlFor="longitude" error={form.formState.errors.longitude?.message}>
                <Input id="longitude" type="number" step="0.000001" inputMode="decimal" {...form.register("longitude")} />
              </Field>
              <Field
                label="Radius meter"
                htmlFor="radiusMeters"
                error={form.formState.errors.radiusMeters?.message}
                hint="Radius menentukan jarak maksimal user dari titik absensi."
              >
                <Input id="radiusMeters" type="number" min={20} max={5000} {...form.register("radiusMeters")} />
              </Field>
            </div>
            <StatusCheckbox
              disabled={!isEditing}
              description={
                isEditing
                  ? "Nonaktifkan lokasi jika sementara tidak boleh digunakan untuk clock-in."
                  : "Lokasi baru akan aktif setelah disimpan."
              }
              {...form.register("active")}
            />
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button type="submit" disabled={create.isPending || update.isPending}>
                {isEditing
                  ? update.isPending
                    ? "Memperbarui..."
                    : "Perbarui Lokasi"
                  : create.isPending
                    ? "Menyimpan..."
                    : "Simpan Lokasi"}
              </Button>
              {isEditing ? (
                <Button type="button" variant="outline" onClick={resetForm}>
                  Batal edit
                </Button>
              ) : null}
            </div>
          </form>
          {create.isError ? <p className="mt-3 text-sm text-red-600">{getErrorMessage(create.error)}</p> : null}
          {update.isError ? <p className="mt-3 text-sm text-red-600">{getErrorMessage(update.error)}</p> : null}
          {create.isSuccess ? <p className="mt-3 text-sm text-emerald-700">Lokasi berhasil disimpan.</p> : null}
          {update.isSuccess ? <p className="mt-3 text-sm text-emerald-700">Lokasi berhasil diperbarui.</p> : null}
        </CardContent>
      </Card>
      {locations.isLoading ? <LoadingState label="Memuat lokasi..." /> : null}
      {locations.isError ? <ErrorState message={getErrorMessage(locations.error)} /> : null}
      {locations.isSuccess && !locations.data.length ? (
        <EmptyState
          title="Belum ada lokasi absensi"
          description="Tambahkan lokasi pertama agar user bisa melakukan clock-in."
        />
      ) : null}
      {locations.isSuccess && locations.data.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nama</TableHead>
                <TableHead>Radius</TableHead>
                <TableHead>Latitude</TableHead>
                <TableHead>Longitude</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Aksi</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {locations.data.map((location) => (
                <TableRow key={location.id} className={editingLocationId === location.id ? "bg-muted/50" : undefined}>
                  <TableCell className="font-medium">{location.name}</TableCell>
                  <TableCell>{location.radiusMeters} m</TableCell>
                  <TableCell>{location.latitude.toFixed(6)}</TableCell>
                  <TableCell>{location.longitude.toFixed(6)}</TableCell>
                  <TableCell>
                    <ActiveBadge active={location.active} />
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button size="sm" variant="outline" disabled={update.isPending} onClick={() => editLocation(location)}>
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={update.isPending}
                        onClick={() => update.mutate({ locationId: location.id, payload: { active: !location.active } })}
                      >
                        {location.active ? "Nonaktifkan" : "Aktifkan"}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      ) : null}
    </div>
  );
}

function Field({
  label,
  htmlFor,
  children,
  error,
  hint,
}: {
  label: string;
  htmlFor: string;
  children: ReactNode;
  error?: string;
  hint?: string;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hint ? <p className="text-xs leading-5 text-muted-foreground">{hint}</p> : null}
      {error ? <p className="text-xs leading-5 text-red-600">{error}</p> : null}
    </div>
  );
}

function StatusCheckbox({
  description,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { description: string }) {
  return (
    <label className="flex min-h-16 items-start gap-3 rounded-md border px-3 py-3 text-sm">
      <input type="checkbox" className="mt-1 h-5 w-5 accent-primary disabled:opacity-60" {...props} />
      <span>
        <span className="block font-medium">Lokasi aktif</span>
        <span className="mt-1 block text-muted-foreground">{description}</span>
      </span>
    </label>
  );
}

function numberInput(schema: z.ZodNumber) {
  return z.preprocess((value) => {
    if (value == null) return undefined;
    if (typeof value === "string") {
      const trimmed = value.trim();
      if (!trimmed) return undefined;
      return Number(trimmed);
    }
    return value;
  }, schema);
}

function toCoordinate(latitude: unknown, longitude: unknown): Coordinate | null {
  const parsedLatitude = toFiniteNumber(latitude);
  const parsedLongitude = toFiniteNumber(longitude);

  if (parsedLatitude == null || parsedLongitude == null) return null;
  if (parsedLatitude < -90 || parsedLatitude > 90 || parsedLongitude < -180 || parsedLongitude > 180) return null;

  return {
    latitude: parsedLatitude,
    longitude: parsedLongitude,
  };
}

function toPositiveNumber(value: unknown) {
  const parsed = toFiniteNumber(value);
  return parsed != null && parsed > 0 ? parsed : null;
}

function toFiniteNumber(value: unknown) {
  if (value == null || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function getDefaultMapCenter(locations?: Location[]): Coordinate {
  const location = locations?.find((item) => item.active) ?? locations?.[0];
  if (!location) return fallbackCenter;
  return {
    latitude: location.latitude,
    longitude: location.longitude,
  };
}
