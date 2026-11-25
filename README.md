# Event Ticketing & Flash Sale Platform (FlashTix)

> **High-Concurrency Ticket Booking & Virtual Waiting Room System** — Designed and built to solve real-world flash-sale concurrency challenges: double-booking prevention, deadlock-free multi-seat locking, idempotent payments & webhooks, transactional outbox messaging, real-time seat updates, and virtual waiting room traffic shaping.

[![CI/CD Pipeline](https://github.com/hoducvinhh/ticket-booking-service/actions/workflows/ci.yml/badge.svg)](https://github.com/hoducvinhh/ticket-booking-service/actions/workflows/ci.yml)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Business Requirements & Actors](#2-business-requirements--actors)
3. [Tech Stack](#3-tech-stack)
4. [System Architecture](#4-system-architecture)
5. [Database Design & ERD](#5-database-design--erd)
6. [Quick Start with Docker](#6-quick-start-with-docker)
7. [Environment Configuration](#7-environment-configuration)
8. [API Documentation](#8-api-documentation)
9. [Security & Rate Limiting](#9-security--rate-limiting)
10. [Testing & Load Testing](#10-testing--load-testing)
11. [Technical Challenges & Trade-offs](#11-technical-challenges--trade-offs)
12. [Monitoring & Observability](#12-monitoring--observability)

---

## 1. Project Overview

During high-demand ticket drops (e.g., 10,000 concurrent users competing for 100 VIP seats), naive CRUD booking systems suffer from overselling, deadlocks, duplicate payment charges, and database connection exhaustion.

**FlashTix** guarantees strict correctness and resilience under load:
- **Zero Double Booking**: Deterministic `seatIds` ordering + PostgreSQL `SELECT ... FOR UPDATE` + `seat_claims` `PRIMARY KEY (seat_id)`.
- **Zero Overselling (Reserved Seating & General Admission)**: Atomic conditional updates (`WHERE sold_quantity + :qty <= total_quantity`) and partial unique indexes (`uq_tickets_active_seat`).
- **Idempotent Checkout & Webhooks**: `Idempotency-Key` payload hashing (`409` on mismatch) and atomic `INSERT ... ON CONFLICT DO NOTHING` on `webhook_events`.
- **Automatic Expiration & Recovery**: 10-minute reservation TTL via RabbitMQ native TTL + Dead Letter Exchange (`reservation.delay.queue` → `reservation.expire.queue`), backed by a `@Scheduled` PostgreSQL recovery sweeper.
- **Virtual Waiting Room**: Redis Sorted Set FIFO queue (`epochMillis * 10000 + seq`) issuing HMAC-signed Admission Tokens enforced at the backend reservation boundary.
- **Transactional Outbox & Reliable Async**: `outbox_events` (`FOR UPDATE SKIP LOCKED`) feeding RabbitMQ queues (`notification`, `email`, `ticket`, `analytics`) with retries and Dead Letter Queues (`*.dlq`).
- **Real-time Seat Map**: Spring WebSocket / STOMP broadcasting `SEAT_HELD`, `SEAT_RELEASED`, `SEAT_SOLD`, `QUEUE_POSITION_UPDATED`, `USER_ADMITTED`, and `RESERVATION_EXPIRING` after DB transaction commit.

### Repository Layout
```text
├── ticket-booking-service/   # Java 21 + Spring Boot 4 Modular Monolith Backend (14 modules)
├── ticket-booking-fe/        # Next.js 16 + TypeScript + Tailwind Frontend
├── load-tests/               # k6 Load Test Scripts (Scenarios A–E) + Correctness SQL
├── monitoring/               # Prometheus scrape config & Grafana dashboard provisioning
├── docs/                     # Detailed Architecture, ERD, Trade-offs & Load Test docs
├── .github/workflows/ci.yml  # GitHub Actions CI/CD pipeline
├── docker-compose.yml        # Full-stack containerized orchestration
└── PROJECT_SPECIFICATION.md  # Full system specification
```

---

## 2. Business Requirements & Actors

### Actors & RBAC + Resource Ownership (Anti-IDOR)
| Actor | Capabilities |
|---|---|
| **Guest** | Browse public events, search & filter, view seat map & availability |
| **Customer** | Join waiting room, hold seats (10m TTL), create orders, checkout (PayOS/VNPay/Sandbox), cancel valid holds/orders, view QR e-tickets & notifications |
| **Organizer** | Manage venues, events (draft/publish/hide/cancel/banner), sections, seats (block/unblock), GA ticket tiers, check-in staff assignment, sales & check-in analytics |
| **Check-in Staff** | View assigned events, scan QR codes (camera or token), atomic single-use ticket validation, check-in audit history |
| **Admin** | Platform overview metrics (9 KPIs), user lock/unlock, role assignment, order refund execution (`REFUND_PENDING` → `REFUNDED`), webhook & audit log inspection |

### Core Booking & Payment Flow
```text
Browse Event → View Seat Map → Join Waiting Room (if enabled) → Receive Admission Token
  → Hold Seats (10m TTL) → Create Order → Checkout (Idempotency-Key)
  → Payment Gateway / Sandbox → Signed Webhook (Idempotent) → Issue HMAC-Signed QR Tickets
  → Async Email & In-App Notification (Outbox + RabbitMQ) → Staff Atomic QR Check-in
```

---

## 3. Tech Stack

| Layer | Technologies |
|---|---|
| **Backend** | Java 21, Spring Boot 4 (`Modular Monolith`), Spring WebMVC, Spring Data JPA, Spring Security, Spring WebSocket (STOMP), Spring AMQP, Spring Mail |
| **Database & Migration** | PostgreSQL 16, Flyway (`V1`–`V3`) |
| **Cache & Coordination** | Redis 7 (`StringRedisTemplate`, Lua unlock, Sorted Set queue, Bucket4j rate limiting) |
| **Message Broker** | RabbitMQ 3.13 (`TopicExchange`, `DirectExchange` DLX, DLQs, native TTL delay queue) |
| **Security & QR** | BCrypt, HTTP-only Secure Cookie + JWT (`jjwt`), HMAC-SHA256 QR & Admission Tokens, ZXing |
| **Frontend** | Next.js 16 (App Router), TypeScript, Tailwind CSS v4, TanStack Query, Zustand, React Hook Form + Zod, `@stomp/stompjs`, Axios, `qrcode.react`, `html5-qrcode` |
| **Testing** | JUnit 5, Mockito, Testcontainers (PostgreSQL, Redis, RabbitMQ), Playwright, k6 |
| **DevOps & Monitoring** | Docker, Docker Compose, GitHub Actions, Spring Boot Actuator, Micrometer, Prometheus, Grafana |

---

## 4. System Architecture

See full details in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

```text
                    CLIENT (Browser)
                          |
                  HTTP / WebSocket (STOMP)
                          |
                          v
             NEXT.JS FRONTEND (:3000)
                          |
                    REST /api/v1 & /ws
                          |
                          v
        SPRING BOOT MODULAR MONOLITH (:8080)
          ├── common (Security, RateLimit, Outbox, Audit, WebSocket)
          └── 14 modules: auth, user, event, venue, ticket, inventory,
              reservation, waitingroom, order, payment, notification,
              checkin, reporting, admin
                          |
         +----------------+----------------+
         |                |                |
         v                v                v
   PostgreSQL           Redis          RabbitMQ
    (:5432)            (:6379)      (:5672 / :15672)
  Source of Truth     Cache / TTL    Outbox Consumers
  Locks & Claims     Waiting Room   Delayed Expiration
  Orders / Tickets    Rate Limit      DLX / DLQ
```

---

## 5. Database Design & ERD

See full diagram & constraints in [docs/ERD.md](docs/ERD.md) and migrations under [ticket-booking-service/src/main/resources/db/migration](ticket-booking-service/src/main/resources/db/migration).

Key tables (25 tables across Flyway `V1`–`V3`):
- **Identity**: `users`, `roles`, `user_roles`, `refresh_tokens`, `password_reset_tokens`
- **Event & Venue**: `venues`, `events`, `ticket_sections`, `ticket_types`, `seats`, `event_staff`
- **Booking & Payment**: `reservations`, `reservation_items`, `seat_claims` (`PK = seat_id`), `orders` (`UNIQUE reservation_id`), `order_items`, `payments`, `webhook_events` (`UNIQUE(provider, provider_event_id)`), `idempotency_records`
- **Tickets & System**: `tickets` (`uq_tickets_active_seat`), `checkin_logs`, `waiting_room_entries`, `outbox_events`, `notifications`, `audit_logs`

---

## 6. Quick Start with Docker

### Prerequisites
- Docker & Docker Compose v2+

### Run Full Stack
```bash
# 1. Create local environment file
cp .env.example .env

# 2. Build and start PostgreSQL, Redis, RabbitMQ, Backend, and Frontend
docker compose up -d --build

# 3. (Optional) Include Prometheus & Grafana monitoring
docker compose --profile monitoring up -d --build
```

### Service Endpoints
| Service | URL |
|---|---|
| **Frontend App** | http://localhost:3000 |
| **Backend REST API** | http://localhost:8080/api/v1 |
| **WebSocket (STOMP)** | ws://localhost:8080/ws |
| **Actuator Health** | http://localhost:8080/actuator/health |
| **Prometheus Metrics** | http://localhost:8080/actuator/prometheus |
| **RabbitMQ Management** | http://localhost:15672 (`guest` / `guest`) |
| **Prometheus UI** (`monitoring` profile) | http://localhost:9090 |
| **Grafana UI** (`monitoring` profile) | http://localhost:3001 (`admin` / `admin`) |

---

## 7. Environment Configuration

All variables are documented in [.env.example](.env.example). Never commit `.env` or real secrets to Git.

| Variable | Description | Default (Docker) |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://postgres:5432/ticketing` |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials | `postgres` / `postgres` |
| `REDIS_HOST` / `REDIS_PORT` | Redis host & port | `redis` / `6379` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ host & AMQP port | `rabbitmq` / `5672` |
| `JWT_SECRET` | HMAC key for JWT access tokens (>= 32 chars) | Dev secret |
| `QR_HMAC_SECRET` | HMAC key for ticket QR signing | Dev secret |
| `ADMISSION_TOKEN_SECRET` | HMAC key for waiting room admission tokens | Dev secret |
| `PAYOS_*` / `VNPAY_*` | Sandbox gateway credentials (blank = Local Simulator) | Blank |
| `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials (blank = simulated mail logs) | Blank |
| `NEXT_PUBLIC_API_URL` / `NEXT_PUBLIC_WS_URL` | Browser API & WS endpoints | `http://localhost:8080/api/v1` |

---

## 8. API Documentation

Base URL: `/api/v1`

- **Auth & Profile**: `POST /auth/{register,login,logout,refresh,forgot-password,reset-password}`, `GET|PATCH /users/me`, `PATCH /users/me/password`
- **Events & Venues**: `GET /events`, `GET /events/{id}`, `GET /events/{id}/seat-map`, `GET /events/{id}/availability`, `GET /events/{id}/ticket-types`, `GET /venues`, `GET /venues/{id}`
- **Organizer**: `GET|POST /organizer/events`, `PATCH|DELETE /organizer/events/{id}`, `POST /organizer/events/{id}/{publish,hide,cancel,banner,staff,ticket-types}`, `GET /organizer/events/{id}/{statistics,check-ins,check-in-statistics}`, `POST /organizer/venues`, `POST /organizer/venues/{id}/sections`, `POST /organizer/sections/{id}/seats`, `PATCH /organizer/seats/{id}`
- **Waiting Room**: `POST /events/{id}/waiting-room/{join,leave}`, `GET /events/{id}/waiting-room/status`
- **Reservations & Orders**: `POST /events/{id}/reservations`, `GET|DELETE /reservations/{id}`, `GET /users/me/reservations`, `POST /orders`, `GET /orders/{id}`, `GET /users/me/orders`, `POST /orders/{id}/checkout` (`Idempotency-Key`), `POST /orders/{id}/cancel`
- **Payments & Webhooks**: `GET /payments/{id}`, `POST /webhooks/{payos,vnpay}`, `POST /sandbox-payments/{paymentId}/complete`
- **Tickets, Check-in & Notifications**: `GET /users/me/tickets`, `GET /tickets/{id}`, `GET /tickets/{id}/qr`, `POST /check-in/scan`, `GET /check-in/my-events`, `GET /users/me/notifications`, `PATCH /notifications/{id}/read`
- **Admin**: `GET /admin/{overview,users,events,orders,payments,webhook-events,audit-logs}`, `PATCH /admin/users/{id}/status`, `POST /admin/users/{id}/roles`, `POST /admin/orders/{id}/refund`

---

## 9. Security & Rate Limiting

- **Auth & Transport**: BCrypt (`strength=12`), HTTP-only cookies + Bearer support, refresh token rotation & revocation, CSP & `X-Frame-Options: DENY` headers.
- **Anti-IDOR**: Every resource validates RBAC role + resource ownership (`OwnershipValidator` / `SecurityUtils.requireOwnerOrAdmin`).
- **Bucket4j + Redis Rate Limits**:
  - `POST /api/v1/auth/login`: 5 req/min/IP
  - `POST /api/v1/events/*/waiting-room/join`: 3 req/min/user/event
  - `POST /api/v1/events/*/reservations`: 5 req/min/user
  - `POST /api/v1/orders/*/checkout`: 5 req/min/user
  - `POST /api/v1/check-in/scan`: 30 req/min/staff

---

## 10. Testing & Load Testing

### Backend Unit, Testcontainers & Concurrency Tests
```bash
cd ticket-booking-service
./mvnw test
```
Includes:
- Unit tests (`CoreBusinessUnitTests`)
- 5 Mandatory Concurrency Tests (`BookingConcurrencyIntegrationTest`):
  1. 100-thread double-booking contention on 1 seat
  2. Multi-seat overlapping deadlock prevention
  3. 10 concurrent identical webhooks + checkout `Idempotency-Key`
  4. Reservation expiration & claim release recovery
  5. Concurrent staff QR check-in atomicity
- Late-payment-after-expiration `REFUND_PENDING` test
- Waiting Room Admission Token integration test (`WaitingRoomIntegrationTest`)
- General Admission quantity contention & Admin Refund lifecycle test (`GeneralAdmissionAndRefundIntegrationTest`)

### Frontend Lint, Type-Check, Build & Playwright E2E
```bash
cd ticket-booking-fe
npm run lint && npx tsc --noEmit && npm run build
npx playwright test
```

### Load Testing & Post-Run Correctness Verification (k6)
See [docs/LOAD_TESTING_REPORT.md](docs/LOAD_TESTING_REPORT.md) and [load-tests/](load-tests/):
```bash
k6 run load-tests/scenario-a-browsing.js
k6 run -e EVENT_ID=<uuid> -e SEAT_IDS=<csv> -e TOKEN=<jwt> load-tests/scenario-d-flash-sale.js
docker exec -i ticketing-postgres psql -U postgres -d ticketing < load-tests/verify-correctness.sql
```

---

## 11. Technical Challenges & Trade-offs

See [docs/TECHNICAL_CHALLENGES_AND_TRADEOFFS.md](docs/TECHNICAL_CHALLENGES_AND_TRADEOFFS.md) for detailed analysis of:
- Modular Monolith vs Microservices
- PostgreSQL Pessimistic Locking vs Redis Distributed Lock
- RabbitMQ Delayed Queue vs Scheduled Sweeper
- WebSocket (STOMP) vs SSE
- RabbitMQ vs Redis Pub/Sub

---

## 12. Monitoring & Observability

- **Spring Boot Actuator & Micrometer** expose `/actuator/health` and `/actuator/prometheus` (JVM, HikariCP, HTTP latency p95/p99, scheduled jobs, and custom gauges `ticketing_reservation_expiration_backlog`, `ticketing_webhook_failures`, `ticketing_payment_failures`, `ticketing_outbox_failed`, `ticketing_waitingroom_backlog`).
- Pre-provisioned Grafana dashboard at [monitoring/grafana/provisioning/dashboards/ticketing-dashboard.json](monitoring/grafana/provisioning/dashboards/ticketing-dashboard.json).
