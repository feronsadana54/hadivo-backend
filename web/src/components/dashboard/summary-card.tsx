import type { LucideIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function SummaryCard({
  title,
  value,
  icon: Icon,
  note,
}: {
  title: string;
  value: string | number;
  icon: LucideIcon;
  note?: string;
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
        <Icon className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-semibold">{value}</div>
        {note ? <p className="mt-2 text-sm leading-5 text-muted-foreground">{note}</p> : null}
      </CardContent>
    </Card>
  );
}
