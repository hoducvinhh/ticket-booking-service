# PROJECT SPECIFICATION
# EVENT TICKETING & FLASH SALE PLATFORM
### High-Concurrency Ticket Booking & Virtual Waiting Room System

**Phiên bản:** 1.0  
**Định hướng:** Fresher / Junior Backend Developer  
**Backend:** Java 21 + Spring Boot 3  
**Frontend:** Next.js + TypeScript  
**Database:** PostgreSQL  
**Cache & Coordination:** Redis  
**Message Broker:** RabbitMQ  
**Real-time:** WebSocket  
**Testing:** JUnit 5 + Testcontainers + Playwright + k6  
**Deployment:** Docker + GitHub Actions

---

# 1. TỔNG QUAN DỰ ÁN

## 1.1. Giới thiệu

Event Ticketing & Flash Sale Platform là hệ thống đặt vé sự kiện trực tuyến, cho phép người dùng tìm kiếm sự kiện, lựa chọn vé hoặc ghế ngồi, giữ chỗ tạm thời, thanh toán trực tuyến và nhận vé điện tử.

Hệ thống được thiết kế để giải quyết bài toán có nhiều người dùng truy cập và thực hiện giao dịch đồng thời, đặc biệt trong các đợt mở bán vé Flash Sale.

Ví dụ:

Một sự kiện concert có 100 vé VIP. Vào thời điểm mở bán, 10.000 người dùng cùng truy cập và thực hiện thao tác đặt vé trong thời gian rất ngắn.

Hệ thống phải đảm bảo:

- Không xảy ra tình trạng một ghế được bán cho nhiều người.
- Không bán vượt quá số lượng vé tồn tại.
- Không tạo nhiều đơn hàng hoặc giao dịch thanh toán do request bị gửi lặp.
- Tự động giải phóng ghế khi người dùng không thanh toán trong thời gian quy định.
- Cập nhật trạng thái ghế theo thời gian thực.
- Phân phối lưu lượng truy cập thông qua Virtual Waiting Room.
- Xử lý thanh toán và các tác vụ phụ một cách bất đồng bộ.
- Có khả năng theo dõi, kiểm thử và đánh giá hiệu năng.

## 1.2. Mục tiêu dự án

Dự án hướng đến việc thể hiện năng lực phát triển một hệ thống backend có nghiệp vụ thực tế, không chỉ dừng lại ở các chức năng CRUD thông thường.

Các mục tiêu kỹ thuật:

1. Thiết kế kiến trúc backend theo Modular Monolith.
2. Thiết kế database đảm bảo tính nhất quán dữ liệu.
3. Xử lý concurrency và race condition.
4. Sử dụng transaction, row-level locking và unique constraints.
5. Sử dụng Redis để caching, TTL, coordination và waiting room.
6. Xây dựng hệ thống xử lý bất đồng bộ với RabbitMQ.
7. Tích hợp cổng thanh toán và xử lý webhook idempotent.
8. Xây dựng hệ thống realtime bằng WebSocket.
9. Kiểm thử bằng Unit Test, Integration Test, E2E và Load Test.
10. Triển khai hệ thống bằng Docker và CI/CD.

## 1.3. Phạm vi sản phẩm

Hệ thống bao gồm ba nền tảng chính:

**Customer Platform**

- Đăng ký, đăng nhập.
- Tìm kiếm và xem sự kiện.
- Xem sơ đồ ghế.
- Tham gia phòng chờ ảo.
- Giữ ghế và đặt vé.
- Thanh toán trực tuyến.
- Nhận vé điện tử có QR Code.
- Xem lịch sử đơn hàng.
- Quản lý vé đã mua.

**Organizer Platform**

- Quản lý sự kiện.
- Quản lý địa điểm.
- Quản lý khu vực và ghế.
- Quản lý loại vé và giá vé.
- Theo dõi số lượng vé đã bán.
- Theo dõi doanh thu.
- Quản lý check-in.
- Xem báo cáo sự kiện.

**Admin Platform**

- Quản lý người dùng.
- Quản lý organizer.
- Quản lý sự kiện.
- Quản lý tài khoản nhân viên.
- Theo dõi đơn hàng và giao dịch.
- Theo dõi hoạt động hệ thống.
- Quản lý các trường hợp vi phạm hoặc sự cố.

---

# 2. ĐỐI TƯỢNG SỬ DỤNG VÀ PHÂN QUYỀN

## 2.1. Các nhóm người dùng

| Actor | Mô tả |
|---|---|
| Guest | Người chưa đăng nhập |
| Customer | Người mua vé |
| Organizer | Người tổ chức sự kiện |
| Check-in Staff | Nhân viên kiểm soát vé |
| Admin | Quản trị viên hệ thống |

## 2.2. Quyền hạn

### Guest

- Xem danh sách sự kiện công khai.
- Tìm kiếm sự kiện.
- Xem chi tiết sự kiện.
- Xem sơ đồ ghế và tình trạng khả dụng.
- Tham gia waiting room nếu hệ thống cho phép theo session.

### Customer

- Toàn bộ quyền của Guest.
- Đặt và giữ vé.
- Thanh toán.
- Hủy reservation khi còn hợp lệ.
- Xem đơn hàng.
- Xem vé điện tử.
- Quản lý thông tin cá nhân.

### Organizer

- Tạo và quản lý sự kiện thuộc quyền sở hữu.
- Quản lý địa điểm, khu vực và ghế.
- Quản lý loại vé.
- Xem báo cáo bán vé.
- Quản lý nhân viên check-in của sự kiện.
- Theo dõi danh sách khách hàng đã mua vé.

### Check-in Staff

- Xem các sự kiện được phân công.
- Quét mã QR.
- Xác thực vé.
- Check-in khách tham dự.
- Xem lịch sử check-in thuộc phạm vi được cấp quyền.

### Admin

- Quản lý toàn bộ người dùng.
- Quản lý organizer.
- Quản lý sự kiện.
- Khóa tài khoản.
- Xử lý các trường hợp sự kiện vi phạm.
- Xem báo cáo tổng quan.

## 2.3. Authorization

Sử dụng RBAC kết hợp kiểm tra ownership.

Ví dụ:

- CUSTOMER chỉ được xem order của chính mình.
- ORGANIZER chỉ được cập nhật event do mình sở hữu.
- CHECKIN_STAFF chỉ được check-in tại event được phân công.
- ADMIN có quyền quản trị toàn hệ thống.

Không chỉ kiểm tra role. Backend phải kiểm tra quyền truy cập tài nguyên cụ thể để tránh IDOR.

---

# 3. FUNCTIONAL REQUIREMENTS

## 3.1. Authentication & User Management

Các chức năng:

- Đăng ký tài khoản bằng email.
- Đăng nhập bằng email/password.
- Đăng xuất.
- Refresh token hoặc quản lý session.
- Quên mật khẩu.
- Đặt lại mật khẩu.
- Thay đổi mật khẩu.
- Cập nhật thông tin cá nhân.
- Xem thông tin tài khoản.
- Khóa hoặc vô hiệu hóa tài khoản.

Business Rules:

- Email phải duy nhất.
- Password phải được hash bằng BCrypt hoặc Argon2.
- Tài khoản bị khóa không được thực hiện giao dịch.
- Access token có thời gian sống ngắn.
- Refresh token phải có cơ chế thu hồi.
- Không trả password hash trong API response.

## 3.2. Event Management

Organizer có thể:

- Tạo sự kiện.
- Chỉnh sửa sự kiện.
- Upload banner.
- Thiết lập địa điểm.
- Thiết lập thời gian tổ chức.
- Thiết lập thời gian mở bán.
- Thiết lập thời gian đóng bán.
- Công khai sự kiện.
- Ẩn sự kiện.
- Hủy sự kiện.

Thông tin Event:

| Field | Description |
|---|---|
| id | ID sự kiện |
| organizer_id | Chủ sở hữu |
| venue_id | Địa điểm |
| name | Tên sự kiện |
| slug | URL thân thiện |
| description | Mô tả |
| banner_url | Banner |
| start_time | Thời gian bắt đầu |
| end_time | Thời gian kết thúc |
| sales_start_time | Mở bán |
| sales_end_time | Đóng bán |
| status | Trạng thái |
| created_at | Ngày tạo |
| updated_at | Ngày cập nhật |

Event Status:

- DRAFT
- PUBLISHED
- SALES_OPEN
- SALES_CLOSED
- COMPLETED
- CANCELLED

Business Rules:

- Chỉ sự kiện PUBLISHED mới hiển thị công khai.
- Chỉ được giữ vé trong khoảng thời gian mở bán.
- Thời gian kết thúc phải sau thời gian bắt đầu.
- Thời gian mở bán phải được cấu hình hợp lệ.
- Chỉ organizer sở hữu hoặc admin mới được cập nhật sự kiện.
- Không xóa vật lý sự kiện đã phát sinh giao dịch.
- Hủy sự kiện phải có quy trình xử lý đơn hàng và hoàn tiền.

