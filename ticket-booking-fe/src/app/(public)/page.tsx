"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Calendar, MapPin, Search, Sparkles } from "lucide-react";
import { api } from "@/lib/api";
import { formatCurrency, formatDateTime, formatEventStatus } from "@/lib/utils";
import { ApiEnvelope, EventItem, PageEnvelope } from "@/types";
import { Badge, Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function HomePage() {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [timeFilter, setTimeFilter] = useState<"all" | "upcoming" | "this-week">("all");

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["public-events", search, category, timeFilter],
    queryFn: async () => {
      const now = Date.now();
      const fromTime = timeFilter === "all" ? undefined : new Date(now).toISOString();
      const toTime =
        timeFilter === "this-week" ? new Date(now + 7 * 24 * 60 * 60 * 1000).toISOString() : undefined;
      const res = await api.get<ApiEnvelope<PageEnvelope<EventItem>>>("/events", {
        params: {
          search: search || undefined,
          category: category || undefined,
          fromTime,
          toTime,
          size: 12,
        },
      });
      return res.data.data;
    },
  });

  const events = data?.content ?? [];

  return (
    <div className="space-y-8">
      <section className="rounded-2xl bg-gradient-to-r from-indigo-600 via-violet-600 to-purple-600 p-8 text-white shadow-lg">
        <div className="max-w-2xl space-y-3">
          <div className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium">
            <Sparkles className="h-3.5 w-3.5" />
            Sơ đồ ghế thời gian thực &amp; Phòng chờ ảo chống nghẽn
          </div>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
            Khám phá &amp; Đặt vé sự kiện nhanh chóng, không lo trùng ghế
          </h1>
          <p className="text-indigo-100">
            Cập nhật các đêm nhạc, hội thảo công nghệ và giải đấu hấp dẫn. Giữ chỗ tức thì theo thời gian thực và nhận vé điện tử QR ngay sau khi thanh toán.
          </p>
        </div>

        <div className="mt-6 grid gap-3 rounded-xl bg-white p-3 text-slate-900 shadow-md sm:grid-cols-4">
          <div className="relative sm:col-span-2">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
            <input
              type="text"
              placeholder="Tìm sự kiện theo tên hoặc từ khóa..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-3 text-sm focus:border-indigo-500 focus:outline-none"
            />
          </div>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
          >
            <option value="">Tất cả danh mục</option>
            <option value="Music">Âm nhạc &amp; Concert</option>
            <option value="Tech">Công nghệ &amp; Hội thảo</option>
            <option value="Sports">Thể thao</option>
            <option value="Theater">Nghệ thuật &amp; Sân khấu</option>
          </select>
          <select
            value={timeFilter}
            onChange={(e) => setTimeFilter(e.target.value as "all" | "upcoming" | "this-week")}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
          >
            <option value="all">Mọi thời gian</option>
            <option value="upcoming">Sắp diễn ra</option>
            <option value="this-week">Trong 7 ngày tới</option>
          </select>
        </div>
      </section>

      {isLoading ? (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-72 animate-pulse rounded-xl bg-slate-200" />
          ))}
        </div>
      ) : isError ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <p className="text-sm text-rose-600">Không thể tải danh sách sự kiện. Vui lòng kiểm tra kết nối máy chủ.</p>
            <Button variant="outline" onClick={() => refetch()}>Thử lại</Button>
          </CardContent>
        </Card>
      ) : events.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-slate-500">
            Không tìm thấy sự kiện nào phù hợp với bộ lọc hiện tại.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {events.map((ev) => {
            const minPrice = ev.sections?.length
              ? Math.min(...ev.sections.map((s) => s.price))
              : null;
            return (
              <Card key={ev.id} className="flex flex-col overflow-hidden transition hover:shadow-md">
                <div className="h-40 bg-gradient-to-br from-slate-800 to-indigo-900 p-4 text-white flex flex-col justify-between">
                  <div className="flex items-center justify-between">
                    <Badge tone={ev.status === "SALES_OPEN" ? "success" : "info"}>
                      {formatEventStatus(ev.status)}
                    </Badge>
                    {ev.waitingRoomEnabled && <Badge tone="warning">Có phòng chờ</Badge>}
                  </div>
                  <h2 className="line-clamp-2 text-lg font-bold">{ev.name}</h2>
                </div>
                <CardContent className="flex flex-1 flex-col justify-between gap-4">
                  <div className="space-y-2 text-sm text-slate-600">
                    <div className="flex items-center gap-2">
                      <Calendar className="h-4 w-4 text-indigo-500" />
                      <span>{formatDateTime(ev.startTime)}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <MapPin className="h-4 w-4 text-indigo-500" />
                      <span>{ev.venueName || "Địa điểm chính"}</span>
                    </div>
                  </div>
                  <div className="flex items-center justify-between border-t border-slate-100 pt-3">
                    <div>
                      <p className="text-xs text-slate-500">Giá từ</p>
                      <p className="font-semibold text-indigo-600">
                        {minPrice !== null ? formatCurrency(minPrice) : "Đang cập nhật"}
                      </p>
                    </div>
                    <Link href={`/events/${ev.id}`}>
                      <Button size="sm">Xem chi tiết</Button>
                    </Link>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
