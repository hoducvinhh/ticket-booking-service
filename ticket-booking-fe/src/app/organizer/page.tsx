"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, Building2, CalendarPlus, CheckCircle, PlusCircle, Ticket, UserPlus } from "lucide-react";
import { api, extractErrorMessage } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/utils";
import { ApiEnvelope, EventItem, PageEnvelope, SeatMapResponse, VenueItem } from "@/types";
import { Badge, Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface EventStats {
  eventId: string;
  totalTickets: number;
  soldTickets: number;
  heldTickets: number;
  availableTickets: number;
  totalOrders: number;
  totalRevenue: number;
  paymentSuccessRate: number;
  checkinCount: number;
}

interface CheckinStats {
  totalIssuedTickets: number;
  checkedInCount: number;
  remainingCount: number;
}

export default function OrganizerDashboardPage() {
  const [feedback, setFeedback] = useState("");
  const [selectedEventId, setSelectedEventId] = useState<string>("");

  const [venueName, setVenueName] = useState("");
  const [venueAddr, setVenueAddr] = useState("");
  const [venueCap, setVenueCap] = useState(500);

  const [eventName, setEventName] = useState("");
  const [eventVenueId, setEventVenueId] = useState("");
  const [eventCategory, setEventCategory] = useState("Music");
  const [waitingRoom, setWaitingRoom] = useState(false);

  const [sectionName, setSectionName] = useState("VIP");
  const [sectionPrice, setSectionPrice] = useState(1500000);
  const [rowLabel, setRowLabel] = useState("A");
  const [seatCount, setSeatCount] = useState(10);

  const [gaName, setGaName] = useState("GA Standing");
  const [gaPrice, setGaPrice] = useState(500000);
  const [gaQuantity, setGaQuantity] = useState(100);
  const [staffUserId, setStaffUserId] = useState("");

  const { data: venuesData, refetch: refetchVenues } = useQuery({
    queryKey: ["venues"],
    queryFn: async () => (await api.get<ApiEnvelope<PageEnvelope<VenueItem>>>("/venues?size=50")).data.data,
  });

  const { data: eventsData, refetch: refetchEvents } = useQuery({
    queryKey: ["organizer-events"],
    queryFn: async () => (await api.get<ApiEnvelope<PageEnvelope<EventItem>>>("/organizer/events?size=50")).data.data,
  });

  const { data: stats } = useQuery({
    queryKey: ["organizer-stats", selectedEventId],
    enabled: !!selectedEventId,
    queryFn: async () =>
      (await api.get<ApiEnvelope<EventStats>>(`/organizer/events/${selectedEventId}/statistics`)).data.data,
  });

  const { data: checkinStats } = useQuery({
    queryKey: ["organizer-checkin-stats", selectedEventId],
    enabled: !!selectedEventId,
    queryFn: async () =>
      (await api.get<ApiEnvelope<CheckinStats>>(`/organizer/events/${selectedEventId}/check-in-statistics`)).data.data,
  });

  const { data: seatMap, refetch: refetchSeatMap } = useQuery({
    queryKey: ["organizer-seat-map", selectedEventId],
    enabled: !!selectedEventId,
    queryFn: async () =>
      (await api.get<ApiEnvelope<SeatMapResponse>>(`/events/${selectedEventId}/seat-map`)).data.data,
  });

  const venues = venuesData?.content ?? [];
  const events = eventsData?.content ?? [];

  const handleCreateVenue = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback("");
    try {
      const res = await api.post<ApiEnvelope<VenueItem>>("/organizer/venues", {
        name: venueName,
        address: venueAddr,
        capacity: venueCap,
      });
      setEventVenueId(res.data.data.id);
      setVenueName("");
      setFeedback(`Venue "${res.data.data.name}" created.`);
      refetchVenues();
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handleCreateEvent = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback("");
    try {
      const now = Date.now();
      const res = await api.post<ApiEnvelope<EventItem>>("/organizer/events", {
        venueId: eventVenueId || venues[0]?.id,
        name: eventName,
        category: eventCategory,
        description: "Live high-concurrency ticketing event",
        startTime: new Date(now + 7 * 86400_000).toISOString(),
        endTime: new Date(now + 8 * 86400_000).toISOString(),
        salesStartTime: new Date(now - 3600_000).toISOString(),
        salesEndTime: new Date(now + 6 * 86400_000).toISOString(),
        waitingRoomEnabled: waitingRoom,
        maxConcurrentUsers: 300,
        maxSeatsPerReservation: 6,
      });
      setSelectedEventId(res.data.data.id);
      setEventName("");
      setFeedback(`Event "${res.data.data.name}" created (DRAFT).`);
      refetchEvents();
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handleCreateSectionAndSeats = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedEventId) {
      setFeedback("Select an event first.");
      return;
    }
    const ev = events.find((x) => x.id === selectedEventId);
    if (!ev) return;
    setFeedback("");
    try {
      const secRes = await api.post<ApiEnvelope<{ id: string }>>(`/organizer/venues/${ev.venueId}/sections`, {
        eventId: ev.id,
        name: sectionName,
        price: sectionPrice,
        capacity: seatCount,
        type: "RESERVED",
      });
      const seats = Array.from({ length: seatCount }, (_, idx) => ({
        seatCode: `${rowLabel}${String(idx + 1).padStart(2, "0")}`,
        rowLabel,
        seatNumber: idx + 1,
      }));
      await api.post(`/organizer/sections/${secRes.data.data.id}/seats`, { seats });
      setFeedback(`Created section ${sectionName} with ${seatCount} seats.`);
      refetchEvents();
      refetchSeatMap();
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handleCreateTicketType = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedEventId) {
      setFeedback("Select an event first.");
      return;
    }
    setFeedback("");
    try {
      await api.post(`/organizer/events/${selectedEventId}/ticket-types`, {
        name: gaName,
        price: gaPrice,
        totalQuantity: gaQuantity,
        saleMode: "GENERAL_ADMISSION",
      });
      setFeedback(`Created General Admission tier "${gaName}".`);
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handleAssignStaff = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedEventId || !staffUserId.trim()) return;
    setFeedback("");
    try {
      await api.post(`/organizer/events/${selectedEventId}/staff`, { staffId: staffUserId.trim() });
      setStaffUserId("");
      setFeedback("Check-in staff assigned to event.");
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handleToggleBlockSeat = async (seatId: string, currentStatus: string) => {
    if (currentStatus === "SOLD" || currentStatus === "HELD") return;
    const next = currentStatus === "BLOCKED" ? "AVAILABLE" : "BLOCKED";
    try {
      await api.patch(`/organizer/seats/${seatId}`, { status: next });
      refetchSeatMap();
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handlePublish = async (id: string) => {
    try {
      await api.post(`/organizer/events/${id}/publish`);
      setFeedback("Event published and open for sales.");
      refetchEvents();
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handleHide = async (id: string) => {
    try {
      await api.post(`/organizer/events/${id}/hide`);
      setFeedback("Event hidden (returned to DRAFT).");
      refetchEvents();
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  const handleCancel = async (id: string) => {
    try {
      await api.post(`/organizer/events/${id}/cancel`);
      setFeedback("Event cancelled and paid orders queued for refund.");
      refetchEvents();
    } catch (err) {
      setFeedback(extractErrorMessage(err));
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold">Organizer Platform</h1>
        <p className="text-sm text-slate-500">Manage venues, events, seating, general admission, staff, and analytics.</p>
      </div>

      {feedback && (
        <div className="flex items-center gap-2 rounded-xl bg-indigo-50 p-4 text-sm text-indigo-800">
          <CheckCircle className="h-4 w-4" />
          <span>{feedback}</span>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card>
          <CardHeader className="flex items-center gap-2">
            <Building2 className="h-5 w-5 text-indigo-600" />
            <h2 className="font-semibold">1. Create Venue</h2>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateVenue} className="space-y-3 text-sm">
              <input required placeholder="Venue Name" value={venueName} onChange={(e) => setVenueName(e.target.value)} className="w-full rounded-lg border px-3 py-2" />
              <input placeholder="Address" value={venueAddr} onChange={(e) => setVenueAddr(e.target.value)} className="w-full rounded-lg border px-3 py-2" />
              <input type="number" min={1} value={venueCap} onChange={(e) => setVenueCap(Number(e.target.value))} className="w-full rounded-lg border px-3 py-2" />
              <Button type="submit" className="w-full">Create Venue</Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex items-center gap-2">
            <CalendarPlus className="h-5 w-5 text-indigo-600" />
            <h2 className="font-semibold">2. Create Event</h2>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateEvent} className="space-y-3 text-sm">
              <select
                value={eventVenueId || venues[0]?.id || ""}
                onChange={(e) => setEventVenueId(e.target.value)}
                className="w-full rounded-lg border px-3 py-2"
              >
                {venues.map((v) => (
                  <option key={v.id} value={v.id}>{v.name}</option>
                ))}
              </select>
              <input required placeholder="Event Name" value={eventName} onChange={(e) => setEventName(e.target.value)} className="w-full rounded-lg border px-3 py-2" />
              <select value={eventCategory} onChange={(e) => setEventCategory(e.target.value)} className="w-full rounded-lg border px-3 py-2">
                <option value="Music">Music</option>
                <option value="Tech">Tech</option>
                <option value="Sports">Sports</option>
              </select>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={waitingRoom} onChange={(e) => setWaitingRoom(e.target.checked)} />
                <span>Enable Virtual Waiting Room</span>
              </label>
              <Button type="submit" className="w-full" disabled={venues.length === 0}>Create Event</Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex items-center gap-2">
            <PlusCircle className="h-5 w-5 text-indigo-600" />
            <h2 className="font-semibold">3. Add Section & Seats</h2>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateSectionAndSeats} className="space-y-3 text-sm">
              <select value={selectedEventId} onChange={(e) => setSelectedEventId(e.target.value)} className="w-full rounded-lg border px-3 py-2">
                <option value="">Select Event...</option>
                {events.map((ev) => (
                  <option key={ev.id} value={ev.id}>{ev.name}</option>
                ))}
              </select>
              <input required placeholder="Section Name (e.g. VIP)" value={sectionName} onChange={(e) => setSectionName(e.target.value)} className="w-full rounded-lg border px-3 py-2" />
              <input type="number" min={0} placeholder="Price (VND)" value={sectionPrice} onChange={(e) => setSectionPrice(Number(e.target.value))} className="w-full rounded-lg border px-3 py-2" />
              <div className="grid grid-cols-2 gap-2">
                <input required placeholder="Row (A)" value={rowLabel} onChange={(e) => setRowLabel(e.target.value)} className="rounded-lg border px-3 py-2" />
                <input type="number" min={1} max={100} value={seatCount} onChange={(e) => setSeatCount(Number(e.target.value))} className="rounded-lg border px-3 py-2" />
              </div>
              <Button type="submit" className="w-full">Generate Seats</Button>
            </form>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex items-center gap-2">
            <Ticket className="h-5 w-5 text-indigo-600" />
            <h2 className="font-semibold">Add General Admission Ticket Tier</h2>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreateTicketType} className="grid gap-3 text-sm sm:grid-cols-3">
              <input required placeholder="Tier Name" value={gaName} onChange={(e) => setGaName(e.target.value)} className="rounded-lg border px-3 py-2" />
              <input type="number" min={0} placeholder="Price" value={gaPrice} onChange={(e) => setGaPrice(Number(e.target.value))} className="rounded-lg border px-3 py-2" />
              <input type="number" min={1} placeholder="Qty" value={gaQuantity} onChange={(e) => setGaQuantity(Number(e.target.value))} className="rounded-lg border px-3 py-2" />
              <Button type="submit" className="sm:col-span-3" disabled={!selectedEventId}>Create GA Ticket Tier</Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex items-center gap-2">
            <UserPlus className="h-5 w-5 text-indigo-600" />
            <h2 className="font-semibold">Assign Check-in Staff</h2>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleAssignStaff} className="flex gap-2 text-sm">
              <input
                required
                placeholder="Staff User UUID"
                value={staffUserId}
                onChange={(e) => setStaffUserId(e.target.value)}
                className="flex-1 rounded-lg border px-3 py-2 font-mono"
              />
              <Button type="submit" disabled={!selectedEventId}>Assign</Button>
            </form>
          </CardContent>
        </Card>
      </div>

      {stats && (
        <Card>
          <CardHeader className="flex items-center gap-2">
            <BarChart3 className="h-5 w-5 text-indigo-600" />
            <h2 className="font-semibold">Event Sales & Check-in Analytics</h2>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Total Seats</p><p className="text-xl font-bold">{stats.totalTickets}</p></div>
            <div className="rounded-lg bg-emerald-50 p-3"><p className="text-xs text-emerald-700">Sold</p><p className="text-xl font-bold text-emerald-700">{stats.soldTickets}</p></div>
            <div className="rounded-lg bg-amber-50 p-3"><p className="text-xs text-amber-700">Held</p><p className="text-xl font-bold text-amber-700">{stats.heldTickets}</p></div>
            <div className="rounded-lg bg-indigo-50 p-3"><p className="text-xs text-indigo-700">Revenue</p><p className="text-xl font-bold text-indigo-700">{formatCurrency(stats.totalRevenue)}</p></div>
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Available</p><p className="text-lg font-bold">{stats.availableTickets}</p></div>
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Orders</p><p className="text-lg font-bold">{stats.totalOrders}</p></div>
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Payment Success</p><p className="text-lg font-bold">{stats.paymentSuccessRate}%</p></div>
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Check-ins</p><p className="text-lg font-bold">{checkinStats ? `${checkinStats.checkedInCount} / ${checkinStats.totalIssuedTickets}` : stats.checkinCount}</p></div>
          </CardContent>
        </Card>
      )}

      {seatMap && seatMap.sections.length > 0 && (
        <Card>
          <CardHeader><h2 className="font-semibold">Seat Management (Click AVAILABLE/BLOCKED seat to toggle)</h2></CardHeader>
          <CardContent className="space-y-3">
            {seatMap.sections.map((sec) => (
              <div key={sec.sectionId} className="space-y-1.5">
                <p className="text-xs font-semibold text-slate-600">{sec.sectionName}</p>
                <div className="flex flex-wrap gap-1.5">
                  {sec.seats.map((s) => (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => handleToggleBlockSeat(s.id, s.status)}
                      className={`rounded border px-2 py-1 text-xs font-mono cursor-pointer ${
                        s.status === "BLOCKED"
                          ? "bg-rose-100 border-rose-300 text-rose-800"
                          : s.status === "AVAILABLE"
                          ? "bg-emerald-50 border-emerald-300 text-emerald-800"
                          : "bg-slate-200 text-slate-500 cursor-not-allowed"
                      }`}
                    >
                      {s.seatCode}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader><h2 className="font-semibold">My Events</h2></CardHeader>
        <CardContent className="divide-y divide-slate-100">
          {events.length === 0 ? (
            <p className="text-sm text-slate-500">No events created yet.</p>
          ) : (
            events.map((ev) => (
              <div key={ev.id} className="flex flex-wrap items-center justify-between gap-4 py-3">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold">{ev.name}</span>
                    <Badge tone={ev.status === "SALES_OPEN" ? "success" : "info"}>{ev.status}</Badge>
                  </div>
                  <p className="text-xs text-slate-500">{formatDateTime(ev.startTime)} • ID: {ev.id}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={() => setSelectedEventId(ev.id)}>Manage / Stats</Button>
                  {ev.status === "DRAFT" && (
                    <Button size="sm" onClick={() => handlePublish(ev.id)}>Publish</Button>
                  )}
                  {(ev.status === "PUBLISHED" || ev.status === "SALES_OPEN") && (
                    <Button size="sm" variant="outline" onClick={() => handleHide(ev.id)}>Hide</Button>
                  )}
                  {ev.status !== "CANCELLED" && (
                    <Button size="sm" variant="danger" onClick={() => handleCancel(ev.id)}>Cancel</Button>
                  )}
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