## 3.3. Venue Management

Quản lý địa điểm tổ chức sự kiện.

Một Venue có thể chứa nhiều Section.

Ví dụ:

```text
Venue: National Convention Center

├── Section VIP
│   ├── A01
│   ├── A02
│   └── A03
│
├── Section PREMIUM
│   ├── B01
│   ├── B02
│   └── B03
│
└── Section STANDARD
    ├── C01
    ├── C02
    └── C03
```

Venue gồm:

- Tên địa điểm.
- Địa chỉ.
- Mô tả.
- Sức chứa.
- Danh sách khu vực.

Section gồm:

- Tên khu vực.
- Giá vé.
- Sức chứa.
- Loại khu vực.
- Danh sách ghế.

Seat gồm:

- Seat Code.
- Row Label.
- Seat Number.
- Section ID.
- Trạng thái.

Seat Status:

- AVAILABLE
- HELD
- SOLD
- BLOCKED

Trạng thái HELD và AVAILABLE cần được xác định dựa trên claim/reservation còn hiệu lực, tránh phụ thuộc vào một cột trạng thái bị cập nhật chậm.

## 3.4. Ticket Management

Hệ thống hỗ trợ hai loại hình đặt vé.

**Reserved Seating**

Người dùng chọn ghế cụ thể.

- Mỗi ghế chỉ được bán một lần.
- Có sơ đồ ghế trực quan.
- Có trạng thái ghế realtime.
- Phù hợp concert, hội trường, rạp chiếu phim.

**General Admission**

Người dùng mua số lượng vé mà không chọn ghế.

- Quản lý inventory theo số lượng.
- Không cần sơ đồ ghế.
- Phù hợp sự kiện ngoài trời, festival.

Trong phiên bản đầu tiên, ưu tiên Reserved Seating để tập trung giải quyết concurrency.

General Admission là chức năng mở rộng sau khi core booking hoạt động ổn định.

## 3.5. Reservation Management

Reservation là nghiệp vụ giữ vé tạm thời.

Luồng:

1. User chọn ghế.
2. Frontend gửi danh sách seat ID.
3. Backend xác thực người dùng.
4. Backend kiểm tra event đang mở bán.
5. Backend kiểm tra admission token nếu event có waiting room.
6. Backend kiểm tra các ghế có hợp lệ không.
7. Backend thực hiện transaction giữ ghế.
8. Backend tạo reservation.
9. Backend tạo reservation items.
10. Backend tạo seat claims.
11. Backend trả về reservation ID và expiration time.
12. Frontend hiển thị countdown 10 phút.

Reservation Status:

- ACTIVE
- CONFIRMED
- EXPIRED
- CANCELLED

Business Rules:

- Mỗi user chỉ được giữ tối đa N ghế trong một reservation.
- Một ghế không được có nhiều claim đang hiệu lực.
- Reservation chỉ có hiệu lực trong 10 phút.
- Người dùng không được thanh toán reservation hết hạn.
- Người dùng có thể hủy reservation khi còn ACTIVE.
- Khi thanh toán thành công, reservation chuyển sang CONFIRMED.
- Giá vé phải được snapshot tại thời điểm giữ vé.

## 3.6. Order Management

Order được tạo dựa trên reservation hợp lệ.

Thông tin Order:

- Order ID.
- User ID.
- Reservation ID.
- Order Code.
- Total Amount.
- Currency.
- Status.
- Created At.
- Updated At.

Order Status:

- PENDING_PAYMENT
- PAID
- EXPIRED
- CANCELLED
- REFUND_PENDING
- REFUNDED

Business Rules:

- Một reservation chỉ được chuyển thành đơn hàng thanh toán theo quy trình hợp lệ.
- Backend tự tính tổng tiền.
- Không tin giá tiền do frontend gửi.
- Không được sửa giá order sau khi thanh toán.
- Không cho phép thanh toán order đã hết hạn.
- Không được tạo nhiều đơn hàng thanh toán thành công cho cùng một reservation.

## 3.7. Payment Management

Tích hợp:

- PayOS Sandbox.
- VNPay Sandbox.

Payment Flow:

1. User chọn thanh toán.
2. Backend kiểm tra order.
3. Backend tạo payment attempt.
4. Backend gửi yêu cầu đến payment provider.
5. Provider trả về payment URL.
6. Frontend chuyển hướng người dùng.
7. Người dùng thực hiện thanh toán.
8. Provider gửi webhook.
9. Backend xác minh chữ ký webhook.
10. Backend kiểm tra amount, order reference và trạng thái giao dịch.
11. Backend xử lý idempotency.
12. Backend cập nhật Payment và Order.
13. Backend phát hành Ticket.

Payment Status:

- PENDING
- PROCESSING
- SUCCESS
- FAILED
- EXPIRED
- REFUND_PENDING
- REFUNDED

Business Rules:

- Không xác nhận thanh toán chỉ dựa vào redirect URL.
- Phải xác thực webhook.
- Amount phải khớp với order.
- Provider transaction ID phải được kiểm soát trùng lặp.
- Webhook gửi nhiều lần không được tạo nhiều ticket.
- Các trạng thái giao dịch không được chuyển ngược một cách tùy tiện.

## 3.8. Ticket Issuance

Sau khi thanh toán thành công, hệ thống phát hành vé điện tử.

Thông tin Ticket:

- Ticket ID.
- Ticket Code.
- Order ID.
- Event ID.
- Seat ID.
- QR Token.
- Status.
- Issued At.
- Checked In At.

Ticket Status:

- VALID
- CHECKED_IN
- CANCELLED
- REFUNDED

Chức năng:

- Tạo ticket code.
- Sinh QR token.
- Lưu thông tin vé.
- Gửi email.
- Hiển thị vé trong tài khoản.
- Cho phép người dùng xem lại QR.

QR token cần được ký bằng HMAC hoặc JWT. Không nên chỉ đưa ticket ID vào QR rồi xem đó là cơ chế bảo mật.

## 3.9. Check-in Management

Staff có thể quét QR Code để xác thực vé.

Quy trình:

1. Staff đăng nhập.
2. Chọn sự kiện được phân công.
3. Scan QR.
4. Frontend gửi QR token lên backend.
5. Backend xác minh chữ ký.
6. Backend tìm ticket.
7. Backend kiểm tra trạng thái.
8. Backend chuyển VALID thành CHECKED_IN.
9. Backend ghi nhận thời gian và nhân viên thực hiện.

Business Rules:

- Vé phải thuộc sự kiện được phân công.
- Vé phải hợp lệ.
- Vé chưa được sử dụng.
- Một vé chỉ được check-in một lần.
- Check-in phải được xử lý atomic.
- Mỗi lần check-in phải có audit log.

## 3.10. Virtual Waiting Room

Virtual Waiting Room quản lý lưu lượng người dùng trong các đợt Flash Sale.

Ví dụ:

- 10.000 người truy cập.
- Hệ thống chỉ cho phép tối đa 300 người cùng vào booking.
- Những người còn lại phải chờ.

Chức năng:

- Join queue.
- Xem vị trí hàng đợi.
- Xem số lượng người đang chờ.
- Xem trạng thái admission.
- Nhận admission token.
- Rời queue.
- Tự động hết hạn token.

Redis Sorted Set có thể được sử dụng để quản lý thứ tự hàng đợi.

Thông tin:

- Event ID.
- User ID hoặc Session ID.
- Queue Position.
- Joined At.
- Admission Status.
- Admission Token.
- Token Expiration.

Backend phải kiểm tra admission token khi người dùng thực hiện giữ ghế. Không được chỉ dựa vào frontend để kiểm soát quyền vào booking.

## 3.11. Notification Management

Các loại thông báo:

- Đăng ký thành công.
- Đơn hàng được tạo.
- Thanh toán thành công.
- Vé được phát hành.
- Reservation sắp hết hạn.
- Sự kiện sắp diễn ra.
- Sự kiện bị hủy.
- Hoàn tiền thành công.

Các kênh:

- Email.
- In-app Notification.
- WebSocket.

Email và các tác vụ không quan trọng đối với response chính nên được xử lý bất đồng bộ.

## 3.12. Reporting & Analytics

Organizer có thể theo dõi:

- Tổng số vé.
- Tổng số vé đã bán.
- Số vé đang giữ.
- Số vé còn lại.
- Tổng đơn hàng.
- Doanh thu.
- Tỷ lệ thanh toán thành công.
- Số lượt check-in.

Admin có thể theo dõi:

- Tổng người dùng.
- Tổng sự kiện.
- Tổng đơn hàng.
- Tổng giao dịch.
- Tổng doanh thu.
- Payment thất bại.
- Webhook lỗi.
- Queue backlog.
- Reservation expiration backlog.

---

# 4. NON-FUNCTIONAL REQUIREMENTS

## 4.1. Consistency

