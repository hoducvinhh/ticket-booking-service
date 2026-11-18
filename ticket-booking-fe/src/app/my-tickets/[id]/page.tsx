"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { ApiEnvelope, TicketItem, TicketQrResponse } from "@/types";
import { Badge, Card, CardContent, CardHeader } from "@/components/ui/card";

export default function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();

  const { data: ticket } = useQuery({
    queryKey: ["ticket", id],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<TicketItem>>(`/tickets/${id}`);
      return res.data.data;
    },
  });

  const { data: qr } = useQuery({
    queryKey: ["ticket-qr", id],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<TicketQrResponse>>(`/tickets/${id}/qr`);
      return res.data.data;
    },
  });

  if (!ticket || !qr) {
    return <div className="h-80 animate-pulse rounded-xl bg-slate-200" />;
  }

  return (
    <div className="mx-auto max-w-md py-4">
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-bold">{ticket.eventName || "Event Pass"}</h1>
            <p className="font-mono text-xs text-slate-500">{ticket.ticketCode}</p>
          </div>
          <Badge tone={ticket.status === "VALID" ? "success" : "info"}>{ticket.status}</Badge>
        </CardHeader>
        <CardContent className="flex flex-col items-center space-y-5 text-center">
          <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <QRCodeSVG value={qr.qrToken} size={220} />
          </div>
          <div className="w-full space-y-2 rounded-xl bg-slate-50 p-4 text-left text-sm">
            <div className="flex justify-between">
              <span className="text-slate-500">Section</span>
              <span className="font-medium">{ticket.sectionName || "-"}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Seat</span>
              <span className="font-bold text-indigo-600">{ticket.seatCode || "-"}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Issued At</span>
              <span>{formatDateTime(ticket.issuedAt)}</span>
            </div>
            {ticket.checkedInAt && (
              <div className="flex justify-between">
                <span className="text-slate-500">Checked In</span>
                <span>{formatDateTime(ticket.checkedInAt)}</span>
              </div>
            )}
          </div>
          <details className="w-full text-left text-xs text-slate-500">
            <summary className="cursor-pointer font-medium">Raw Signed QR Token (for manual check-in test)</summary>
            <p className="mt-2 break-all rounded bg-slate-100 p-2 font-mono">{qr.qrToken}</p>
          </details>
        </CardContent>
      </Card>
    </div>
  );
}
