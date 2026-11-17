"use client";

import { useCallback, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { AlertCircle, Wifi, WifiOff } from "lucide-react";
import { api, extractErrorMessage } from "@/lib/api";
import { formatCurrency } from "@/lib/utils";
import { useStompSubscription } from "@/hooks/use-stomp";
import { ApiEnvelope, EventItem, OrderResponse, ReservationResponse, SeatMapResponse, SeatNode } from "@/types";
import { Badge, Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { CountdownTimer } from "@/components/countdown/countdown-timer";

export default function SeatBookingPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const router = useRouter();

  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [reservation, setReservation] = useState<ReservationResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [busy, setBusy] = useState(false);

  const { data: event } = useQuery({
    queryKey: ["event", eventId],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<EventItem>>(`/events/${eventId}`);
      return res.data.data;
    },
  });

  const {
    data: seatMap,
    isLoading,
    refetch: refetchSeatMap,
  } = useQuery({
    queryKey: ["seat-map", eventId],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<SeatMapResponse>>(`/events/${eventId}/seat-map`);
      return res.data.data;
    },
  });

  const handleWsMessage = useCallback(
    (msg: { type: string; payload: Record<string, unknown> }) => {
      if (["SEAT_HELD", "SEAT_RELEASED", "SEAT_SOLD"].includes(msg.type)) {
        refetchSeatMap();
        const affected = (msg.payload?.seatIds as string[]) ?? [];
        if (msg.type !== "SEAT_RELEASED" && affected.length) {
          setSelectedIds((prev) => prev.filter((id) => !affected.includes(id)));
        }
      }
    },
    [refetchSeatMap]
  );

  const { connected } = useStompSubscription(
    `/topic/events/${eventId}/seats`,
    handleWsMessage,
    () => {
      // Section 12.3 / 15.6: Always refetch seat map on WebSocket reconnect
      refetchSeatMap();
    }
  );

  const seatLookup = useMemo(() => {
    const map = new Map<string, SeatNode>();
    seatMap?.sections.forEach((sec) => sec.seats.forEach((s) => map.set(s.id, s)));
    return map;
  }, [seatMap]);

  const estimatedTotal = selectedIds.reduce((acc, id) => acc + (seatLookup.get(id)?.price ?? 0), 0);

  const toggleSeat = (seat: SeatNode) => {
    if (seat.status !== "AVAILABLE" || reservation) return;
    setErrorMsg("");
    setSelectedIds((prev) => {
      if (prev.includes(seat.id)) return prev.filter((id) => id !== seat.id);
      const maxSeats = event?.maxSeatsPerReservation ?? 6;
      if (prev.length >= maxSeats) {
        setErrorMsg(`You can select at most ${maxSeats} seats per reservation.`);
        return prev;
      }
      return [...prev, seat.id];
    });
  };

  const handleReserve = async () => {
    setBusy(true);
    setErrorMsg("");
    try {
      const admissionToken = sessionStorage.getItem(`admission_token_${eventId}`) || undefined;
      const res = await api.post<ApiEnvelope<ReservationResponse>>(
        `/events/${eventId}/reservations`,
        { seatIds: selectedIds, admissionToken },
        admissionToken ? { headers: { "X-Admission-Token": admissionToken } } : undefined
      );
      setReservation(res.data.data);
      refetchSeatMap();
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Failed to reserve seats. Some seats may have just been taken."));
      refetchSeatMap();
    } finally {
      setBusy(false);
    }
  };

  const handleProceedOrder = async () => {
    if (!reservation) return;
    setBusy(true);
    setErrorMsg("");
    try {
      const res = await api.post<ApiEnvelope<OrderResponse>>("/orders", {
        reservationId: reservation.reservationId,
      });
      router.push(`/checkout/${res.data.data.id}`);
    } catch (err) {
      setErrorMsg(extractErrorMessage(err, "Could not create order"));
    } finally {
      setBusy(false);
    }
  };

  const handleCancelReservation = async () => {
    if (!reservation) return;
    setBusy(true);
    try {
      await api.delete(`/reservations/${reservation.reservationId}`);
      setReservation(null);
      setSelectedIds([]);
      refetchSeatMap();
    } catch (err) {
      setErrorMsg(extractErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="grid gap-8 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold">{event?.name || "Select Your Seats"}</h1>
            <p className="text-sm text-slate-500">Click available seats below to hold them for 10 minutes.</p>
          </div>
          <Badge tone={connected ? "success" : "warning"} className="gap-1.5">
            {connected ? <Wifi className="h-3.5 w-3.5" /> : <WifiOff className="h-3.5 w-3.5" />}
            {connected ? "Live Updates On" : "Reconnecting..."}
          </Badge>
        </div>

        <div className="flex flex-wrap gap-4 rounded-xl border border-slate-200 bg-white p-3 text-xs font-medium">
          <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded bg-emerald-100 border border-emerald-400" /> Available</span>
          <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded bg-indigo-600" /> Selected</span>
          <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded bg-amber-200 border border-amber-400" /> Held</span>
          <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded bg-slate-300" /> Sold / Blocked</span>
        </div>

        {errorMsg && (
          <div className="flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
            <AlertCircle className="h-5 w-5 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        {isLoading ? (
          <div className="h-80 animate-pulse rounded-xl bg-slate-200" />
        ) : (
          <div className="space-y-6">
            <div className="rounded-xl bg-slate-800 py-2 text-center text-xs font-semibold tracking-widest text-slate-200 uppercase">
              STAGE / MAIN SCREEN
            </div>
            {seatMap?.sections.map((sec) => (
              <Card key={sec.sectionId}>
                <CardHeader className="flex items-center justify-between py-3">
                  <h2 className="font-semibold">{sec.sectionName}</h2>
                  <span className="text-sm font-semibold text-indigo-600">{formatCurrency(sec.price)}</span>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-5 gap-2 sm:grid-cols-8 md:grid-cols-10">
                    {sec.seats.map((seat) => {
                      const isSelected = selectedIds.includes(seat.id);
                      let style = "bg-emerald-50 border-emerald-300 text-emerald-900 hover:bg-emerald-100 cursor-pointer";
                      if (isSelected) {
                        style = "bg-indigo-600 border-indigo-700 text-white cursor-pointer";
                      } else if (seat.status === "HELD") {
                        style = "bg-amber-100 border-amber-300 text-amber-800 cursor-not-allowed";
                      } else if (seat.status === "SOLD" || seat.status === "BLOCKED") {
                        style = "bg-slate-200 border-slate-300 text-slate-500 cursor-not-allowed";
                      }
                      return (
                        <button
                          key={seat.id}
                          type="button"
                          disabled={seat.status !== "AVAILABLE" || !!reservation}
                          onClick={() => toggleSeat(seat)}
                          title={`${seat.seatCode} (${seat.status})`}
                          className={`rounded-lg border px-2 py-2 text-xs font-semibold transition ${style}`}
                        >
                          {seat.seatCode}
                        </button>
                      );
                    })}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>

      <div>
        <Card className="sticky top-24">
          <CardHeader>
            <h3 className="font-semibold">Your Selection</h3>
          </CardHeader>
          <CardContent className="space-y-4">
            {reservation ? (
              <div className="space-y-4">
                <CountdownTimer
                  expiresAt={reservation.expiresAt}
                  label="Hold expires in"
                  onExpire={() => {
                    setReservation(null);
                    setSelectedIds([]);
                    setErrorMsg("Your reservation has expired. Please select seats again.");
                    refetchSeatMap();
                  }}
                />
                <div className="space-y-1 text-sm">
                  {reservation.items.map((item) => (
                    <div key={item.seatId} className="flex justify-between">
                      <span>{item.sectionName} — {item.seatCode}</span>
                      <span className="font-medium">{formatCurrency(item.unitPrice)}</span>
                    </div>
                  ))}
                </div>
                <div className="flex justify-between border-t border-slate-100 pt-3 font-bold">
                  <span>Total (Server Snapshot)</span>
                  <span className="text-indigo-600">{formatCurrency(reservation.totalAmount)}</span>
                </div>
                <Button className="w-full" size="lg" disabled={busy} onClick={handleProceedOrder}>
                  {busy ? "Creating Order..." : "Proceed to Checkout"}
                </Button>
                <Button
                  variant="outline"
                  className="w-full"
                  disabled={busy}
                  onClick={handleCancelReservation}
                >
                  Release Seats
                </Button>
              </div>
            ) : (
              <div className="space-y-4">
                {selectedIds.length === 0 ? (
                  <p className="text-sm text-slate-500">No seats selected yet.</p>
                ) : (
                  <div className="space-y-1 text-sm">
                    {selectedIds.map((id) => {
                      const s = seatLookup.get(id);
                      return (
                        <div key={id} className="flex justify-between">
                          <span>Seat {s?.seatCode}</span>
                          <span>{formatCurrency(s?.price ?? 0)}</span>
                        </div>
                      );
                    })}
                  </div>
                )}
                <div className="flex justify-between border-t border-slate-100 pt-3 font-bold">
                  <span>Estimated Total</span>
                  <span className="text-indigo-600">{formatCurrency(estimatedTotal)}</span>
                </div>
                <Button
                  className="w-full"
                  size="lg"
                  disabled={selectedIds.length === 0 || busy}
                  onClick={handleReserve}
                >
                  {busy ? "Locking Seats..." : `Hold ${selectedIds.length} Seat(s)`}
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
