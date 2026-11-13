export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  status: string;
  roles: string[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: UserProfile;
}

export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface PageEnvelope<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface SectionSummary {
  id: string;
  name: string;
  price: number;
  capacity: number;
  type: string;
}

export interface EventItem {
  id: string;
  organizerId: string;
  venueId: string;
  venueName?: string;
  venueAddress?: string;
  name: string;
  slug: string;
  category?: string;
  description?: string;
  bannerUrl?: string;
  startTime: string;
  endTime: string;
  salesStartTime: string;
  salesEndTime: string;
  waitingRoomEnabled: boolean;
  maxConcurrentUsers: number;
  maxSeatsPerReservation: number;
  status: string;
  sections: SectionSummary[];
}

export interface SeatNode {
  id: string;
  seatCode: string;
  rowLabel: string;
  seatNumber: number;
  status: "AVAILABLE" | "HELD" | "SOLD" | "BLOCKED";
  price: number;
}

export interface SectionSeatMap {
  sectionId: string;
  sectionName: string;
  price: number;
  type: string;
  seats: SeatNode[];
}

export interface SeatMapResponse {
  eventId: string;
  sections: SectionSeatMap[];
}

export interface ReservationResponse {
  reservationId: string;
  eventId: string;
  userId: string;
  status: string;
  expiresAt: string;
  totalAmount: number;
  createdAt: string;
  items: {
    seatId: string;
    seatCode?: string;
    sectionName?: string;
    unitPrice: number;
  }[];
}

export interface OrderResponse {
  id: string;
  userId: string;
  reservationId: string;
  eventId: string;
  eventName?: string;
  orderCode: string;
  totalAmount: number;
  currency: string;
  status: string;
  createdAt: string;
  reservationExpiresAt?: string;
  items: {
    seatId: string;
    seatCode?: string;
    unitPrice: number;
    quantity: number;
    subtotal: number;
  }[];
}

export interface CheckoutResponse {
  paymentId: string;
  orderId: string;
  orderCode: string;
  provider: string;
  amount: number;
  status: string;
  paymentUrl: string;
}

export interface TicketItem {
  id: string;
  orderId: string;
  eventId: string;
  eventName?: string;
  seatId?: string;
  seatCode?: string;
  sectionName?: string;
  ticketCode: string;
  status: string;
  checkedInAt?: string;
  issuedAt: string;
}

export interface TicketQrResponse {
  ticketId: string;
  ticketCode: string;
  qrToken: string;
  qrImageBase64: string;
  status: string;
}

export interface WaitingRoomStatus {
  eventId: string;
  userId: string;
  status: "WAITING" | "ADMITTED" | "EXPIRED" | "LEFT";
  queuePosition?: number;
  totalWaiting: number;
  estimatedWaitSeconds: number;
  admissionToken?: string;
  tokenExpiresAt?: string;
}

export interface NotificationItem {
  id: string;
  userId: string;
  type: string;
  title: string;
  content: string;
  channel: string;
  read: boolean;
  createdAt: string;
}

export interface VenueItem {
  id: string;
  organizerId: string;
  name: string;
  address?: string;
  description?: string;
  capacity: number;
  createdAt: string;
}

export interface TicketTypeItem {
  id: string;
  eventId: string;
  sectionId?: string;
  name: string;
  price: number;
  totalQuantity: number;
  soldQuantity: number;
  remainingQuantity: number;
  saleMode: string;
}

export interface AvailabilityResponse {
  eventId: string;
  totalSeats: number;
  availableSeats: number;
  heldSeats: number;
  soldSeats: number;
  blockedSeats: number;
}
