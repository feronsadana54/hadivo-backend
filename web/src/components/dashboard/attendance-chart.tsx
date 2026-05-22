"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/state";
import type { MonthlyReport } from "@/types/api";

export function AttendanceChart({ report }: { report?: MonthlyReport }) {
  const data = report?.days.map((day) => ({
    date: day.date.slice(5),
    total: day.total,
    onTime: day.byStatus.ON_TIME ?? 0,
    late: day.byStatus.LATE ?? 0,
  }));
  const hasRecords = data?.some((day) => day.total > 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Absensi bulan ini</CardTitle>
        <CardDescription>Ringkasan jumlah absensi per hari.</CardDescription>
      </CardHeader>
      <CardContent className="h-80">
        {!data?.length || !hasRecords ? (
          <EmptyState title="Belum ada data grafik" description="Grafik akan muncul setelah ada user yang melakukan clock-in." />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="date" tickLine={false} axisLine={false} />
              <YAxis tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="onTime" name="Tepat waktu" stackId="a" fill="#047857" radius={[4, 4, 0, 0]} />
              <Bar dataKey="late" name="Terlambat" stackId="a" fill="#B45309" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}
