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

const registerSchema = z.object({
  fullName: z.string().min(2, "Full name is required"),
  email: z.string().email("Invalid email address"),
  phone: z.string().optional(),
  password: z.string().min(8, "Password must be at least 8 characters"),
  organizer: z.boolean().optional(),
});

type RegisterForm = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [errorMsg, setErrorMsg] = useState("");

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (values: RegisterForm) => {
    setErrorMsg("");
    try {
      const res = await api.post<ApiEnvelope<AuthResponse>>("/auth/register", values);
      const { user, accessToken } = res.data.data;
      setAuth(user, accessToken);
      router.push("/");
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Registration failed"));
    }
  };

  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <h1 className="text-xl font-bold">Create Account</h1>
          <p className="text-sm text-slate-500">Join FlashTix as a customer or event organizer</p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {errorMsg && (
              <div className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{errorMsg}</div>
            )}
            <div>
              <label className="mb-1 block text-sm font-medium">Full Name</label>
              <input
                {...register("fullName")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="Nguyen Van A"
              />
              {errors.fullName && <p className="mt-1 text-xs text-rose-600">{errors.fullName.message}</p>}
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Email</label>
              <input
                type="email"
                {...register("email")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="you@example.com"
              />
              {errors.email && <p className="mt-1 text-xs text-rose-600">{errors.email.message}</p>}
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Phone (optional)</label>
              <input
                {...register("phone")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="0901234567"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Password</label>
              <input
                type="password"
                {...register("password")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
              {errors.password && <p className="mt-1 text-xs text-rose-600">{errors.password.message}</p>}
            </div>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" {...register("organizer")} className="rounded border-slate-300" />
              <span>Register also as an Event Organizer</span>
            </label>
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Creating account..." : "Create Account"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-600">
            Already registered?{" "}
            <Link href="/login" className="font-medium text-indigo-600 hover:underline">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
