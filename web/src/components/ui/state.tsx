import { AlertCircle, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function LoadingState({ label = "Loading data" }: { label?: string }) {
  return (
    <div className="flex min-h-40 items-center justify-center gap-2 rounded-lg border bg-card px-4 text-sm font-medium text-muted-foreground">
      <Loader2 className="h-5 w-5 animate-spin" />
      {label}
    </div>
  );
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex min-h-40 flex-col items-center justify-center rounded-lg border bg-card px-4 text-center">
      <p className="text-base font-semibold">{title}</p>
      {description ? <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">{description}</p> : null}
    </div>
  );
}

export function ErrorState({ title = "Unable to load data", message }: { title?: string; message?: string }) {
  return (
    <div className={cn("flex min-h-40 flex-col items-center justify-center rounded-lg border border-red-200 bg-red-50 px-4 text-center text-red-700")}>
      <AlertCircle className="mb-2 h-6 w-6" />
      <p className="text-base font-semibold">{title}</p>
      {message ? <p className="mt-2 max-w-md text-sm leading-6">{message}</p> : null}
    </div>
  );
}
