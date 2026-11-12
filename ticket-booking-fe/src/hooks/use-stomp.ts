"use client";

import { useEffect, useRef, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";

export interface StompEventPayload {
  type: string;
  eventId?: string;
  payload: Record<string, unknown>;
  timestamp?: string;
}

export function useStompSubscription(
  topic: string | null,
  onMessage: (msg: StompEventPayload) => void,
  onReconnect?: () => void
) {
  const [connected, setConnected] = useState(false);
  const onMessageRef = useRef(onMessage);
  const onReconnectRef = useRef(onReconnect);
  const hasConnectedOnce = useRef(false);

  useEffect(() => {
    onMessageRef.current = onMessage;
    onReconnectRef.current = onReconnect;
  }, [onMessage, onReconnect]);

  useEffect(() => {
    if (!topic) return;
    const rawWsBase = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";
    const brokerURL = rawWsBase.replace(/^http/, "ws");

    const client = new Client({
      brokerURL,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);
        if (hasConnectedOnce.current && onReconnectRef.current) {
          onReconnectRef.current();
        }
        hasConnectedOnce.current = true;
        client.subscribe(topic, (frame: IMessage) => {
          try {
            const parsed = JSON.parse(frame.body) as StompEventPayload;
            onMessageRef.current(parsed);
          } catch {
            // ignore malformed frame
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
    });

    client.activate();
    return () => {
      client.deactivate();
    };
  }, [topic]);

  return { connected };
}
