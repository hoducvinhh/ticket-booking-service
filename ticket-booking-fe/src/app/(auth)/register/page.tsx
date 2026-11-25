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
  fullName: z.string().min(2, "Vui lòng nhập họ và tên"),
  email: z.string().email("Địa chỉ email không hợp lệ"),
  phone: z.string().optional(),
  password: z.string().min(8, "Mật khẩu phải có ít nhất 8 ký tự"),
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
      setErrorMsg(extractErrorMessage(err, "Đăng ký tài khoản thất bại"));
    }
  };

  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <h1 className="text-xl font-bold">Tạo tài khoản mới</h1>
          <p className="text-sm text-slate-500">Tham gia FlashTix với vai trò Khách hàng hoặc Ban tổ chức</p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {errorMsg && (
              <div className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{errorMsg}</div>
            )}
            <div>
              <label className="mb-1 block text-sm font-medium">Họ và tên</label>
              <input
                {...register("fullName")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="Nguyễn Văn An"
              />
              {errors.fullName && <p className="mt-1 text-xs text-rose-600">{errors.fullName.message}</p>}
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Email</label>
              <input
                type="email"
                {...register("email")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="ban@example.com"
              />
              {errors.email && <p className="mt-1 text-xs text-rose-600">{errors.email.message}</p>}
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Số điện thoại (không bắt buộc)</label>
              <input
                {...register("phone")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="0901234567"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Mật khẩu</label>
              <input
                type="password"
                {...register("password")}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="Tối thiểu 8 ký tự"
              />
              {errors.password && <p className="mt-1 text-xs text-rose-600">{errors.password.message}</p>}
            </div>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" {...register("organizer")} className="rounded border-slate-300" />
              <span>Đăng ký kèm quyền Ban tổ chức sự kiện</span>
            </label>
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Đang tạo tài khoản..." : "Đăng ký tài khoản"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-600">
            Đã có tài khoản?{" "}
            <Link href="/login" className="font-medium text-indigo-600 hover:underline">
              Đăng nhập
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