- Không bán một ghế cho hai người.
- Không bán vượt quá inventory.
- Không phát hành trùng vé.
- Không xác nhận payment trùng.
- Không check-in một vé nhiều lần.

## 4.2. Performance

Mục tiêu ban đầu để đánh giá trong môi trường staging:

- API đọc thông tin sự kiện có p95 dưới 500ms.
- API lấy seat map có p95 dưới 500ms.
- API giữ ghế có p95 dưới 1 giây trong kịch bản contention được xác định.
- Webhook xử lý và phản hồi ACK nhanh sau khi đã lưu nhận sự kiện an toàn.
- Email không làm chậm response thanh toán.

Đây là mục tiêu thử nghiệm, không phải cam kết hiệu năng mặc định.

## 4.3. Scalability

- Có thể tăng số instance Spring Boot.
- Redis được sử dụng cho các dữ liệu phù hợp.
- Database có index hợp lý.
- Background worker có thể tăng số consumer.
- Frontend và backend được triển khai độc lập.

## 4.4. Availability & Reliability

- Có health check.
- Có retry với giới hạn.
- Có dead-letter queue.
- Có cơ chế phục hồi các job chưa xử lý.
- Có database backup.
- Có logging và monitoring.

## 4.5. Security

- Authentication.
- Authorization.
- Password hashing.
- Input validation.
- Rate limiting.
- Webhook signature verification.
- Secure QR token.
- Audit log.
- HTTPS.
- Không lưu thông tin thẻ thanh toán nhạy cảm trên hệ thống.

---

# 5. SYSTEM ARCHITECTURE

## 5.1. Kiến trúc tổng thể

Sử dụng Modular Monolith thay vì Microservices trong phiên bản đầu tiên.

Lý do:

- Phù hợp quy mô dự án Fresher/Junior.
- Dễ quản lý transaction.
- Dễ debug.
- Giảm độ phức tạp triển khai.
- Có thể tách module thành service riêng trong tương lai.

Kiến trúc:

```text
                    CLIENT
                      |
              NEXT.JS FRONTEND
                      |
                REST / WSS
                      |
                      v
              SPRING BOOT API
                      |
       +--------------+--------------+
       |              |              |
       v              v              v
  PostgreSQL        Redis         RabbitMQ
       |              |              |
       |              |              |
       v              v              v
 Business Data    Cache/TTL      Background Jobs
 Reservations     Waiting Room   Email
 Orders            Rate Limit    Notification
 Payments          Coordination   Ticket Processing
 Tickets
       |
       v
 Payment Provider
 PayOS / VNPay
```

## 5.2. Backend Modules

1. Auth Module.
2. User Module.
3. Event Module.
4. Venue Module.
5. Ticket Module.
6. Inventory Module.
7. Reservation Module.
8. Waiting Room Module.
9. Order Module.
10. Payment Module.
11. Notification Module.
12. Check-in Module.
13. Reporting Module.
14. Admin Module.

## 5.3. Backend Layered Architecture

```text
Controller
    |
    v
Application Service
    |
    v
Domain Logic
    |
    v
Repository
    |
    v
PostgreSQL
```

**Controller**

- Nhận request.
- Validate DTO.
- Gọi service.
- Trả response.

**Application Service**

- Điều phối nghiệp vụ.
- Quản lý transaction.
- Gọi repository.
- Gọi các integration service.

**Domain**

- Entity.
- Enum.
- Business Rules.
- Domain Services.

**Repository**

- Truy vấn dữ liệu.
- Lưu dữ liệu.
- Các query chuyên biệt.

Không xử lý nghiệp vụ trong Controller và không để Entity trực tiếp làm API response.

## 5.4. Backend Project Structure

```text
backend/
└── src/main/java/com/example/ticketing/
    ├── common/
    │   ├── config/
    │   ├── exception/
    │   ├── response/
    │   ├── security/
    │   └── util/
    │
    ├── auth/
    ├── user/
    ├── event/
    ├── venue/
    ├── ticket/
    ├── inventory/
    ├── reservation/
    ├── waitingroom/
    ├── order/
    ├── payment/
    ├── notification/
    ├── checkin/
    ├── reporting/
    └── admin/
```

Mỗi module nên có các thành phần cần thiết:

```text
module/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
└── enums/
```

Không bắt buộc mọi module phải có đủ tất cả thư mục nếu không cần thiết.

## 5.5. Frontend Project Structure

```text
frontend/
└── src/
    ├── app/
    │   ├── (public)/
    │   │   ├── events/
    │   │   └── page.tsx
    │   │
    │   ├── (auth)/
    │   │   ├── login/
    │   │   └── register/
    │   │
    │   ├── booking/
    │   │   └── [eventId]/
    │   │
    │   ├── waiting-room/
    │   ├── checkout/
    │   ├── my-orders/
    │   ├── my-tickets/
    │   ├── organizer/
    │   ├── staff/
    │   └── admin/
    │
    ├── components/
    │   ├── ui/
    │   ├── seat-map/
    │   ├── event-card/
    │   └── countdown/
    │
    ├── features/
    │   ├── auth/
    │   ├── events/
    │   ├── booking/
    │   ├── orders/
    │   └── tickets/
    │
    ├── hooks/
    ├── lib/
    ├── services/
    ├── stores/
    └── types/
```

Frontend Technologies:

| Technology | Purpose |
|---|---|
| Next.js | Frontend framework |
| TypeScript | Type safety |
| Tailwind CSS | Styling |
| shadcn/ui | UI components |
| TanStack Query | Server state |
| Zustand | Client state |
| React Hook Form | Form management |
| Zod | Validation |
| Socket.IO Client hoặc STOMP Client | Realtime |
| Axios / Fetch | HTTP |

---

# 6. DATABASE DESIGN

## 6.1. Database Overview

Sử dụng PostgreSQL làm primary database và nguồn dữ liệu chuẩn của hệ thống.

Các bảng được chia thành nhóm:

**Identity**

- users
- roles
- user_roles

**Event**

- venues
- events
- ticket_sections
- seats
- ticket_types
- event_staff

**Booking**

- reservations
- reservation_items
- seat_claims
- orders
- order_items

**Payment**

- payments
- webhook_events

**Ticket**

- tickets
- checkin_logs

**System**

- notifications
- waiting_room_entries
- outbox_events
- audit_logs
- idempotency_records

## 6.2. Users

| Column | Type |
|---|---|
| id | UUID |
| email | VARCHAR |
| password_hash | VARCHAR |
| full_name | VARCHAR |
| phone | VARCHAR |
| status | VARCHAR |
| created_at | TIMESTAMPTZ |
| updated_at | TIMESTAMPTZ |

Constraints:

- PRIMARY KEY(id).
- UNIQUE(email).

## 6.3. Roles

| Column | Type |
|---|---|
| id | UUID |
| name | VARCHAR |
| description | TEXT |

Unique:

- name.

## 6.4. User Roles

| Column | Type |
|---|---|
| user_id | UUID |
| role_id | UUID |

Primary key:

- (user_id, role_id).

## 6.5. Venues

| Column | Type |
|---|---|
| id | UUID |
| name | VARCHAR |
| address | TEXT |
| description | TEXT |
| capacity | INT |
| created_at | TIMESTAMPTZ |

## 6.6. Events

| Column | Type |
|---|---|
| id | UUID |
| organizer_id | UUID |
| venue_id | UUID |
| name | VARCHAR |
| slug | VARCHAR |
| description | TEXT |
| banner_url | TEXT |
| start_time | TIMESTAMPTZ |
| end_time | TIMESTAMPTZ |
| sales_start_time | TIMESTAMPTZ |
| sales_end_time | TIMESTAMPTZ |
| status | VARCHAR |
| created_at | TIMESTAMPTZ |
| updated_at | TIMESTAMPTZ |

Index:

```sql
CREATE UNIQUE INDEX idx_events_slug
ON events(slug);

CREATE INDEX idx_events_status_sales
ON events(status, sales_start_time);

CREATE INDEX idx_events_organizer
ON events(organizer_id, created_at);
```

## 6.7. Ticket Sections

| Column | Type |
|---|---|
| id | UUID |
| event_id | UUID |
| name | VARCHAR |
| price | BIGINT |
| capacity | INT |
| type | VARCHAR |

Unique:

- (event_id, name).

## 6.8. Seats

| Column | Type |
|---|---|
| id | UUID |
| section_id | UUID |
| seat_code | VARCHAR |
| row_label | VARCHAR |
| seat_number | INT |
| status | VARCHAR |

Unique:

- (section_id, seat_code).

## 6.9. Reservations

| Column | Type |
|---|---|
| id | UUID |
| user_id | UUID |
| event_id | UUID |
| status | VARCHAR |
| expires_at | TIMESTAMPTZ |
| created_at | TIMESTAMPTZ |
| updated_at | TIMESTAMPTZ |

Index:

