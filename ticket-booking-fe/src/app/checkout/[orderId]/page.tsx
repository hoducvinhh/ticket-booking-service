"use client";

import { useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, CreditCard, ShieldAlert } from "lucide-react";
import { api, extractErrorMessage } from "@/lib/api";
import { formatCurrency, formatOrderStatus } from "@/lib/utils";
import { ApiEnvelope, CheckoutResponse, OrderResponse } from "@/types";
import { Badge, Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { CountdownTimer } from "@/components/countdown/countdown-timer";

const PROVIDERS: Array<{ id: "PAYOS" | "VNPAY" | "SANDBOX"; label: string }> = [
  { id: "PAYOS", label: "PayOS (VietQR)" },
  { id: "VNPAY", label: "VNPAY" },
  { id: "SANDBOX", label: "Cổng thử nghiệm" },
];

export default function CheckoutPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();

  const [provider, setProvider] = useState<"PAYOS" | "VNPAY" | "SANDBOX">("PAYOS");
  const [checkoutRes, setCheckoutRes] = useState<CheckoutResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [busy, setBusy] = useState(false);

  const [idempotencyKey] = useState<string>(() =>
    typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : `idem-${Date.now()}`
  );

  const { data: order, isLoading, refetch } = useQuery({
    queryKey: ["order", orderId],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<OrderResponse>>(`/orders/${orderId}`);
      return res.data.data;
    },
  });

  const sandboxPaymentId = searchParams.get("sandboxPaymentId") || checkoutRes?.paymentId;

  const handleCheckout = async () => {
    setBusy(true);
    setErrorMsg("");
    try {
      const res = await api.post<ApiEnvelope<CheckoutResponse>>(
        `/orders/${orderId}/checkout`,
        { provider },
        { headers: { "Idempotency-Key": idempotencyKey } }
      );
      const data = res.data.data;
      setCheckoutRes(data);
      if (data.paymentUrl && !data.paymentUrl.includes("sandboxPaymentId=")) {
        window.location.href = data.paymentUrl;
      }
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Khởi tạo thanh toán thất bại"));
    } finally {
      setBusy(false);
    }
  };

  const handleSimulateSandbox = async (success: boolean) => {
    if (!sandboxPaymentId) return;
    setBusy(true);
    setErrorMsg("");
    try {
      await api.post(`/sandbox-payments/${sandboxPaymentId}/complete`, { success });
      await refetch();
      if (success) {
        router.push("/my-tickets");
      }
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Giả lập thanh toán thất bại"));
    } finally {
      setBusy(false);
    }
  };

  if (isLoading || !order) {
    return <div className="h-64 animate-pulse rounded-xl bg-slate-200" />;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Thanh toán — {order.orderCode}</h1>
          <p className="text-sm text-slate-500">{order.eventName}</p>
        </div>
        <Badge tone={order.status === "PAID" ? "success" : order.status === "PENDING_PAYMENT" ? "warning" : "danger"}>
          {formatOrderStatus(order.status)}
        </Badge>
      </div>

      {order.reservationExpiresAt && order.status === "PENDING_PAYMENT" && (
        <CountdownTimer expiresAt={order.reservationExpiresAt} label="Vui lòng thanh toán trong" />
      )}

      {errorMsg && (
        <div className="flex items-center gap-2 rounded-xl bg-rose-50 p-4 text-sm text-rose-700">
          <ShieldAlert className="h-5 w-5" />
          <span>{errorMsg}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <h2 className="font-semibold">Chi tiết đơn hàng</h2>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          {order.items.map((item, idx) => (
            <div key={idx} className="flex justify-between">
              <span>{item.seatCode ? `Ghế ${item.seatCode}` : "Vé tự do"} (x{item.quantity})</span>
              <span className="font-medium">{formatCurrency(item.subtotal, order.currency)}</span>
            </div>
          ))}
          <div className="flex justify-between border-t border-slate-100 pt-3 text-base font-bold">
            <span>Tổng thanh toán</span>
            <span className="text-indigo-600">{formatCurrency(order.totalAmount, order.currency)}</span>
          </div>
        </CardContent>
      </Card>

      {order.status === "PAID" ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-8 text-center">
            <CheckCircle2 className="h-12 w-12 text-emerald-600" />
            <p className="font-semibold text-emerald-700">Thanh toán thành công &amp; Vé điện tử đã được phát hành!</p>
            <Button onClick={() => router.push("/my-tickets")}>Xem vé của tôi</Button>
          </CardContent>
        </Card>
      ) : order.status === "PENDING_PAYMENT" ? (
        <Card>
          <CardHeader>
            <h2 className="font-semibold">Chọn phương thức thanh toán</h2>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-3 gap-3">
              {PROVIDERS.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => setProvider(p.id)}
                  className={`rounded-xl border p-3 text-sm font-semibold transition cursor-pointer ${
                    provider === p.id
                      ? "border-indigo-600 bg-indigo-50 text-indigo-700"
                      : "border-slate-200 hover:bg-slate-50"
                  }`}
                >
                  {p.label}
                </button>
              ))}
            </div>

            {!sandboxPaymentId ? (
              <Button className="w-full" size="lg" disabled={busy} onClick={handleCheckout}>
                <CreditCard className="h-4 w-4" />
                {busy ? "Đang chuyển sang cổng thanh toán..." : `Thanh toán ${formatCurrency(order.totalAmount, order.currency)}`}
              </Button>
            ) : (
              <div className="space-y-3 rounded-xl border border-indigo-200 bg-indigo-50 p-4">
                <p className="text-sm font-semibold text-indigo-900">
                  Trình giả lập thanh toán Sandbox ({checkoutRes?.provider || provider})
                </p>
                <p className="text-xs text-indigo-700">
                  Mô phỏng callback webhook có chữ ký số gửi về hệ thống kèm kiểm tra idempotency.
                </p>
                <div className="flex gap-3">
                  <Button className="flex-1" disabled={busy} onClick={() => handleSimulateSandbox(true)}>
                    Giả lập thanh toán thành công
                  </Button>
                  <Button variant="danger" className="flex-1" disabled={busy} onClick={() => handleSimulateSandbox(false)}>
                    Giả lập thất bại
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
