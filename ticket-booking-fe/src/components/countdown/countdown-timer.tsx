"use client";

import { useEffect, useState } from "react";
import { Clock } from "lucide-react";

interface CountdownTimerProps {
  expiresAt: string;
  onExpire?: () => void;
  label?: string;
}

export function CountdownTimer({ expiresAt, onExpire, label = "Thời gian còn lại" }: CountdownTimerProps) {
  const [secondsLeft, setSecondsLeft] = useState<number>(() =>
    Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000))
  );

  useEffect(() => {
    const calc = () => Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));

    const timer = setInterval(() => {
      const next = calc();
      setSecondsLeft(next);
      if (next <= 0) {
        clearInterval(timer);
        onExpire?.();
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [expiresAt, onExpire]);

  const mins = Math.floor(secondsLeft / 60);
  const secs = secondsLeft % 60;
  const formatted = `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
  const isUrgent = secondsLeft <= 120;

  return (
    <div
      className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-semibold ${
        secondsLeft === 0
          ? "bg-rose-100 text-rose-800"
          : isUrgent
          ? "bg-amber-100 text-amber-800"
          : "bg-indigo-50 text-indigo-800"
      }`}
    >
      <Clock className="h-4 w-4" />
      <span>{label}:</span>
      <span className="font-mono">{formatted}</span>
    </div>
  );
}