```sql
CREATE INDEX idx_reservation_expiry
ON reservations(status, expires_at);

CREATE INDEX idx_reservation_user
ON reservations(user_id, created_at);
```

## 6.10. Reservation Items

| Column | Type |
|---|---|
| id | UUID |
| reservation_id | UUID |
| seat_id | UUID |
| unit_price | BIGINT |

Lưu snapshot giá vé tại thời điểm reservation.

## 6.11. Seat Claims

Seat Claims là bảng đảm bảo một ghế chỉ có tối đa một claim hiện tại.

| Column | Type |
|---|---|
| seat_id | UUID PRIMARY KEY |
| reservation_id | UUID |
| user_id | UUID |
| expires_at | TIMESTAMPTZ |
| created_at | TIMESTAMPTZ |

SQL:

```sql
CREATE TABLE seat_claims (
    seat_id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

Khi reservation hết hạn, hệ thống phải giải phóng claim trong transaction.

Không được chỉ kiểm tra expires_at rồi bỏ qua claim cũ mà không xử lý việc xóa hoặc thay thế claim.

## 6.12. Orders

| Column | Type |
|---|---|
| id | UUID |
| user_id | UUID |
| reservation_id | UUID |
| order_code | VARCHAR |
| total_amount | BIGINT |
| currency | VARCHAR |
| status | VARCHAR |
| created_at | TIMESTAMPTZ |

Unique:

- order_code.
- reservation_id nếu thiết kế một reservation chỉ tạo tối đa một order.

## 6.13. Order Items

| Column | Type |
|---|---|
| id | UUID |
| order_id | UUID |
| seat_id | UUID |
| ticket_type_id | UUID |
| unit_price | BIGINT |
| quantity | INT |
| subtotal | BIGINT |

Đối với reserved seating, mỗi item thường tương ứng một ghế.

## 6.14. Payments

| Column | Type |
|---|---|
| id | UUID |
| order_id | UUID |
| provider | VARCHAR |
| provider_payment_id | VARCHAR |
| idempotency_key | VARCHAR |
| amount | BIGINT |
| status | VARCHAR |
| created_at | TIMESTAMPTZ |
| updated_at | TIMESTAMPTZ |

Index:

- order_id.
- provider_payment_id.
- UNIQUE(provider, provider_payment_id) khi provider_payment_id có giá trị.

## 6.15. Webhook Events

| Column | Type |
|---|---|
| id | UUID |
| provider | VARCHAR |
| provider_event_id | VARCHAR |
| payload_hash | VARCHAR |
| status | VARCHAR |
| received_at | TIMESTAMPTZ |
| processed_at | TIMESTAMPTZ |

Unique:

```sql
UNIQUE(provider, provider_event_id)
```

## 6.16. Tickets

| Column | Type |
|---|---|
| id | UUID |
| order_id | UUID |
| event_id | UUID |
| seat_id | UUID |
| ticket_code | VARCHAR |
| qr_token_hash | VARCHAR |
| status | VARCHAR |
| checked_in_at | TIMESTAMPTZ |
| created_at | TIMESTAMPTZ |

Unique:

- ticket_code.
- seat_id đối với mô hình một ghế chỉ được bán một lần.

## 6.17. Check-in Logs

| Column | Type |
|---|---|
| id | UUID |
| ticket_id | UUID |
| staff_id | UUID |
| event_id | UUID |
| checked_in_at | TIMESTAMPTZ |
| device_info | TEXT |

## 6.18. Waiting Room Entries

| Column | Type |
|---|---|
| id | UUID |
| event_id | UUID |
| user_id | UUID |
| status | VARCHAR |
| joined_at | TIMESTAMPTZ |
| admitted_at | TIMESTAMPTZ |
| token_expires_at | TIMESTAMPTZ |

Unique:

- (event_id, user_id) nếu mỗi user chỉ có một entry cho một sự kiện.

## 6.19. Outbox Events

| Column | Type |
|---|---|
| id | UUID |
| aggregate_type | VARCHAR |
| aggregate_id | UUID |
| event_type | VARCHAR |
| payload | JSONB |
| status | VARCHAR |
| created_at | TIMESTAMPTZ |
| published_at | TIMESTAMPTZ |

## 6.20. Audit Logs

| Column | Type |
|---|---|
| id | UUID |
| actor_id | UUID |
| action | VARCHAR |
| resource_type | VARCHAR |
| resource_id | UUID |
| metadata | JSONB |
| created_at | TIMESTAMPTZ |

---

# 7. CORE BOOKING FLOW

## 7.1. Luồng đặt vé tổng thể

```text
User
 |
 v
Browse Event
 |
 v
View Seat Map
 |
 v
Join Waiting Room (if enabled)
 |
 v
Receive Admission Token
 |
 v
Select Seats
 |
 v
Create Reservation
 |
 v
Create Order
 |
 v
Create Payment
 |
 v
Payment Provider
 |
 v
Webhook Verification
 |
 v
Confirm Payment
 |
 v
Issue Ticket
 |
 v
Send Email
```

## 7.2. Reservation Transaction

Đây là phần quan trọng nhất của hệ thống.

Khi user yêu cầu giữ ghế:

1. Validate request.
2. Kiểm tra user.
3. Kiểm tra event.
4. Kiểm tra thời gian mở bán.
5. Kiểm tra admission token.
6. Sắp xếp danh sách seat ID theo thứ tự cố định.
7. Bắt đầu transaction.
8. Lock các seat theo thứ tự ID.
9. Kiểm tra seat có tồn tại và thuộc event không.
10. Kiểm tra seat không bị BLOCKED hoặc SOLD.
11. Kiểm tra claim hiện tại.
12. Giải phóng claim hết hạn nếu có.
13. Tạo reservation.
14. Tạo reservation items.
15. Tạo seat claims.
16. Commit transaction.
17. Publish realtime event sau khi transaction commit.
18. Tạo delayed job giải phóng ghế.

Lưu ý: Việc sắp xếp thứ tự khóa giúp hạn chế deadlock khi nhiều request cùng chọn nhiều ghế.

## 7.3. PostgreSQL Pessimistic Locking

Ví dụ:

```sql
SELECT *
FROM seats
WHERE id = :seatId
FOR UPDATE;
```

Giải thích:

- `FOR UPDATE` khóa row được chọn.
- Transaction khác muốn khóa cùng row phải đợi.
- Khi transaction đầu tiên commit hoặc rollback, transaction tiếp theo mới tiếp tục.

Trong Spring Data JPA có thể sử dụng:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.id = :id")
Optional<Seat> findByIdForUpdate(UUID id);
```

Phương thức giữ ghế cần chạy trong transaction:

```java
@Transactional
public ReservationResponse reserveSeats(
        UUID userId,
        UUID eventId,
        List<UUID> seatIds
) {
    // Validate event and user
    // Lock seats in deterministic order
    // Validate claims and ticket availability
    // Create reservation
    // Create reservation items
    // Create seat claims

    return response;
}
```

Không gọi external payment provider bên trong transaction giữ ghế.

## 7.4. Unique Constraint Protection

Seat Claims có `seat_id` là PRIMARY KEY.

Do đó, database không thể chứa hai claim cùng một ghế.

Nếu hai request đồng thời cố insert cùng seat ID:

- Một request thành công.
- Request còn lại bị unique constraint violation.
- Backend chuyển lỗi thành HTTP 409 Conflict.

Đây là lớp bảo vệ cuối cùng ở database.

## 7.5. Optimistic Locking

Có thể bổ sung `version` cho các entity cần cập nhật cạnh tranh.

```java
@Version
private Long version;
```

Khi hai transaction cùng cập nhật một entity:

- Transaction đầu tiên thành công.
- Transaction thứ hai có thể gặp OptimisticLockException.

Với seat booking có tranh chấp cao, Pessimistic Locking và unique constraint thường dễ giải thích và kiểm soát hơn.

---

# 8. REDIS DESIGN

## 8.1. Vai trò của Redis

Redis được sử dụng cho:

- Cache event.
- Cache seat map.
- TTL reservation.
- Distributed coordination.
- Rate limiting.
- Waiting room.
- Admission token.
- Queue position.
- Realtime event distribution nếu cần.

PostgreSQL vẫn là nguồn dữ liệu chuẩn.

Không được để dữ liệu booking quan trọng chỉ tồn tại trong Redis.

## 8.2. Redis Key Naming

```text
event:{eventId}:seat-map

event:{eventId}:queue

event:{eventId}:admitted

reservation:{reservationId}:expiry

seat:{seatId}:lock

admission:{tokenId}

rate-limit:{userId}:{endpoint}
```

## 8.3. Redis TTL

Ví dụ:

```redis
SET reservation:res-001 ACTIVE EX 600
```

Key tự hết hạn sau 600 giây.

Tuy nhiên, Redis key hết hạn không đồng nghĩa với database tự động giải phóng seat claim.

Cần background job để cập nhật dữ liệu PostgreSQL.

