"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { ApiEnvelope, UserProfile } from "@/types";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      })
  );

  const setAuth = useAuthStore((s) => s.setAuth);
  const setHydrated = useAuthStore((s) => s.setHydrated);

  useEffect(() => {
    api
      .get<ApiEnvelope<UserProfile>>("/users/me")
      .then((res) => setAuth(res.data.data))
      .catch(() => setHydrated(true));
  }, [setAuth, setHydrated]);

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
