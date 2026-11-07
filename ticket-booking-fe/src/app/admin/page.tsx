"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Shield } from "lucide-react";
import { api, extractErrorMessage } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/utils";
import { ApiEnvelope, PageEnvelope, UserProfile } from "@/types";
import { Badge, Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface AdminOverview {
  totalUsers: number;
  totalEvents: number;
  totalOrders: number;
  totalPayments: number;
  totalRevenue: number;
  failedPayments: number;
  failedWebhooks: number;
  waitingRoomQueueBacklog: number;
  reservationExpirationBacklog: number;
}

interface AdminOrderSummary {
  id: string;
  orderCode: string;
  totalAmount: number;
  currency: string;
  status: string;
  createdAt: string;
}

interface AdminEventSummary {
  id: string;
  name: string;
  status: string;
  waitingRoomEnabled: boolean;
  startTime: string;
}

interface AdminWebhookSummary {
  id: string;
  provider: string;
  providerEventId: string;
  status: string;
  errorMessage?: string;
  receivedAt: string;
}

interface AdminAuditLogSummary {
  id: string;
  actorId?: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  createdAt: string;
}

export default function AdminDashboardPage() {
  const [tab, setTab] = useState<"users" | "events" | "orders" | "webhooks" | "audit">("users");
  const [msg, setMsg] = useState("");

  const { data: overview } = useQuery({
    queryKey: ["admin-overview"],
    queryFn: async () => (await api.get<ApiEnvelope<AdminOverview>>("/admin/overview")).data.data,
  });

  const { data: usersPage, refetch: refetchUsers } = useQuery({
    queryKey: ["admin-users"],
    queryFn: async () => (await api.get<ApiEnvelope<PageEnvelope<UserProfile>>>("/admin/users?size=20")).data.data,
  });

  const { data: eventsPage } = useQuery({
    queryKey: ["admin-events"],
    enabled: tab === "events",
    queryFn: async () => (await api.get<ApiEnvelope<PageEnvelope<AdminEventSummary>>>("/admin/events?size=20")).data.data,
  });

  const { data: ordersPage, refetch: refetchOrders } = useQuery({
    queryKey: ["admin-orders"],
    enabled: tab === "orders",
    queryFn: async () => (await api.get<ApiEnvelope<PageEnvelope<AdminOrderSummary>>>("/admin/orders?size=20")).data.data,
  });

  const { data: webhooksPage } = useQuery({
    queryKey: ["admin-webhooks"],
    enabled: tab === "webhooks",
    queryFn: async () => (await api.get<ApiEnvelope<PageEnvelope<AdminWebhookSummary>>>("/admin/webhook-events?size=20")).data.data,
  });

  const { data: auditPage } = useQuery({
    queryKey: ["admin-audit"],
    enabled: tab === "audit",
    queryFn: async () => (await api.get<ApiEnvelope<PageEnvelope<AdminAuditLogSummary>>>("/admin/audit-logs?size=30")).data.data,
  });

  const toggleUserStatus = async (user: UserProfile) => {
    const next = user.status === "ACTIVE" ? "LOCKED" : "ACTIVE";
    await api.patch(`/admin/users/${user.id}/status`, { status: next });
    refetchUsers();
  };

  const assignRole = async (userId: string, roleName: string) => {
    setMsg("");
    try {
      await api.post(`/admin/users/${userId}/roles`, { roleName });
      setMsg(`Assigned role ${roleName}.`);
      refetchUsers();
    } catch (err) {
      setMsg(extractErrorMessage(err));
    }
  };

  const processRefund = async (orderId: string) => {
    setMsg("");
    try {
      await api.post(`/admin/orders/${orderId}/refund`);
      setMsg("Refund completed and user notified.");
      refetchOrders();
    } catch (err) {
      setMsg(extractErrorMessage(err));
    }
  };

  return (
    <div className="space-y-8">
      <div className="flex items-center gap-2">
        <Shield className="h-6 w-6 text-indigo-600" />
        <h1 className="text-2xl font-bold">Admin Control Center</h1>
      </div>

      {msg && <div className="rounded-lg bg-indigo-50 p-3 text-sm text-indigo-800">{msg}</div>}

      {overview && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          <Card><CardContent><p className="text-xs text-slate-500">Users</p><p className="text-2xl font-bold">{overview.totalUsers}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Events</p><p className="text-2xl font-bold">{overview.totalEvents}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Orders</p><p className="text-2xl font-bold">{overview.totalOrders}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Revenue</p><p className="text-2xl font-bold text-indigo-600">{formatCurrency(overview.totalRevenue)}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Failed Webhooks</p><p className="text-2xl font-bold text-rose-600">{overview.failedWebhooks}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Payments</p><p className="text-xl font-bold">{overview.totalPayments}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Failed Payments</p><p className="text-xl font-bold text-rose-600">{overview.failedPayments}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Queue Backlog</p><p className="text-xl font-bold">{overview.waitingRoomQueueBacklog}</p></CardContent></Card>
          <Card><CardContent><p className="text-xs text-slate-500">Expiry Backlog</p><p className="text-xl font-bold">{overview.reservationExpirationBacklog}</p></CardContent></Card>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {(["users", "events", "orders", "webhooks", "audit"] as const).map((t) => (
          <Button key={t} size="sm" variant={tab === t ? "primary" : "outline"} onClick={() => setTab(t)}>
            {t.toUpperCase()}
          </Button>
        ))}
      </div>

      {tab === "users" && (
        <Card>
          <CardHeader><h2 className="font-semibold">User & Role Management</h2></CardHeader>
          <CardContent className="divide-y divide-slate-100 text-sm">
            {(usersPage?.content ?? []).map((u) => (
              <div key={u.id} className="flex flex-wrap items-center justify-between gap-4 py-3">
                <div>
                  <p className="font-semibold">{u.fullName} <span className="text-slate-400">({u.email})</span></p>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {u.roles.map((r) => <Badge key={r} tone="info">{r}</Badge>)}
                    <Badge tone={u.status === "ACTIVE" ? "success" : "danger"}>{u.status}</Badge>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={() => assignRole(u.id, "ORGANIZER")}>+Organizer</Button>
                  <Button size="sm" variant="outline" onClick={() => assignRole(u.id, "CHECKIN_STAFF")}>+Staff</Button>
                  <Button size="sm" variant="outline" onClick={() => toggleUserStatus(u)}>
                    {u.status === "ACTIVE" ? "Lock" : "Unlock"}
                  </Button>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {tab === "events" && (
        <Card>
          <CardHeader><h2 className="font-semibold">All Platform Events</h2></CardHeader>
          <CardContent className="divide-y divide-slate-100 text-sm">
            {(eventsPage?.content ?? []).map((e) => (
              <div key={e.id} className="flex items-center justify-between py-3">
                <div>
                  <p className="font-semibold">{e.name}</p>
                  <p className="text-xs text-slate-500">{formatDateTime(e.startTime)}</p>
                </div>
                <Badge tone="info">{e.status}</Badge>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {tab === "orders" && (
        <Card>
          <CardHeader><h2 className="font-semibold">Orders & Refunds</h2></CardHeader>
          <CardContent className="divide-y divide-slate-100 text-sm">
            {(ordersPage?.content ?? []).map((o) => (
              <div key={o.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div>
                  <p className="font-mono font-semibold">{o.orderCode}</p>
                  <p className="text-xs text-slate-500">{formatDateTime(o.createdAt)}</p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="font-semibold">{formatCurrency(o.totalAmount, o.currency)}</span>
                  <Badge tone={o.status === "PAID" ? "success" : o.status === "REFUND_PENDING" ? "warning" : "neutral"}>
                    {o.status}
                  </Badge>
                  {(o.status === "REFUND_PENDING" || o.status === "PAID") && (
                    <Button size="sm" variant="danger" onClick={() => processRefund(o.id)}>
                      Process Refund
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {tab === "webhooks" && (
        <Card>
          <CardHeader><h2 className="font-semibold">Webhook Events</h2></CardHeader>
          <CardContent className="divide-y divide-slate-100 text-sm">
            {(webhooksPage?.content ?? []).map((w) => (
              <div key={w.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                <div>
                  <p className="font-mono font-semibold">{w.provider} — {w.providerEventId}</p>
                  {w.errorMessage && <p className="text-xs text-rose-600">{w.errorMessage}</p>}
                </div>
                <Badge tone={w.status === "PROCESSED" ? "success" : "danger"}>{w.status}</Badge>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {tab === "audit" && (
        <Card>
          <CardHeader><h2 className="font-semibold">Audit Logs</h2></CardHeader>
          <CardContent className="divide-y divide-slate-100 text-sm">
            {(auditPage?.content ?? []).map((a) => (
              <div key={a.id} className="flex items-center justify-between py-2.5">
                <div>
                  <span className="font-mono font-semibold">{a.action}</span>{" "}
                  <span className="text-slate-500">({a.resourceType})</span>
                </div>
                <span className="text-xs text-slate-400">{formatDateTime(a.createdAt)}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
