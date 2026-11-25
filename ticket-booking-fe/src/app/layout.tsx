import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";
import { Navbar } from "@/components/navbar";

export const metadata: Metadata = {
  title: "FlashTix | Nền tảng Đặt vé Sự kiện & Flash Sale",
  description: "Hệ thống đặt vé sự kiện trực tuyến chịu tải cao với sơ đồ ghế thời gian thực và phòng chờ ảo",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi">
      <body className="min-h-screen bg-slate-50 text-slate-900 antialiased">
        <Providers>
          <Navbar />
          <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6">{children}</main>
        </Providers>
      </body>
    </html>
  );
}
