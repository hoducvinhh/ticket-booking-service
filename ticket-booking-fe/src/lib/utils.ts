import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatCurrency(amount: number, currency = "VND"): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(iso));
}

const EVENT_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Bản nháp",
  PUBLISHED: "Sắp mở bán",
  SALES_OPEN: "Đang mở bán",
  SALES_CLOSED: "Đã đóng bán",
  COMPLETED: "Đã diễn ra",
  CANCELLED: "Đã hủy",
};

const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  PAID: "Đã thanh toán",
  EXPIRED: "Hết hạn",
  CANCELLED: "Đã hủy",
  REFUND_PENDING: "Chờ hoàn tiền",
  REFUNDED: "Đã hoàn tiền",
};

const TICKET_STATUS_LABELS: Record<string, string> = {
  VALID: "Còn hiệu lực",
  CHECKED_IN: "Đã check-in",
  CANCELLED: "Đã hủy",
  REFUNDED: "Đã hoàn tiền",
};

const SEAT_STATUS_LABELS: Record<string, string> = {
  AVAILABLE: "Còn trống",
  HELD: "Đang giữ chỗ",
  SOLD: "Đã bán",
  BLOCKED: "Tạm khóa",
};

const USER_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Hoạt động",
  LOCKED: "Đang khóa",
  DISABLED: "Vô hiệu hóa",
};

const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: "Khách hàng",
  ORGANIZER: "Ban tổ chức",
  CHECKIN_STAFF: "Nhân viên soát vé",
  ADMIN: "Quản trị viên",
};

const CATEGORY_LABELS: Record<string, string> = {
  Music: "Âm nhạc",
  Tech: "Công nghệ",
  Sports: "Thể thao",
  Theater: "Nghệ thuật",
};

export function formatEventStatus(status: string): string {
  return EVENT_STATUS_LABELS[status] ?? status;
}

export function formatOrderStatus(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

export function formatTicketStatus(status: string): string {
  return TICKET_STATUS_LABELS[status] ?? status;
}

export function formatSeatStatus(status: string): string {
  return SEAT_STATUS_LABELS[status] ?? status;
}

export function formatUserStatus(status: string): string {
  return USER_STATUS_LABELS[status] ?? status;
}

export function formatRoleName(role: string): string {
  return ROLE_LABELS[role] ?? role;
}

export function formatCategory(category?: string | null): string {
  if (!category) return "";
  return CATEGORY_LABELS[category] ?? category;
}
