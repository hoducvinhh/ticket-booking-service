"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Calendar, Clock, MapPin, ShieldCheck, Ticket } from "lucide-react";
import { api, extractErrorMessage } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/utils";
import { useAuthStore } from "@/stores/auth-store";
import {
  ApiEnvelope,
  AvailabilityResponse,
  EventItem,
  OrderResponse,
  ReservationResponse,
  TicketTypeItem,
} from "@/types";
import { Badge, Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [gaQty, setGaQty] = useState<Record<string, number>>({});
  const [gaError, setGaError] = useState("");
  const [gaBusy, setGaBusy] = useState(false);

  const { data: event, isLoading, isError } = useQuery({
    queryKey: ["event-detail", id],
    queryFn: async () => (await api.get<ApiEnvelope<EventItem>>(`/events/${id}`)).data.data,
  });

  const { data: availability } = useQuery({
    queryKey: ["event-availability", id],
    queryFn: async () => (await api.get<ApiEnvelope<AvailabilityResponse>>(`/events/${id}/availability`)).data.data,
  });

  const { data: ticketTypes } = useQuery({
    queryKey: ["event-ticket-types", id],
    queryFn: async () => (await api.get<ApiEnvelope<TicketTypeItem[]>>(`/events/${id}/ticket-types`)).data.data,
  });

  if (isLoading) {
    return <div className="h-96 animate-pulse rounded-2xl bg-slate-200" />;
  }

  if (isError || !event) {
    return (
      <Card>
        <CardContent className="py-12 text-center text-rose-600">
          Event not found or no longer available.
        </CardContent>
      </Card>
    );
  }

  const handleBookClick = () => {
    if (!user) {
      router.push(`/login?redirect=/events/${event.id}`);
      return;
    }
    if (event.waitingRoomEnabled) {
      router.push(`/waiting-room/${event.id}`);
    } else {
      router.push(`/booking/${event.id}`);
    }
  };

  const handleGaBook = async (ticketTypeId: string) => {
    if (!user) {
      router.push(`/login?redirect=/events/${event.id}`);
      return;
    }
    setGaBusy(true);
    setGaError("");
    try {
      const qty = gaQty[ticketTypeId] || 1;
      const admissionToken = sessionStorage.getItem(`admission_token_${event.id}`) || undefined;
      const res = await api.post<ApiEnvelope<ReservationResponse>>(
        `/events/${event.id}/reservations`,
        { ticketTypeId, quantity: qty, admissionToken },
        admissionToken ? { headers: { "X-Admission-Token": admissionToken } } : undefined
      );
      const orderRes = await api.post<ApiEnvelope<OrderResponse>>("/orders", {
        reservationId: res.data.data.reservationId,
      });
      router.push(`/checkout/${orderRes.data.data.id}`);
    } catch (err) {
      setGaError(extractErrorMessage(err, "Could not reserve GA tickets"));
    } finally {
      setGaBusy(false);
    }
  };

  return (
    <div className="grid gap-8 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        <div className="rounded-2xl bg-gradient-to-br from-slate-900 via-indigo-950 to-indigo-800 p-8 text-white shadow-lg">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={event.status === "SALES_OPEN" ? "success" : "info"}>{event.status}</Badge>
            {event.category && <Badge tone="neutral">{event.category}</Badge>}
            {event.waitingRoomEnabled && <Badge tone="warning">Virtual Waiting Room Active</Badge>}
          </div>
          <h1 className="mt-4 text-3xl font-bold">{event.name}</h1>
          <div className="mt-4 flex flex-wrap gap-6 text-sm text-indigo-100">
            <div className="flex items-center gap-2">
              <Calendar className="h-4 w-4" />
              <span>{formatDateTime(event.startTime)} — {formatDateTime(event.endTime)}</span>
            </div>
            <div className="flex items-center gap-2">
              <MapPin className="h-4 w-4" />
              <span>{event.venueName}{event.venueAddress ? ` (${event.venueAddress})` : ""}</span>
            </div>
          </div>
        </div>

        <Card>
          <CardHeader><h2 className="text-lg font-semibold">About This Event</h2></CardHeader>
          <CardContent>
            <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
              {event.description || "Join us for an unforgettable live experience."}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Reserved Seating Sections</h2>
            {availability && availability.totalSeats > 0 && (
              <span className="text-xs font-medium text-slate-500">
                {availability.availableSeats} / {availability.totalSeats} seats available
              </span>
            )}
          </CardHeader>
          <CardContent>
            {event.sections?.length ? (
              <div className="divide-y divide-slate-100">
                {event.sections.map((sec) => (
                  <div key={sec.id} className="flex items-center justify-between py-3">
                    <div>
                      <p className="font-medium text-slate-900">{sec.name}</p>
                      <p className="text-xs text-slate-500">Capacity: {sec.capacity} • {sec.type}</p>
                    </div>
                    <span className="font-semibold text-indigo-600">{formatCurrency(sec.price)}</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-500">No reserved seating sections configured.</p>
            )}
          </CardContent>
        </Card>

        {ticketTypes && ticketTypes.length > 0 && (
          <Card>
            <CardHeader className="flex items-center gap-2">
              <Ticket className="h-5 w-5 text-indigo-600" />
              <h2 className="text-lg font-semibold">General Admission Tickets</h2>
            </CardHeader>
            <CardContent className="space-y-3">
              {gaError && <p className="text-sm text-rose-600">{gaError}</p>}
              {ticketTypes.map((tt) => (
                <div key={tt.id} className="flex flex-wrap items-center justify-between gap-4 rounded-lg border p-3 text-sm">
                  <div>
                    <p className="font-semibold">{tt.name}</p>
                    <p className="text-xs text-slate-500">
                      {formatCurrency(tt.price)} • {tt.remainingQuantity} remaining
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      min={1}
                      max={Math.min(event.maxSeatsPerReservation, Math.max(1, tt.remainingQuantity))}
                      value={gaQty[tt.id] || 1}
                      onChange={(e) => setGaQty((prev) => ({ ...prev, [tt.id]: Number(e.target.value) }))}
                      className="w-16 rounded-lg border px-2 py-1.5 text-center"
                    />
                    <Button
                      size="sm"
                      disabled={gaBusy || tt.remainingQuantity === 0}
                      onClick={() => handleGaBook(tt.id)}
                    >
                      Buy GA
                    </Button>
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        )}
      </div>

      <div className="space-y-4">
        <Card className="sticky top-24">
          <CardHeader><h3 className="font-semibold">Booking Information</h3></CardHeader>
          <CardContent className="space-y-4 text-sm">
            <div className="flex items-start gap-2 text-slate-600">
              <Clock className="mt-0.5 h-4 w-4 text-indigo-600" />
              <div>
                <p className="font-medium text-slate-900">Sales Window</p>
                <p>{formatDateTime(event.salesStartTime)} → {formatDateTime(event.salesEndTime)}</p>
              </div>
            </div>
            <div className="flex items-start gap-2 text-slate-600">
              <ShieldCheck className="mt-0.5 h-4 w-4 text-emerald-600" />
              <div>
                <p className="font-medium text-slate-900">Seat Hold Protection</p>
                <p>Max {event.maxSeatsPerReservation} seats per reservation, held for 10 minutes.</p>
              </div>
            </div>
            <Button
              size="lg"
              className="w-full"
              disabled={event.status !== "SALES_OPEN" && event.status !== "PUBLISHED"}
              onClick={handleBookClick}
            >
              {event.waitingRoomEnabled ? "Enter Waiting Room" : "Select Seats & Book"}
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