## 8.4. Redis Distributed Lock

Ví dụ:

```redis
SET lock:seat:A01 random-token NX PX 10000
```

Trong đó:

- NX: chỉ tạo key khi chưa tồn tại.
- PX: TTL tính bằng milliseconds.
- random-token: token nhận diện chủ sở hữu lock.

Khi release lock, phải kiểm tra token trước khi xóa.

Không sử dụng lệnh DEL trực tiếp mà không xác minh ownership.

Redis lock chỉ là lớp coordination bổ sung. PostgreSQL transaction và constraints mới là cơ sở đảm bảo tính nhất quán dữ liệu.

---

# 9. RESERVATION EXPIRATION

## 9.1. Cơ chế hết hạn

Mỗi reservation có thời gian giữ ghế 10 phút.

Có thể sử dụng:

- RabbitMQ Delayed Message Plugin.
- BullMQ nếu dùng Redis và Node.js worker riêng.
- Spring Scheduler.
- Redis TTL kết hợp background sweeper.

Với stack Java Spring Boot, nên ưu tiên RabbitMQ và Spring Scheduler.

## 9.2. Expiration Flow

```text
Create Reservation
       |
       v
Set expires_at
       |
       v
Create Expiration Job
       |
       v
Wait 10 Minutes
       |
       v
Check Reservation Status
       |
       +---- CONFIRMED
       |        |
       |        v
       |      Ignore
       |
       +---- ACTIVE
                |
                v
         Check expires_at
                |
                v
        Release Seat Claims
                |
                v
        Update EXPIRED
                |
                v
        Publish Seat Available
```

## 9.3. Recovery Mechanism

Delayed job không nên là cơ chế duy nhất.

Cần thêm scheduled sweeper:

```sql
SELECT id
FROM reservations
WHERE status = 'ACTIVE'
  AND expires_at <= NOW()
ORDER BY expires_at
LIMIT 100;
```

Các reservation tìm được sẽ được xử lý theo batch trong transaction.

Khi giải phóng claim phải kiểm tra reservation ID để tránh xóa claim thuộc một reservation mới.

---

# 10. PAYMENT & IDEMPOTENCY

## 10.1. Payment Flow

```text
Frontend
   |
   v
Create Checkout Request
   |
   v
Backend Validate Order
   |
   v
Create Payment Attempt
   |
   v
Call Payment Provider
   |
   v
Receive Payment URL
   |
   v
User Pays
   |
   v
Provider Webhook
   |
   v
Verify Signature
   |
   v
Validate Amount & Reference
   |
   v
Idempotency Check
   |
   v
Update Payment & Order
   |
   v
Create Ticket
```

## 10.2. Idempotency Key

Khi frontend gửi yêu cầu tạo checkout:

```http
POST /api/v1/orders/{orderId}/checkout
Idempotency-Key: 4e0d...
```

Backend lưu key và kết quả xử lý.

Nếu cùng key được gửi lại:

- Không tạo payment attempt trùng.
- Trả kết quả phù hợp với request ban đầu.
- Nếu key đã tồn tại nhưng payload khác, trả lỗi conflict.

## 10.3. Webhook Idempotency

Provider có thể gửi một webhook nhiều lần.

Cách xử lý:

1. Nhận webhook.
2. Verify signature.
3. Lưu provider event ID.
4. Kiểm tra unique constraint.
5. Nếu đã xử lý thì trả response phù hợp.
6. Nếu chưa xử lý thì cập nhật trạng thái giao dịch trong transaction.
7. Phát hành vé theo cơ chế đảm bảo không trùng.
8. Đánh dấu event đã xử lý.

Không chỉ dựa vào một đoạn `if (payment.isSuccess()) return` vì hai webhook có thể chạy đồng thời.

## 10.4. Trường hợp Payment Success nhưng Reservation đã hết hạn

Đây là tình huống cần có chính sách nghiệp vụ rõ ràng.

Đề xuất:

- Nếu provider xác nhận tiền đã thu nhưng reservation không còn hợp lệ, không tự ý phát hành vé.
- Ghi nhận giao dịch đã nhận tiền.
- Chuyển order sang trạng thái cần xử lý.
- Khởi tạo quy trình refund hoặc xử lý ngoại lệ.
- Thông báo cho người dùng.
- Ghi audit log.

Không được giả định rằng timeout ở phía client đồng nghĩa payment thất bại.

---

# 11. MESSAGE QUEUE & OUTBOX PATTERN

## 11.1. Vì sao cần RabbitMQ?

Các tác vụ sau không cần thực hiện trực tiếp trong HTTP request:

- Gửi email.
- Tạo file PDF.
- Tạo notification.
- Gửi reminder.
- Xử lý analytics.
- Phát hành một số tài nguyên phụ của vé.

RabbitMQ giúp tách biệt luồng nghiệp vụ chính với các tác vụ chạy nền.

## 11.2. Queue Design

```text
ticketing.exchange
       |
       +---- notification.queue
       |
       +---- ticket.queue
       |
       +---- email.queue
       |
       +---- analytics.queue
```

Các queue cần có:

- Retry policy.
- Dead Letter Exchange.
- Dead Letter Queue.
- Message acknowledgment.
- Consumer error handling.

## 11.3. Outbox Pattern

Vấn đề:

Database commit thành công nhưng ứng dụng chết trước khi gửi message sang RabbitMQ.

Hệ thống sẽ lưu dữ liệu nghiệp vụ nhưng không gửi được notification.

Giải pháp Outbox:

Trong cùng transaction:

1. Cập nhật order.
2. Cập nhật payment.
3. Tạo ticket.
4. Ghi outbox event.
5. Commit.

Một background publisher đọc outbox và publish sang RabbitMQ.

Sau khi publish thành công, đánh dấu event đã được gửi.

Consumer vẫn phải idempotent vì message có thể được gửi lại.

---

# 12. REALTIME COMMUNICATION

## 12.1. Công nghệ

Sử dụng WebSocket với Spring WebSocket/STOMP.

## 12.2. Các sự kiện realtime

| Event | Ý nghĩa |
|---|---|
| SEAT_HELD | Ghế vừa được giữ |
| SEAT_RELEASED | Ghế vừa được giải phóng |
| SEAT_SOLD | Ghế đã bán |
| QUEUE_POSITION_UPDATED | Cập nhật vị trí queue |
| USER_ADMITTED | Người dùng được vào booking |
| RESERVATION_EXPIRING | Reservation sắp hết hạn |

## 12.3. Luồng cập nhật ghế

```text
User A Reserves Seat
        |
        v
Backend Transaction
        |
        v
Database Commit
        |
        v
Publish Seat Event
        |
        v
WebSocket Broker
        |
        v
Broadcast To Event Room
        |
        v
User B Updates Seat Map
```

Chỉ broadcast sự kiện sau khi transaction commit.

Nếu dùng nhiều backend instance, cần cơ chế chia sẻ hoặc phân phối sự kiện giữa các instance. Có thể sử dụng RabbitMQ làm broker cho Spring WebSocket hoặc một cơ chế pub/sub phù hợp.

Frontend cần reconnect và tải lại trạng thái ghế từ API khi mất kết nối.

---

# 13. VIRTUAL WAITING ROOM

## 13.1. Mục tiêu

Giới hạn số lượng người dùng được vào khu vực booking cùng lúc.

Ví dụ:

- 10.000 người tham gia.
- 300 người được admission.
- 9.700 người còn lại tiếp tục chờ.

## 13.2. Queue Management

Sử dụng Redis Sorted Set.

Ví dụ:

```redis
ZADD event:123:queue 1730000000 user-A
ZADD event:123:queue 1730000001 user-B
```

Score được sử dụng để sắp xếp thứ tự.

Trong thực tế, cần xử lý timestamp trùng và định danh queue entry ổn định.

## 13.3. Admission

Background worker:

1. Kiểm tra số lượng người đang được admission.
2. Lấy những user đứng đầu queue.
3. Cấp admission token.
4. Lưu trạng thái admission.
5. Cập nhật vị trí queue.
6. Broadcast realtime.

Admission token cần:

- Có thời hạn.
- Gắn với user hoặc session.
- Gắn với event.
- Có chữ ký hoặc cơ chế xác thực.
- Không thể dùng cho event khác.
- Không được sử dụng vượt quá chính sách admission.

## 13.4. Kiểm soát quyền booking

Mọi request giữ vé cần kiểm tra admission token ở backend.

Không được chỉ kiểm tra user đã từng vào trang waiting room hay chưa.

---

# 14. API SPECIFICATION

Base URL:

```text
/api/v1
```

## 14.1. Authentication API

| Method | Endpoint | Description |
|---|---|---|
| POST | /auth/register | Đăng ký |
| POST | /auth/login | Đăng nhập |
| POST | /auth/logout | Đăng xuất |
| POST | /auth/refresh | Refresh token |
| POST | /auth/forgot-password | Quên mật khẩu |
| POST | /auth/reset-password | Reset mật khẩu |
| GET | /users/me | Lấy profile |
| PATCH | /users/me | Cập nhật profile |

