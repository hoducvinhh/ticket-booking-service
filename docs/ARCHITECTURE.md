# Architecture Documentation

## High-Level System Architecture

```mermaid
flowchart TB
    User["Guest / Customer / Organizer / Staff / Admin"] -->|HTTPS / WSS| FE["Next.js + TypeScript (:3000)"]
    FE -->|REST /api/v1 & STOMP /ws| BE["Spring Boot Modular Monolith (:8080)"]
    BE -->|ACID Transactions, Row Locks, Constraints| PG[("PostgreSQL (:5432)")]
    BE -->|Seat Map Cache, TTL, Lock, Sorted Set, Rate Limit| RD[("Redis (:6379)")]
    BE -->|Outbox Poller & Delayed Expiration + DLQ| RMQ[("RabbitMQ (:5672 / :15672)")]
    BE -->|Payment URL & Signed Webhook| GW["PayOS / VNPay / Sandbox"]
    BE -->|SMTP| SMTP["Mail Server"]
    PROM["Prometheus (:9090)"] -->|/actuator/prometheus| BE
    GRAF["Grafana (:3001)"] --> PROM
```

## Modular Monolith Layers

- **Controller**: HTTP/DTO validation, delegates immediately to Application Service (zero business logic, zero direct Entity responses).
- **Application Service**: Orchestrates `@Transactional` boundaries, locks, idempotency, outbox persistence, and post-commit events.
- **Domain / Entity / Enums**: Encapsulates state machines (`EventStatus`, `ReservationStatus`, `OrderStatus`, `PaymentStatus`, `TicketStatus`) and optimistic locking (`@Version`).
- **Repository**: Spring Data JPA (`PESSIMISTIC_WRITE`, Criteria `Specification`, atomic `UPDATE`, native `ON CONFLICT DO NOTHING`).
