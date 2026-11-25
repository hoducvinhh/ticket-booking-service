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
  email: z.string().email("Địa chỉ email không hợp lệ"),
  password: z.string().min(1, "Vui lòng nhập mật khẩu"),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [errorMsg, setErrorMsg] = useState("");

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  const fillDemo = (email: string) => {
    setValue("email", email, { shouldValidate: true });
    setValue("password", "FlashTix@2026", { shouldValidate: true });
  };

  const onSubmit = async (values: LoginForm) => {
    setErrorMsg("");
    try {
      const res = await api.post<ApiEnvelope<AuthResponse>>("/auth/login", values);
      const { user, accessToken } = res.data.data;
      setAuth(user, accessToken);
      router.push("/");
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Đăng nhập không thành công"));
    }
  };

  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <h1 className="text-xl font-bold">Đăng nhập FlashTix</h1>
          <p className="text-sm text-slate-500">Quản lý vé điện tử, đơn hàng và đặt chỗ sự kiện</p>
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
                placeholder="ban@example.com"
              />
              {errors.email && <p className="mt-1 text-xs text-rose-600">{errors.email.message}</p>}
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Mật khẩu</label>
              <input
                type="password"
                {...register("password")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
              />
              {errors.password && <p className="mt-1 text-xs text-rose-600">{errors.password.message}</p>}
            </div>
            <div className="flex justify-end text-xs">
              <Link href="/forgot-password" className="text-indigo-600 hover:underline">
                Quên mật khẩu?
              </Link>
            </div>
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Đang đăng nhập..." : "Đăng nhập"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-600">
            Chưa có tài khoản?{" "}
            <Link href="/register" className="font-medium text-indigo-600 hover:underline">
              Đăng ký ngay
            </Link>
          </p>
        </CardContent>
      </Card>

      <Card className="mt-4">
        <CardContent className="space-y-2.5 pt-4 text-xs text-slate-600">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-slate-800">Tài khoản trải nghiệm nhanh</span>
            <span className="font-mono text-[11px] text-slate-500">Mật khẩu: FlashTix@2026</span>
          </div>
          <div className="grid grid-cols-2 gap-2">
            {[
              { label: "Khách hàng (An)", email: "buyer@flashtix.vn" },
              { label: "Ban tổ chức", email: "organizer@flashtix.vn" },
              { label: "Nhân viên soát vé", email: "staff@flashtix.vn" },
              { label: "Quản trị viên", email: "admin@flashtix.vn" },
            ].map((acc) => (
              <button
                key={acc.email}
                type="button"
                onClick={() => fillDemo(acc.email)}
                className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-left transition hover:border-indigo-400 hover:bg-indigo-50"
              >
                <div className="font-medium text-slate-800">{acc.label}</div>
                <div className="truncate text-[11px] text-slate-500">{acc.email}</div>
              </button>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
