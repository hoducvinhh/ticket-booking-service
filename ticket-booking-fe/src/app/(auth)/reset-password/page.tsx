"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, extractErrorMessage } from "@/lib/api";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function ResetPasswordPage() {
  const router = useRouter();
  const [token, setToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      await api.post("/auth/reset-password", { token, newPassword });
      router.push("/login");
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <h1 className="text-xl font-bold">Set New Password</h1>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && <p className="text-sm text-rose-600">{error}</p>}
            <input
              required
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Reset token"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
            <input
              type="password"
              required
              minLength={8}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="New password (min 8 chars)"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
            <Button type="submit" className="w-full">Reset Password</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
