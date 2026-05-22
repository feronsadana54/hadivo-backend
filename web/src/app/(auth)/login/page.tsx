"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api } from "@/lib/api/services";
import { getErrorMessage } from "@/lib/api/client";
import { tokenStorage } from "@/lib/auth/token-storage";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z.object({
  email: z.string().email("Masukkan alamat email yang valid."),
  password: z.string().min(1, "Masukkan password."),
});

type LoginForm = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const form = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      email: "superadmin@hadivo.local",
      password: "ChangeMe123!",
    },
  });

  const login = useMutation({
    mutationFn: (values: LoginForm) => api.login(values.email, values.password),
    onSuccess: (tokens) => {
      tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
      router.replace("/dashboard");
    },
  });

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <CardTitle>Masuk ke Hadivo</CardTitle>
          <CardDescription>Gunakan akun admin untuk membuka dashboard absensi.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={form.handleSubmit((values) => login.mutate(values))}>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="contoh: superadmin@hadivo.local"
                {...form.register("email")}
              />
              {form.formState.errors.email ? (
                <p className="text-sm text-red-600">{form.formState.errors.email.message}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                placeholder="Masukkan password"
                {...form.register("password")}
              />
              {form.formState.errors.password ? (
                <p className="text-sm text-red-600">{form.formState.errors.password.message}</p>
              ) : null}
            </div>
            {login.isError ? <p className="rounded-md bg-red-50 p-3 text-sm text-red-700">{getErrorMessage(login.error)}</p> : null}
            <Button className="w-full" type="submit" disabled={login.isPending}>
              {login.isPending ? "Sedang masuk..." : "Masuk"}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              Demo admin: superadmin@hadivo.local / ChangeMe123!
            </p>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
