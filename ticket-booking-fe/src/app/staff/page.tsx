"use client";

import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Camera, CheckCircle2, QrCode, XCircle } from "lucide-react";
import { Html5Qrcode } from "html5-qrcode";
import { api, extractErrorMessage } from "@/lib/api";
import { formatDateTime, formatTicketStatus } from "@/lib/utils";
import { ApiEnvelope } from "@/types";
import { Badge, Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface ScanResult {
  ticketId: string;
  ticketCode: string;
  eventId: string;
  seatId?: string;
  status: string;
  checkedInAt: string;
  message: string;
}

export default function StaffCheckinPage() {
  const [eventId, setEventId] = useState("");
  const [qrToken, setQrToken] = useState("");
  const [result, setResult] = useState<ScanResult | null>(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [history, setHistory] = useState<ScanResult[]>([]);
  const [cameraActive, setCameraActive] = useState(false);
  const scannerRef = useRef<Html5Qrcode | null>(null);

  const { data: assignedEvents } = useQuery({
    queryKey: ["staff-assigned-events"],
    queryFn: async () => (await api.get<ApiEnvelope<string[]>>("/check-in/my-events")).data.data,
  });

  useEffect(() => {
    return () => {
      if (scannerRef.current?.isScanning) {
        scannerRef.current.stop().catch(() => {});
      }
    };
  }, []);

  const submitToken = async (targetEventId: string, token: string) => {
    setResult(null);
    setErrorMsg("");
    try {
      const res = await api.post<ApiEnvelope<ScanResult>>("/check-in/scan", {
        eventId: targetEventId.trim(),
        qrToken: token.trim(),
        deviceInfo: typeof navigator !== "undefined" ? navigator.userAgent : "Máy quét Web",
      });
      setResult(res.data.data);
      setHistory((prev) => [res.data.data, ...prev]);
      setQrToken("");
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Vé không hợp lệ hoặc đã check-in trước đó"));
    }
  };

  const handleScan = async (e: React.FormEvent) => {
    e.preventDefault();
    await submitToken(eventId, qrToken);
  };

  const toggleCamera = async () => {
    if (cameraActive) {
      if (scannerRef.current?.isScanning) {
        await scannerRef.current.stop().catch(() => {});
      }
      setCameraActive(false);
      return;
    }
    try {
      const qr = new Html5Qrcode("qr-reader");
      scannerRef.current = qr;
      setCameraActive(true);
      await qr.start(
        { facingMode: "environment" },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decodedText) => {
          setQrToken(decodedText);
          if (eventId.trim()) {
            submitToken(eventId, decodedText);
          }
        },
        () => {}
      );
    } catch {
      setCameraActive(false);
      setErrorMsg("Không thể mở camera. Vui lòng dán mã QR thủ công bên dưới.");
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <QrCode className="h-6 w-6 text-indigo-600" />
          <h1 className="text-2xl font-bold">Soát vé Check-in</h1>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={toggleCamera}>
          <Camera className="h-4 w-4" />
          {cameraActive ? "Tắt camera" : "Mở camera quét mã"}
        </Button>
      </div>

      {cameraActive && (
        <Card>
          <CardContent>
            <div id="qr-reader" className="mx-auto max-w-sm overflow-hidden rounded-lg" />
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader><h2 className="font-semibold">Xác thực mã QR vé</h2></CardHeader>
        <CardContent>
          <form onSubmit={handleScan} className="space-y-4">
            {assignedEvents && assignedEvents.length > 0 && (
              <div>
                <label className="mb-1 block text-sm font-medium">Sự kiện được phân công</label>
                <select
                  value={eventId}
                  onChange={(e) => setEventId(e.target.value)}
                  className="w-full rounded-lg border px-3 py-2 text-sm font-mono"
                >
                  <option value="">Chọn sự kiện đang phụ trách...</option>
                  {assignedEvents.map((evId) => (
                    <option key={evId} value={evId}>{evId}</option>
                  ))}
                </select>
              </div>
            )}
            <div>
              <label className="mb-1 block text-sm font-medium">Mã sự kiện (UUID)</label>
              <input
                required
                value={eventId}
                onChange={(e) => setEventId(e.target.value)}
                placeholder="Chọn ở trên hoặc dán UUID sự kiện"
                className="w-full rounded-lg border px-3 py-2 text-sm font-mono"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium">Chuỗi QR Token đã quét</label>
              <textarea
                required
                rows={3}
                value={qrToken}
                onChange={(e) => setQrToken(e.target.value)}
                placeholder="Dán mã QR token từ vé điện tử của khách..."
                className="w-full rounded-lg border px-3 py-2 text-sm font-mono"
              />
            </div>
            <Button type="submit" className="w-full">Xác thực &amp; Check-in</Button>
          </form>

          {result && (
            <div className="mt-4 flex items-start gap-3 rounded-xl bg-emerald-50 p-4 text-emerald-800">
              <CheckCircle2 className="h-5 w-5 shrink-0" />
              <div className="text-sm">
                <p className="font-bold">Check-in hợp lệ — {result.ticketCode}</p>
                <p>Đã ghi nhận lúc {formatDateTime(result.checkedInAt)}</p>
              </div>
            </div>
          )}

          {errorMsg && (
            <div className="mt-4 flex items-start gap-3 rounded-xl bg-rose-50 p-4 text-rose-800">
              <XCircle className="h-5 w-5 shrink-0" />
              <div className="text-sm font-medium">{errorMsg}</div>
            </div>
          )}
        </CardContent>
      </Card>

      {history.length > 0 && (
        <Card>
          <CardHeader><h2 className="font-semibold">Lịch sử quét vé trong phiên</h2></CardHeader>
          <CardContent className="divide-y divide-slate-100 text-sm">
            {history.map((h, i) => (
              <div key={i} className="flex items-center justify-between py-2">
                <span className="font-mono font-semibold">{h.ticketCode}</span>
                <Badge tone="success">{formatTicketStatus(h.status)}</Badge>
                <span className="text-xs text-slate-500">{formatDateTime(h.checkedInAt)}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
