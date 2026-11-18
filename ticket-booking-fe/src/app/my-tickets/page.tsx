"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { QrCode, Ticket } from "lucide-react";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { ApiEnvelope, PageEnvelope, TicketItem } from "@/types";
import { Badge, Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function MyTicketsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["my-tickets"],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<PageEnvelope<TicketItem>>>("/users/me/tickets");
      return res.data.data;
    },
  });

  const tickets = data?.content ?? [];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">My E-Tickets</h1>
      {isLoading ? (
        <div className="h-40 animate-pulse rounded-xl bg-slate-200" />
      ) : tickets.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-slate-500">
            You don&apos;t have any issued tickets yet.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tickets.map((t) => (
            <Card key={t.id}>
              <CardContent className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="inline-flex items-center gap-1.5 font-mono text-sm font-bold text-indigo-600">
                    <Ticket className="h-4 w-4" />
                    {t.ticketCode}
                  </span>
                  <Badge tone={t.status === "VALID" ? "success" : t.status === "CHECKED_IN" ? "info" : "danger"}>
                    {t.status}
                  </Badge>
                </div>
                <div>
                  <p className="font-semibold text-slate-900">{t.eventName || "Event"}</p>
                  <p className="text-sm text-slate-600">
                    {t.sectionName || "Section"} • Seat {t.seatCode || "-"}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">Issued: {formatDateTime(t.issuedAt)}</p>
                </div>
                <Link href={`/my-tickets/${t.id}`} className="block">
                  <Button className="w-full" size="sm">
                    <QrCode className="h-4 w-4" /> Show QR Pass
                  </Button>
                </Link>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
