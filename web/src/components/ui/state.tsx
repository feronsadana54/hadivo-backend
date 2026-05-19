import { AlertCircle, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function LoadingState({ label = "Loading data" }: { label?: string }) {
  return (
    <div className="flex min-h-40 items-center justify-center gap-2 rounded-lg border bg-card text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" />
      {label}
    </div>
  );
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex min-h-40 flex-col items-center justify-center rounded-lg border bg-card px-4 text-center">
      <p className="text-sm font-medium">{title}</p>
      {description ? <p className="mt-1 max-w-md text-sm text-muted-foreground">{description}</p> : null}
    </div>
  );
}

export function ErrorState({ title = "Unable to load data", message }: { title?: string; message?: string }) {
  return (
    <div className={cn("flex min-h-40 flex-col items-center justify-center rounded-lg border border-red-200 bg-red-50 px-4 text-center text-red-700")}>
      <AlertCircle className="mb-2 h-5 w-5" />
      <p className="text-sm font-medium">{title}</p>
      {message ? <p className="mt-1 max-w-md text-sm">{message}</p> : null}
    </div>
  );
}
