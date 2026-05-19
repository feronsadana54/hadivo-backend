"use client";

import { zodResolver } from "@hookform/resolvers/zod";
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

const schema = z.object({
  name: z.string().min(2),
  latitude: z.coerce.number().min(-90).max(90),
  longitude: z.coerce.number().min(-180).max(180),
  radiusMeters: z.coerce.number().int().min(10).max(5000),
});

type LocationForm = z.infer<typeof schema>;

export default function LocationsPage() {
  const locations = useLocations();
  const create = useCreateLocation();
  const update = useUpdateLocation();
  const form = useForm<LocationForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      latitude: -6.2,
      longitude: 106.816666,
      radiusMeters: 100,
    },
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-normal">Locations</h1>
        <p className="text-sm text-muted-foreground">Tenant geofence locations and radius settings.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Add location</CardTitle>
          <CardDescription>Create a geofence location for attendance validation.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4 md:grid-cols-5"
            onSubmit={form.handleSubmit((values) =>
              create.mutate(values, {
                onSuccess: () => form.reset({ name: "", latitude: -6.2, longitude: 106.816666, radiusMeters: 100 }),
              }),
            )}
          >
            <Field label="Name" htmlFor="name">
              <Input id="name" {...form.register("name")} />
            </Field>
            <Field label="Latitude" htmlFor="latitude">
              <Input id="latitude" type="number" step="0.000001" {...form.register("latitude")} />
            </Field>
            <Field label="Longitude" htmlFor="longitude">
              <Input id="longitude" type="number" step="0.000001" {...form.register("longitude")} />
            </Field>
            <Field label="Radius meters" htmlFor="radiusMeters">
              <Input id="radiusMeters" type="number" {...form.register("radiusMeters")} />
            </Field>
            <div className="flex items-end">
              <Button className="w-full" type="submit" disabled={create.isPending}>
                {create.isPending ? "Adding..." : "Add"}
              </Button>
            </div>
          </form>
          {create.isError ? <p className="mt-3 text-sm text-red-600">{getErrorMessage(create.error)}</p> : null}
          {create.isSuccess ? <p className="mt-3 text-sm text-emerald-700">Location added successfully.</p> : null}
        </CardContent>
      </Card>
      {locations.isLoading ? <LoadingState label="Loading locations" /> : null}
      {locations.isError ? <ErrorState message={getErrorMessage(locations.error)} /> : null}
      {locations.isSuccess && !locations.data.length ? <EmptyState title="No locations yet" /> : null}
      {locations.isSuccess && locations.data.length ? (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Radius</TableHead>
                <TableHead>Latitude</TableHead>
                <TableHead>Longitude</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {locations.data.map((location) => (
                <TableRow key={location.id}>
                  <TableCell className="font-medium">{location.name}</TableCell>
                  <TableCell>{location.radiusMeters} m</TableCell>
                  <TableCell>{location.latitude.toFixed(6)}</TableCell>
                  <TableCell>{location.longitude.toFixed(6)}</TableCell>
                  <TableCell>
                    <ActiveBadge active={location.active} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={update.isPending}
                      onClick={() => update.mutate({ locationId: location.id, payload: { active: !location.active } })}
                    >
                      {location.active ? "Disable" : "Enable"}
                    </Button>
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

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}