## 14.2. Event API

| Method | Endpoint | Description |
|---|---|---|
| GET | /events | Danh sách sự kiện |
| GET | /events/{id} | Chi tiết |
| POST | /organizer/events | Tạo sự kiện |
| PATCH | /organizer/events/{id} | Cập nhật |
| DELETE | /organizer/events/{id} | Xóa hoặc archive |
| POST | /organizer/events/{id}/publish | Công khai |
| POST | /organizer/events/{id}/cancel | Hủy sự kiện |
| GET | /organizer/events/{id}/statistics | Thống kê |

## 14.3. Venue API

| Method | Endpoint | Description |
|---|---|---|
| GET | /venues | Danh sách địa điểm |
| GET | /venues/{id} | Chi tiết |
| POST | /organizer/venues | Tạo địa điểm |
| POST | /organizer/venues/{id}/sections | Tạo khu vực |
| POST | /organizer/sections/{id}/seats | Tạo ghế |

## 14.4. Seat API

| Method | Endpoint | Description |
|---|---|---|
| GET | /events/{id}/seat-map | Sơ đồ ghế |
| GET | /events/{id}/availability | Inventory |
| PATCH | /organizer/seats/{id} | Cập nhật ghế |

## 14.5. Reservation API

| Method | Endpoint | Description |
|---|---|---|
| POST | /events/{id}/reservations | Giữ ghế |
| GET | /reservations/{id} | Chi tiết |
| DELETE | /reservations/{id} | Hủy |
| GET | /users/me/reservations | Lịch sử |

Request:

```json
{
  "seatIds": [
    "seat-001",
    "seat-002"
  ]
}
```

Response:

```json
{
  "reservationId": "uuid",
  "status": "ACTIVE",
  "expiresAt": "2026-11-25T14:10:00Z",
  "totalAmount": 4000000
}
```

## 14.6. Order API

| Method | Endpoint | Description |
|---|---|---|
| POST | /orders | Tạo order |
| GET | /orders/{id} | Chi tiết |
| GET | /users/me/orders | Lịch sử |
| POST | /orders/{id}/checkout | Checkout |
| POST | /orders/{id}/cancel | Hủy order |

## 14.7. Payment API

| Method | Endpoint | Description |
|---|---|---|
| GET | /payments/{id} | Trạng thái |
| POST | /webhooks/payos | PayOS webhook |
| POST | /webhooks/vnpay | VNPay webhook |

## 14.8. Ticket API

| Method | Endpoint | Description |
|---|---|---|
| GET | /users/me/tickets | Danh sách vé |
| GET | /tickets/{id} | Chi tiết vé |
| GET | /tickets/{id}/qr | QR Code |

## 14.9. Check-in API

| Method | Endpoint | Description |
|---|---|---|
| POST | /check-in/scan | Quét QR |
| GET | /organizer/events/{id}/check-ins | Lịch sử |
| GET | /organizer/events/{id}/check-in-statistics | Thống kê |

## 14.10. Waiting Room API

| Method | Endpoint | Description |
|---|---|---|
| POST | /events/{id}/waiting-room/join | Tham gia |
| GET | /events/{id}/waiting-room/status | Trạng thái |
| POST | /events/{id}/waiting-room/leave | Rời queue |

## 14.11. HTTP Status Code

| Status | Ý nghĩa |
|---|---|
| 200 | Success |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict |
| 422 | Business Rule Violation |
| 429 | Too Many Requests |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

---

# 15. FRONTEND UI REQUIREMENTS

## 15.1. Public Pages

**Home Page**

- Danh sách sự kiện nổi bật.
- Danh sách sự kiện sắp diễn ra.
- Search.
- Filter theo danh mục.
- Filter theo thời gian.

**Event Detail**

- Banner.
- Tên sự kiện.
- Địa điểm.
- Thời gian.
- Mô tả.
- Loại vé.
- Giá vé.
- Nút đặt vé.

## 15.2. Booking Pages

**Seat Selection**

- Hiển thị sơ đồ ghế.
- Phân biệt ghế trống, đang giữ, đã bán.
- Chọn nhiều ghế.
- Hiển thị tổng tiền.
- Countdown.
- Xử lý conflict khi ghế bị người khác giữ.

**Waiting Room**

- Vị trí hàng đợi.
- Số người đang chờ.
- Thời gian chờ ước tính.
- Trạng thái admission.
- Countdown nếu token sắp hết hạn.

**Checkout**

- Thông tin reservation.
- Danh sách ghế.
- Tổng tiền.
- Chọn payment provider.
- Trạng thái thanh toán.

## 15.3. Customer Pages

- My Orders.
- My Tickets.
- Ticket Detail.
- Profile.
- Notification Center.

## 15.4. Organizer Pages

- Dashboard.
- Event Management.
- Venue Management.
- Seat Management.
- Ticket Management.
- Sales Analytics.
- Check-in Management.

## 15.5. Staff Pages

- Danh sách sự kiện được phân công.
- QR Scanner.
- Kết quả xác thực.
- Lịch sử check-in.

## 15.6. Frontend Business Rules

- Không tự quyết định ghế còn trống.
- Không tự tính giá cuối cùng để gửi thanh toán.
- Countdown dựa trên expiresAt từ backend.
- WebSocket chỉ giúp cập nhật giao diện, không thay thế API kiểm tra trạng thái.
- Nếu mất kết nối phải reconnect và refetch dữ liệu.
- Xử lý rõ loading, empty, error, expired và conflict.

---

# 16. SECURITY

## 16.1. Authentication

Sử dụng Spring Security.

Có thể chọn JWT hoặc session cookie.

Với hệ thống Next.js và Spring Boot:

- Ưu tiên HTTP-only Secure Cookie nếu triển khai cookie-based authentication.
- Có CSRF protection phù hợp.
- Nếu dùng JWT, phải có chiến lược refresh và revoke.
- Không lưu refresh token trong localStorage.

## 16.2. Authorization

Sử dụng RBAC kết hợp ownership check.

Ví dụ:

```java
if (!event.getOrganizerId().equals(currentUserId)) {
    throw new AccessDeniedException("Forbidden");
}
```

## 16.3. Security Checklist

| Risk | Solution |
|---|---|
| SQL Injection | Parameterized Query |
| XSS | Output escaping, CSP |
| CSRF | CSRF protection |
| Brute Force | Rate limiting |
| IDOR | Ownership validation |
| Fake Webhook | Signature verification |
| Fake QR | HMAC/JWT |
| Duplicate Payment | Idempotency |
| Reservation Spam | Rate limiting |
| Unauthorized Check-in | RBAC |
| Sensitive Data Exposure | Data minimization |

## 16.4. Rate Limiting

Cấu hình ban đầu để thử nghiệm:

| Endpoint | Limit |
|---|---|
| Login | 5 requests/minute/IP |
| Join Waiting Room | 3 requests/minute/user/event |
| Reservation | 5 requests/minute/user |
| Checkout | 5 requests/minute/user |
| QR Scan | 30 requests/minute/staff |

Có thể sử dụng Bucket4j kết hợp Redis.

Các giới hạn cần điều chỉnh sau khi có số liệu thực tế.

---

# 17. TESTING STRATEGY

## 17.1. Unit Testing

Công nghệ:

- JUnit 5.
- Mockito.

Test:

- Event business rules.
- Reservation validation.
- Order calculation.
- Payment state transitions.
- Ticket validation.
- Authorization rules.

## 17.2. Integration Testing

Công nghệ:

- Spring Boot Test.
- Testcontainers.
- PostgreSQL.
- Redis.
- RabbitMQ.

Test các luồng thực tế với database và infrastructure thật.

## 17.3. Concurrency Testing

Các kịch bản bắt buộc:

**Test 1: Double Booking**

- 100 request cùng giữ một ghế.
- Chỉ một request thành công.
- Các request còn lại nhận Conflict.
- Database có tối đa một seat claim.

**Test 2: Multiple Seats**

- Nhiều request giữ nhiều ghế.
- Kiểm tra deadlock.
- Kiểm tra transaction rollback.
- Kiểm tra dữ liệu reservation items.

**Test 3: Concurrent Webhooks**

- Gửi cùng webhook nhiều lần.
- Các request chạy đồng thời.
- Chỉ một giao dịch được xác nhận.
- Không phát hành duplicate ticket.

**Test 4: Reservation Expiration**

- User A giữ ghế.
- Không thanh toán.
- Reservation hết hạn.
- Claim được giải phóng.
- User B có thể giữ ghế.

**Test 5: Concurrent Check-in**

- Hai nhân viên cùng scan một QR.
- Chỉ một request thành công.
- Request còn lại nhận thông báo vé đã được sử dụng.

## 17.4. End-to-End Testing

