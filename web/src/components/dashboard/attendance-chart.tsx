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

  return (
    <Card>
      <CardHeader>
        <CardTitle>Monthly attendance</CardTitle>
        <CardDescription>Daily totals from the backend monthly report.</CardDescription>
      </CardHeader>
      <CardContent className="h-80">
        {!data?.length ? (
          <EmptyState title="No chart data available yet" description="Clock-in data will appear after attendance records exist." />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="date" tickLine={false} axisLine={false} />
              <YAxis tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="onTime" stackId="a" fill="#0f766e" radius={[4, 4, 0, 0]} />
              <Bar dataKey="late" stackId="a" fill="#d97706" radius={[4, 4, 0, 0]} />
              <Bar dataKey="total" fill="#075985" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}
