"use client";

import { useQuery } from "@tanstack/react-query";
import { Bell, Check } from "lucide-react";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { ApiEnvelope, NotificationItem, PageEnvelope } from "@/types";
import { Badge, Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function NotificationsPage() {
  const { data, isLoading, refetch } = useQuery({
    queryKey: ["my-notifications"],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<PageEnvelope<NotificationItem>>>("/users/me/notifications");
      return res.data.data;
    },
  });

  const markRead = async (id: string) => {
    await api.patch(`/notifications/${id}/read`);
    refetch();
  };

  const items = data?.content ?? [];

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center gap-2">
        <Bell className="h-6 w-6 text-indigo-600" />
        <h1 className="text-2xl font-bold">Trung tâm thông báo</h1>
      </div>

      {isLoading ? (
        <div className="h-40 animate-pulse rounded-xl bg-slate-200" />
      ) : items.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-slate-500">Hiện chưa có thông báo nào.</CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {items.map((n) => (
            <Card key={n.id} className={n.read ? "opacity-75" : "border-indigo-200"}>
              <CardContent className="flex items-start justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold">{n.title}</span>
                    {!n.read && <Badge tone="info">Mới</Badge>}
                  </div>
                  <p className="text-sm text-slate-600">{n.content}</p>
                  <p className="text-xs text-slate-400">{formatDateTime(n.createdAt)}</p>
                </div>
                {!n.read && (
                  <Button size="sm" variant="outline" onClick={() => markRead(n.id)}>
                    <Check className="h-4 w-4" /> Đánh dấu đã đọc
                  </Button>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
