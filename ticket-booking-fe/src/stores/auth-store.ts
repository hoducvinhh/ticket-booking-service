import { create } from "zustand";
import { UserProfile } from "@/types";

interface AuthState {
  user: UserProfile | null;
  accessToken: string | null;
  hydrated: boolean;
  setAuth: (user: UserProfile | null, accessToken?: string | null) => void;
  setHydrated: (val: boolean) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  hydrated: false,
  setAuth: (user, accessToken = null) =>
    set((state) => ({
      user,
      accessToken: accessToken !== undefined ? accessToken : state.accessToken,
      hydrated: true,
    })),
  setHydrated: (hydrated) => set({ hydrated }),
  clearAuth: () => set({ user: null, accessToken: null, hydrated: true }),
}));
