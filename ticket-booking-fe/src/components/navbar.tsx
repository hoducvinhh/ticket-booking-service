"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Bell, LogOut, QrCode, Shield, Ticket, User } from "lucide-react";
import { api } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { Button } from "@/components/ui/button";

export function Navbar() {
  const router = useRouter();
  const { user, clearAuth } = useAuthStore();

  const roles = user?.roles ?? [];
  const isOrganizer = roles.includes("ORGANIZER") || roles.includes("ADMIN");
  const isStaff = roles.includes("CHECKIN_STAFF") || isOrganizer;
  const isAdmin = roles.includes("ADMIN");

  const handleLogout = async () => {
    try {
      await api.post("/auth/logout");
    } finally {
      clearAuth();
      router.push("/login");
    }
  };

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2 text-lg font-bold text-indigo-600">
            <Ticket className="h-6 w-6" />
            <span>FlashTix</span>
          </Link>
          <nav className="hidden md:flex items-center gap-4 text-sm font-medium text-slate-600">
            <Link href="/" className="hover:text-indigo-600">Events</Link>
            {user && (
              <>
                <Link href="/my-orders" className="hover:text-indigo-600">My Orders</Link>
                <Link href="/my-tickets" className="hover:text-indigo-600">My Tickets</Link>
                {isOrganizer && (
                  <Link href="/organizer" className="hover:text-indigo-600">Organizer</Link>
                )}
                {isStaff && (
                  <Link href="/staff" className="inline-flex items-center gap-1 hover:text-indigo-600">
                    <QrCode className="h-3.5 w-3.5" /> Check-in
                  </Link>
                )}
                {isAdmin && (
                  <Link href="/admin" className="inline-flex items-center gap-1 hover:text-indigo-600">
                    <Shield className="h-3.5 w-3.5" /> Admin
                  </Link>
                )}
              </>
            )}
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {user ? (
            <>
              <Link href="/notifications" className="rounded-lg p-2 text-slate-600 hover:bg-slate-100" title="Notifications">
                <Bell className="h-5 w-5" />
              </Link>
              <Link
                href="/profile"
                className="hidden sm:inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                <User className="h-4 w-4" />
                <span>{user.fullName}</span>
              </Link>
              <Button variant="outline" size="sm" onClick={handleLogout}>
                <LogOut className="h-4 w-4" />
                <span className="hidden sm:inline">Logout</span>
              </Button>
            </>
          ) : (
            <>
              <Link href="/login">
                <Button variant="ghost" size="sm">Login</Button>
              </Link>
              <Link href="/register">
                <Button size="sm">Sign up</Button>
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
