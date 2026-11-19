"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Users, Loader2, CheckCircle2 } from "lucide-react";
import { api, extractErrorMessage } from "@/lib/api";
import { useStompSubscription } from "@/hooks/use-stomp";
import { useAuthStore } from "@/stores/auth-store";
import { ApiEnvelope, WaitingRoomStatus } from "@/types";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { CountdownTimer } from "@/components/countdown/countdown-timer";

export default function WaitingRoomPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [status, setStatus] = useState<WaitingRoomStatus | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .post<ApiEnvelope<WaitingRoomStatus>>(`/events/${eventId}/waiting-room/join`)
      .then((res) => {
        const data = res.data.data;
        setStatus(data);
        if (data.status === "ADMITTED" && data.admissionToken) {
          sessionStorage.setItem(`admission_token_${eventId}`, data.admissionToken);
        }
      })
      .catch((err) => setError(extractErrorMessage(err, "Failed to join waiting room")));
  }, [eventId]);

  useEffect(() => {
    if (!status || status.status === "ADMITTED") return;
    const poll = setInterval(() => {
      api
        .get<ApiEnvelope<WaitingRoomStatus>>(`/events/${eventId}/waiting-room/status`)
        .then((res) => {
          const data = res.data.data;
          setStatus(data);
          if (data.status === "ADMITTED" && data.admissionToken) {
            sessionStorage.setItem(`admission_token_${eventId}`, data.admissionToken);
          }
        })
        .catch(() => {});
    }, 3000);
    return () => clearInterval(poll);
  }, [eventId, status]);

  useStompSubscription(
    user ? `/topic/users/${user.id}/notifications` : null,
    (msg) => {
      if (msg.type === "USER_ADMITTED" && msg.payload.eventId === eventId) {
        const token = String(msg.payload.admissionToken ?? "");
        const tokenExpiresAt = String(msg.payload.tokenExpiresAt ?? "");
        if (token) {
          sessionStorage.setItem(`admission_token_${eventId}`, token);
        }
        setStatus((prev) =>
          prev
            ? { ...prev, status: "ADMITTED", admissionToken: token, tokenExpiresAt }
            : prev
        );
      }
    }
  );

  const handleLeave = async () => {
    await api.post(`/events/${eventId}/waiting-room/leave`).catch(() => {});
    router.push(`/events/${eventId}`);
  };

  return (
    <div className="mx-auto max-w-xl py-10">
      <Card>
        <CardHeader>
          <h1 className="text-xl font-bold">Virtual Waiting Room</h1>
          <p className="text-sm text-slate-500">
            Traffic is queued fairly in order of arrival to protect booking stability.
          </p>
        </CardHeader>
        <CardContent className="space-y-6 text-center">
          {error ? (
            <p className="text-sm text-rose-600">{error}</p>
          ) : !status ? (
            <div className="flex flex-col items-center gap-3 py-8">
              <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
              <p className="text-sm text-slate-600">Entering queue...</p>
            </div>
          ) : status.status === "ADMITTED" ? (
            <div className="space-y-4 py-4">
              <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-600" />
              <h2 className="text-lg font-bold text-emerald-700">It&apos;s Your Turn!</h2>
              <p className="text-sm text-slate-600">
                Your admission token is active. Proceed to select your seats now.
              </p>
              {status.tokenExpiresAt && (
                <CountdownTimer expiresAt={status.tokenExpiresAt} label="Admission expires in" />
              )}
              <Button size="lg" className="w-full" onClick={() => router.push(`/booking/${eventId}`)}>
                Enter Seat Selection
              </Button>
            </div>
          ) : (
            <div className="space-y-5 py-4">
              <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-indigo-50 text-2xl font-bold text-indigo-700">
                #{status.queuePosition ?? "-"}
              </div>
              <div className="grid grid-cols-2 gap-4 rounded-xl bg-slate-50 p-4 text-sm">
                <div>
                  <p className="text-slate-500">People Waiting</p>
                  <p className="mt-1 flex items-center justify-center gap-1 font-semibold">
                    <Users className="h-4 w-4 text-indigo-600" /> {status.totalWaiting}
                  </p>
                </div>
                <div>
                  <p className="text-slate-500">Estimated Wait</p>
                  <p className="mt-1 font-semibold">~{status.estimatedWaitSeconds}s</p>
                </div>
              </div>
              <Button variant="outline" onClick={handleLeave}>
                Leave Queue
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
