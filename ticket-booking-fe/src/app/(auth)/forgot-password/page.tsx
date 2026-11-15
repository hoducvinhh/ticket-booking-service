"use client";

import { useState } from "react";
import Link from "next/link";
import { api, extractErrorMessage } from "@/lib/api";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      await api.post("/auth/forgot-password", { email });
      setSent(true);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <h1 className="text-xl font-bold">Reset Password</h1>
        </CardHeader>
        <CardContent>
          {sent ? (
            <div className="space-y-4 text-sm">
              <p className="text-emerald-700">
                If an account exists for <strong>{email}</strong>, a reset token has been sent.
              </p>
              <Link href="/reset-password">
                <Button className="w-full">Enter Reset Token</Button>
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && <p className="text-sm text-rose-600">{error}</p>}
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Enter your email"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
              <Button type="submit" className="w-full">Send Reset Link</Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
