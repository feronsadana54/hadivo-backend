"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  BarChart3,
  Building2,
  Clock3,
  LogOut,
  MapPin,
  Settings,
  ShieldAlert,
  Users,
  WalletCards,
} from "lucide-react";
import { AuthGuard } from "@/lib/auth/auth-guard";
import { tokenStorage } from "@/lib/auth/token-storage";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { useTenant } from "@/hooks/use-api";

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: BarChart3 },
  { href: "/attendance", label: "Attendance", icon: Clock3 },
  { href: "/attendance-attempts", label: "Attempts", icon: ShieldAlert },
  { href: "/members", label: "Members", icon: Users },
  { href: "/settings", label: "Settings", icon: Settings },
  { href: "/locations", label: "Locations", icon: MapPin },
  { href: "/subscription", label: "Subscription", icon: WalletCards },
];

export function DashboardShell({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <DashboardFrame>{children}</DashboardFrame>
    </AuthGuard>
  );
}

function DashboardFrame({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const tenant = useTenant();

  function logout() {
    tokenStorage.clear();
    router.replace("/login");
  }

  return (
    <div className="min-h-screen bg-background">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r bg-card lg:block">
        <div className="flex h-16 items-center gap-3 border-b px-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Building2 className="h-5 w-5" />
          </div>
          <div>
            <p className="text-sm font-semibold">Hadivo Admin</p>
            <p className="text-xs text-muted-foreground">Attendance SaaS</p>
          </div>
        </div>
        <nav className="space-y-1 p-3">
          {navItems.map((item) => {
            const active = pathname === item.href;
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
                  active && "bg-primary/10 text-primary",
                )}
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>
      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b bg-background/95 px-4 backdrop-blur lg:px-8">
          <div>
            <p className="text-sm font-medium">{tenant.data?.name ?? "Hadivo Demo School"}</p>
            <p className="text-xs text-muted-foreground">Tenant dashboard</p>
          </div>
          <Button variant="outline" size="sm" onClick={logout}>
            <LogOut className="mr-2 h-4 w-4" />
            Logout
          </Button>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