Sử dụng Playwright.

Các flow:

1. Register.
2. Login.
3. Browse Event.
4. Select Seat.
5. Create Reservation.
6. Checkout.
7. Simulate Payment.
8. View Ticket.
9. Scan QR.

## 17.5. Load Testing

Sử dụng k6.

Các kịch bản:

**Scenario A: Event Browsing**

- Nhiều user cùng xem danh sách sự kiện.
- Đánh giá API đọc.

**Scenario B: Seat Map**

- Nhiều user cùng tải seat map.
- Đánh giá cache và database.

**Scenario C: Reservation Contention**

- Nhiều user tranh giành cùng một số ghế.
- Đánh giá concurrency.

**Scenario D: Flash Sale**

- 2.000 virtual users.
- 50 vé.
- Kiểm tra số vé bán thành công.

**Scenario E: Webhook Burst**

- Nhiều webhook gửi đồng thời.
- Kiểm tra idempotency và queue.

## 17.6. Metrics

| Metric | Ý nghĩa |
|---|---|
| VUs | Virtual Users |
| RPS | Requests per Second |
| p50 | Median Latency |
| p95 | 95th Percentile |
| p99 | 99th Percentile |
| Error Rate | Tỷ lệ lỗi |
| Throughput | Lượng dữ liệu xử lý |
| DB Connections | Số kết nối DB |
| CPU Usage | Mức sử dụng CPU |
| Memory Usage | Mức sử dụng RAM |

## 17.7. Correctness Verification

Sau mỗi lần load test phải kiểm tra:

- Tổng số vé đã bán không vượt inventory.
- Một ghế không có nhiều ticket hợp lệ.
- Không có duplicate payment processing.
- Không có duplicate ticket issuance.
- Không có reservation hết hạn nhưng vẫn giữ claim lâu dài.
- Tổng số reservation và order có quan hệ hợp lệ.

Không được chỉ dựa vào HTTP response để kết luận hệ thống hoạt động đúng.

---

# 18. DEVOPS & DEPLOYMENT

## 18.1. Docker Compose

Các service:

| Service | Port |
|---|---|
| Next.js | 3000 |
| Spring Boot | 8080 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| RabbitMQ | 5672 |
| RabbitMQ Management | 15672 |

Cấu trúc:

```text
docker-compose.yml
├── frontend
├── backend
├── postgres
├── redis
└── rabbitmq
```

## 18.2. Environment Variables

Backend:

```env
SPRING_PROFILES_ACTIVE=local

DB_URL=jdbc:postgresql://localhost:5432/ticketing
DB_USERNAME=postgres
DB_PASSWORD=

REDIS_HOST=localhost
REDIS_PORT=6379

RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672

JWT_SECRET=

PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=

MAIL_HOST=
MAIL_USERNAME=
MAIL_PASSWORD=
```

Frontend:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

Không commit secret vào GitHub.

## 18.3. CI/CD

Sử dụng GitHub Actions.

Pipeline:

```text
Developer Push
      |
      v
GitHub Actions
      |
      v
Backend Tests
      |
      v
Frontend Lint & Type Check
      |
      v
Integration Tests
      |
      v
Build Docker Images
      |
      v
Deploy Staging
      |
      v
Smoke Tests
```

## 18.4. Monitoring

Công cụ:

- Spring Boot Actuator.
- Micrometer.
- Prometheus.
- Grafana.

Theo dõi:

- API latency.
- HTTP error rate.
- DB connection pool.
- Redis memory.
- RabbitMQ queue depth.
- Failed jobs.
- Reservation expiration backlog.
- Webhook processing failures.

---

# 19. SYSTEM DESIGN TRADE-OFFS

Đây là phần rất quan trọng khi trình bày dự án với Tech Lead.

## 19.1. Modular Monolith vs Microservices

**Chọn Modular Monolith.**

Lý do:

- Dễ triển khai.
- Dễ quản lý transaction.
- Giảm độ phức tạp distributed system.
- Phù hợp quy mô dự án.
- Có thể tách service khi có nhu cầu thực tế.

Trade-off:

- Các module dùng chung deployment.
- Không scale riêng từng module ở mức process độc lập.

## 19.2. PostgreSQL Locking vs Redis Lock

**Chọn PostgreSQL làm lớp bảo vệ correctness.**

Lý do:

- Transactional.
- Row-level locking.
- Unique constraints.
- Dữ liệu bền vững.

Redis lock có thể bổ sung để coordination hoặc giảm contention nhưng không thay thế database constraints.

## 19.3. Delayed Queue vs Cronjob

Delayed Queue:

- Xử lý gần thời điểm hết hạn.
- Không cần quét toàn bộ bảng liên tục.

Cronjob/Sweeper:

- Dễ triển khai.
- Có khả năng phục hồi job bị mất hoặc chạy lỗi.

Giải pháp:

- Dùng delayed job để xử lý chính.
- Dùng sweeper để recovery.

## 19.4. WebSocket vs SSE

WebSocket:

- Giao tiếp hai chiều.
- Phù hợp realtime nhiều loại sự kiện.
- Hỗ trợ các ứng dụng cần tương tác liên tục.

SSE:

- Server push một chiều.
- Đơn giản hơn.
- Phù hợp notification hoặc queue position.

Dự án chọn WebSocket vì có nhiều sự kiện realtime.

## 19.5. RabbitMQ vs Redis Pub/Sub

RabbitMQ:

- Message acknowledgment.
- Retry và DLQ.
- Message routing.
- Phù hợp background jobs cần độ tin cậy.

Redis Pub/Sub:

- Đơn giản.
- Độ trễ thấp.
- Không phải message queue bền vững.

Dự án sử dụng RabbitMQ cho background processing.

---

# 20. ROADMAP IMPLEMENTATION

Thời gian dự kiến: 8 tuần.

## Week 1: Requirement & Database Design

Tasks:

- Phân tích nghiệp vụ.
- Xác định Actors.
- Xây dựng Use Case.
- Xây dựng State Machine.
- Thiết kế ERD.
- Setup Spring Boot.
- Setup Next.js.
- Setup Docker Compose.
- Setup PostgreSQL và Redis.

Deliverables:

- Requirement Specification.
- ERD.
- Project Skeleton.
- Docker Environment.

## Week 2: Authentication & Event Management

Tasks:

- Register/Login.
- Spring Security.
- JWT hoặc session.
- RBAC.
- CRUD Event.
- Venue Management.
- Section Management.
- Seat Management.

Deliverables:

- Authentication API.
- Event Management API.
- Organizer Dashboard cơ bản.

## Week 3: Seat Map & Inventory

Tasks:

- Seat Map API.
- Frontend Seat Map.
- Seat Selection.
- Seat Status.
- Inventory Validation.
- API Integration.

Deliverables:

- Seat Selection hoàn chỉnh.

## Week 4: Reservation & Concurrency

Tasks:

- Reservation API.
- PostgreSQL transaction.
- Pessimistic Locking.
- Seat Claims.
- Redis TTL.
- Reservation Expiration.
- Concurrency Tests.

Deliverables:

- Double Booking Protection.
- Reservation Expiration.

## Week 5: Order & Payment

Tasks:

- Order Management.
- Checkout API.
- Payment Integration.
- Webhook Verification.
- Idempotency.
- Payment State Management.

Deliverables:

- Payment Flow hoạt động với Sandbox.

## Week 6: Ticket & Asynchronous Processing

Tasks:

- Ticket Issuance.
- QR Generation.
- RabbitMQ.
- Email Worker.
- Notification.
- Outbox Pattern.
- WebSocket.

Deliverables:

- Digital Ticket.
- Email Notification.
- Realtime Seat Updates.

## Week 7: Waiting Room & Load Testing

Tasks:

- Redis Sorted Set.
- Queue Management.
- Admission Token.
- Staff Check-in.
- Concurrent Check-in.
- k6 Load Test.
- Performance Optimization.

Deliverables:

- Virtual Waiting Room.
- Check-in System.
- Load Test Report.

## Week 8: Deployment & Documentation

Tasks:

- Docker Optimization.
- GitHub Actions.
- Staging Deployment.
- Monitoring.
- Logging.
- Bug Fixing.
- README.
- Architecture Diagram.
- ERD Documentation.
- Trade-off Documentation.

Deliverables:

- Deployed Application.
- Complete GitHub Repository.
- Technical Documentation.

---

# 21. PRIORITY LEVEL

Không nên triển khai tất cả chức năng cùng một lúc.

## P0 - Core Requirements

Bắt buộc phải hoàn thành:

- Authentication.
- RBAC.
- Event CRUD.
- Venue Management.
- Seat Map.
- Reservation.
- PostgreSQL Transaction.
- Pessimistic Locking.
- Unique Constraint.
- Order Management.
- Payment Sandbox.
- Webhook Idempotency.
- Ticket Issuance.

## P1 - Important Features

Nên hoàn thành:

