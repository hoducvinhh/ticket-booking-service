"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, extractErrorMessage } from "@/lib/api";
import { formatCurrency, formatDateTime, formatOrderStatus } from "@/lib/utils";
import { ApiEnvelope, OrderResponse, PageEnvelope } from "@/types";
import { Badge, Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function MyOrdersPage() {
  const [msg, setMsg] = useState("");

  const { data, isLoading, refetch } = useQuery({
    queryKey: ["my-orders"],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<PageEnvelope<OrderResponse>>>("/users/me/orders");
      return res.data.data;
    },
  });

  const handleCancel = async (orderId: string) => {
    setMsg("");
    try {
      await api.post(`/orders/${orderId}/cancel`);
      setMsg("Đã hủy đơn hàng và giải phóng các ghế đang giữ.");
      refetch();
    } catch (err) {
      setMsg(extractErrorMessage(err));
    }
  };

  const orders = data?.content ?? [];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Đơn hàng của tôi</h1>
      {msg && <div className="rounded-lg bg-indigo-50 p-3 text-sm text-indigo-800">{msg}</div>}
      {isLoading ? (
        <div className="h-40 animate-pulse rounded-xl bg-slate-200" />
      ) : orders.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-slate-500">
            Bạn chưa có đơn hàng nào.
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {orders.map((order) => (
            <Card key={order.id}>
              <CardContent className="flex flex-wrap items-center justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-bold">{order.orderCode}</span>
                    <Badge
                      tone={
                        order.status === "PAID"
                          ? "success"
                          : order.status === "PENDING_PAYMENT" || order.status === "REFUND_PENDING"
                          ? "warning"
                          : order.status === "REFUNDED"
                          ? "info"
                          : "danger"
                      }
                    >
                      {formatOrderStatus(order.status)}
                    </Badge>
                  </div>
                  <p className="text-sm text-slate-600">{order.eventName || "Sự kiện"}</p>
                  <p className="text-xs text-slate-500">{formatDateTime(order.createdAt)}</p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <span className="text-lg font-bold text-indigo-600">
                    {formatCurrency(order.totalAmount, order.currency)}
                  </span>
                  {order.status === "PENDING_PAYMENT" && (
                    <Button variant="outline" size="sm" onClick={() => handleCancel(order.id)}>
                      Hủy đơn
                    </Button>
                  )}
                  <Link href={`/checkout/${order.id}`}>
                    <Button variant={order.status === "PENDING_PAYMENT" ? "primary" : "outline"} size="sm">
                      {order.status === "PENDING_PAYMENT" ? "Thanh toán ngay" : "Xem chi tiết"}
                    </Button>
                  </Link>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
