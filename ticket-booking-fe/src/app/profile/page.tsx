"use client";

import { useState } from "react";
import { api, extractErrorMessage } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { ApiEnvelope, UserProfile } from "@/types";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function ProfilePage() {
  const { user, setAuth } = useAuthStore();
  const [fullName, setFullName] = useState(user?.fullName || "");
  const [phone, setPhone] = useState(user?.phone || "");
  const [msg, setMsg] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setMsg("");
    try {
      const res = await api.patch<ApiEnvelope<UserProfile>>("/users/me", { fullName, phone });
      setAuth(res.data.data);
      setMsg("Profile updated.");
    } catch (err) {
      setMsg(extractErrorMessage(err));
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setMsg("");
    try {
      await api.patch("/users/me/password", { currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setMsg("Password changed successfully.");
    } catch (err) {
      setMsg(extractErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <h1 className="text-2xl font-bold">Profile Settings</h1>
      {msg && <div className="rounded-lg bg-indigo-50 p-3 text-sm text-indigo-800">{msg}</div>}
      <Card>
        <CardHeader><h2 className="font-semibold">Personal Details</h2></CardHeader>
        <CardContent>
          <form onSubmit={handleSaveProfile} className="space-y-4">
            <div>
              <label className="mb-1 block text-sm font-medium">Email</label>
              <input disabled value={user?.email || ""} className="w-full rounded-lg border bg-slate-100 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Full Name</label>
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full rounded-lg border px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Phone</label>
              <input value={phone} onChange={(e) => setPhone(e.target.value)} className="w-full rounded-lg border px-3 py-2 text-sm" />
            </div>
            <Button type="submit">Save Changes</Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><h2 className="font-semibold">Change Password</h2></CardHeader>
        <CardContent>
          <form onSubmit={handleChangePassword} className="space-y-4">
            <input
              type="password"
              required
              placeholder="Current password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm"
            />
            <input
              type="password"
              required
              minLength={8}
              placeholder="New password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm"
            />
            <Button type="submit" variant="secondary">Update Password</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
