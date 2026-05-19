"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { tokenStorage } from "@/lib/auth/token-storage";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!tokenStorage.getAccessToken()) {
      router.replace("/login");
      return;
    }
    setReady(true);
  }, [router]);

  if (!ready) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Preparing dashboard</div>;
  }

  return <>{children}</>;
}