- Redis TTL.
- Reservation Expiration.
- WebSocket.
- RabbitMQ.
- Email Worker.
- QR Check-in.
- Integration Test.
- Concurrency Test.

## P2 - Advanced Features

Điểm cộng:

- Virtual Waiting Room.
- Admission Token.
- Outbox Pattern.
- Retry & DLQ.
- Prometheus.
- Grafana.
- Advanced Load Testing.
- General Admission.
- CI/CD.

---

# 22. INTERVIEW PREPARATION

## 22.1. Java

- OOP.
- Collections.
- HashMap.
- Stream API.
- Exception Handling.
- Multithreading.
- ExecutorService.
- Synchronization.
- CompletableFuture.

## 22.2. Spring Boot

- IoC.
- Dependency Injection.
- Bean Lifecycle.
- Auto Configuration.
- Spring MVC.
- Spring Data JPA.
- Spring Security.
- Transaction Management.
- Validation.
- Exception Handling.

## 22.3. Database

- Primary Key.
- Foreign Key.
- Unique Constraint.
- Index.
- Composite Index.
- JOIN.
- GROUP BY.
- ACID.
- Isolation Level.
- Normalization.
- N+1 Query.
- Transaction.
- Deadlock.

## 22.4. Concurrency

- Race Condition.
- Critical Section.
- Pessimistic Locking.
- Optimistic Locking.
- Distributed Lock.
- Atomic Operation.
- Database Isolation.
- Idempotency.

## 22.5. Redis

- Cache.
- TTL.
- SET NX.
- Sorted Set.
- Pub/Sub.
- Distributed Lock.
- Rate Limiting.

## 22.6. Messaging

- Producer.
- Consumer.
- Exchange.
- Queue.
- Routing Key.
- Acknowledgment.
- Retry.
- Dead Letter Queue.
- Outbox Pattern.
- Eventual Consistency.

## 22.7. System Design

- Horizontal Scaling.
- Vertical Scaling.
- Load Balancing.
- Caching.
- Queue.
- Bottleneck.
- Rate Limiting.
- High Availability.
- Consistency.
- Scalability.

## 22.8. Những câu hỏi phỏng vấn quan trọng

1. Race Condition là gì?
2. Tại sao hai user có thể cùng đặt một ghế?
3. Tại sao không thể chỉ dùng synchronized trong Java?
4. Pessimistic Locking khác Optimistic Locking thế nào?
5. SELECT FOR UPDATE hoạt động như thế nào?
6. Redis SET NX giải quyết vấn đề gì?
7. Tại sao Redis lock không đủ để đảm bảo không oversell?
8. ACID là gì?
9. Isolation Level ảnh hưởng thế nào đến booking?
10. Deadlock xảy ra khi nào?
11. Làm sao hạn chế deadlock?
12. Idempotency là gì?
13. Tại sao webhook cần idempotency?
14. RabbitMQ khác Redis Pub/Sub thế nào?
15. Outbox Pattern giải quyết vấn đề gì?
16. WebSocket khác SSE thế nào?
17. Tại sao cần Virtual Waiting Room?
18. Làm sao xử lý payment success nhưng reservation đã hết hạn?
19. Làm sao đảm bảo một QR chỉ check-in một lần?
20. Khi tăng từ 100 lên 10.000 concurrent users, bottleneck có thể nằm ở đâu?
21. Làm sao chứng minh hệ thống không bán vượt inventory?
22. Nếu RabbitMQ ngừng hoạt động thì hệ thống cần xử lý thế nào?
23. Nếu Redis bị mất dữ liệu thì booking có bị sai không?
24. Nếu backend chết sau khi database commit nhưng trước khi publish message thì sao?
25. Nếu hai webhook cùng cập nhật một payment thì xử lý thế nào?

---

# 23. PORTFOLIO & CV DELIVERABLES

Không chỉ cung cấp GitHub Repository, cần có tài liệu chứng minh năng lực kỹ thuật.

## 23.1. README.md

README cần có:

- Project Overview.
- Business Requirements.
- Tech Stack.
- Architecture Diagram.
- ERD.
- Installation Guide.
- Environment Configuration.
- API Documentation.
- Demo Screenshots.
- Load Testing Report.
- Technical Challenges.
- Trade-offs.

## 23.2. Architecture Diagram

Thể hiện:

- Next.js.
- Spring Boot.
- PostgreSQL.
- Redis.
- RabbitMQ.
- Payment Provider.
- Background Worker.
- WebSocket.

## 23.3. ERD

Giải thích:

- Các bảng.
- Quan hệ.
- Primary Key.
- Foreign Key.
- Unique Constraint.
- Index.
- Những quyết định thiết kế quan trọng.

## 23.4. Load Testing Report

Ví dụ cấu trúc báo cáo:

**Scenario: Flash Sale**

- Virtual Users: 2.000.
- Inventory: 50 tickets.
- Duration: 60 seconds.
- API: Reservation.

Metrics cần ghi nhận:

- Total Requests.
- Successful Reservations.
- Conflict Responses.
- Error Rate.
- p50.
- p95.
- p99.
- RPS.
- Database CPU.
- Database Connections.

Các số liệu phải lấy từ kết quả chạy thực tế. Không sử dụng số liệu giả định làm thành tích dự án.

## 23.5. Technical Challenges

Nên viết các vấn đề đã giải quyết:

**Challenge 1: Double Booking**

Problem:

Hai request đồng thời có thể cùng đọc trạng thái AVAILABLE.

Solution:

Sử dụng PostgreSQL transaction, Pessimistic Locking và unique constraint trên seat claims.

**Challenge 2: Duplicate Webhook**

Problem:

Payment Provider có thể gửi webhook nhiều lần.

Solution:

Sử dụng provider event ID, unique constraint và idempotent transaction.

**Challenge 3: Reservation Expiration**

Problem:

User không thanh toán nhưng ghế vẫn bị giữ.

Solution:

Delayed Job kết hợp Scheduled Sweeper.

**Challenge 4: Database Bottleneck**

Problem:

Nhiều request cùng truy cập inventory.

Solution:

Tối ưu index, transaction duration, connection pool và load testing.

---

# 24. ACCEPTANCE CRITERIA

Dự án được xem là hoàn thành MVP khi đáp ứng các điều kiện:

### Authentication

- Đăng ký và đăng nhập hoạt động.
- Phân quyền đúng.
- Không truy cập trái phép tài nguyên.

### Event Management

- Organizer tạo và quản lý sự kiện.
- Customer xem được sự kiện công khai.
- Event chỉ mở bán trong thời gian hợp lệ.

### Booking

- Customer xem seat map.
- Customer giữ được ghế trống.
- Không thể giữ ghế đã được người khác giữ.
- Reservation hết hạn được giải phóng.
- Không xảy ra duplicate seat claim.

### Payment

- Tạo được payment request.
- Xác thực được webhook.
- Không xử lý payment thành công nhiều lần.
- Không phát hành duplicate ticket.

### Ticket

- Vé có QR Code.
- Người dùng xem được vé.
- Staff xác thực được vé.
- Một vé không thể check-in nhiều lần.

### Infrastructure

- Docker Compose chạy được toàn bộ môi trường.
- Có health check.
- Có integration test.
- Có concurrency test.
- Có load test report.
- Có README đầy đủ.

### Reliability

- Job hết hạn có cơ chế recovery.
- Message có retry và DLQ.
- Không mất business event khi database commit nhưng message chưa publish.
- Các thao tác quan trọng có audit log.

---

# 25. KẾT LUẬN

Event Ticketing & Flash Sale Platform là dự án portfolio tập trung vào những vấn đề backend có giá trị thực tế:

1. Database Design.
2. Transaction Management.
3. Concurrency Control.
4. Redis.
5. Asynchronous Processing.
6. Payment Integration.
7. Idempotency.
8. Real-time Communication.
9. System Testing.
10. Deployment & Monitoring.

Đối với mục tiêu Fresher/Junior Backend Developer, nên ưu tiên chất lượng phần lõi thay vì cố gắng xây dựng một kiến trúc quá phức tạp.

**Thứ tự ưu tiên triển khai:**

Database Design → Business Rules → Reservation Transaction → Concurrency Testing → Order & Payment → Redis → RabbitMQ → WebSocket → Frontend → Load Testing → Deployment.

Nguyên tắc quan trọng nhất:

**PostgreSQL đảm bảo tính đúng đắn của dữ liệu. Redis hỗ trợ hiệu năng và coordination. RabbitMQ xử lý công việc bất đồng bộ. Spring Boot quản lý nghiệp vụ. Next.js cung cấp trải nghiệm người dùng.**

Mục tiêu cuối cùng không phải chỉ là xây dựng một website đặt vé có giao diện đẹp, mà là chứng minh rằng hệ thống có thể xử lý các tình huống đồng thời, bảo vệ dữ liệu và hoạt động ổn định trong điều kiện tải cao.