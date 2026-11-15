"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { api, extractErrorMessage } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { ApiEnvelope, AuthResponse } from "@/types";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

const loginSchema = z.object({
  email: z.string().email("Invalid email address"),
  password: z.string().min(1, "Password is required"),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [errorMsg, setErrorMsg] = useState("");

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (values: LoginForm) => {
    setErrorMsg("");
    try {
      const res = await api.post<ApiEnvelope<AuthResponse>>("/auth/login", values);
      const { user, accessToken } = res.data.data;
      setAuth(user, accessToken);
      router.push("/");
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Login failed"));
    }
  };

  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <h1 className="text-xl font-bold">Sign in to FlashTix</h1>
          <p className="text-sm text-slate-500">Access your tickets, orders, and event bookings</p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {errorMsg && (
              <div className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{errorMsg}</div>
            )}
            <div>
              <label className="mb-1 block text-sm font-medium">Email</label>
              <input
                type="email"
                {...register("email")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
                placeholder="you@example.com"
              />
              {errors.email && <p className="mt-1 text-xs text-rose-600">{errors.email.message}</p>}
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Password</label>
              <input
                type="password"
                {...register("password")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
              />
              {errors.password && <p className="mt-1 text-xs text-rose-600">{errors.password.message}</p>}
            </div>
            <div className="flex justify-end text-xs">
              <Link href="/forgot-password" className="text-indigo-600 hover:underline">
                Forgot password?
              </Link>
            </div>
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Signing in..." : "Sign in"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-600">
            Don&apos;t have an account?{" "}
            <Link href="/register" className="font-medium text-indigo-600 hover:underline">
              Create one
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
