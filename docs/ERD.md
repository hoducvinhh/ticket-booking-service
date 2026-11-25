# Entity Relationship Diagram (ERD)

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : assigned
    users ||--o{ venues : owns
    venues ||--o{ events : hosts
    events ||--o{ ticket_sections : defines
    events ||--o{ ticket_types : offers
    ticket_sections ||--o{ seats : contains
    events ||--o{ event_staff : assigns
    users ||--o{ event_staff : works
    events ||--o{ reservations : receives
    users ||--o{ reservations : creates
    reservations ||--o{ reservation_items : includes
    reservations ||--o{ seat_claims : holds
    seats ||--o| seat_claims : locked_by
    reservations ||--o| orders : converts_to
    orders ||--o{ order_items : contains
    orders ||--o{ payments : initiates
    orders ||--o{ tickets : issues
    tickets ||--o{ checkin_logs : records
    events ||--o{ waiting_room_entries : queues
```

## Key Design Decisions
- **`seat_claims.seat_id` as PRIMARY KEY**: Enforces at the database level that at most one active hold exists per seat regardless of application concurrency.
- **`orders.reservation_id` UNIQUE**: Prevents creating multiple orders for the same reservation.
- **`webhook_events` `UNIQUE(provider, provider_event_id)` + `INSERT ... ON CONFLICT DO NOTHING`**: Guarantees atomic webhook deduplication under concurrent retries.
- **`tickets` Partial Unique Index (`uq_tickets_active_seat`)**: Guarantees one seat cannot have multiple `VALID`/`CHECKED_IN` tickets while allowing re-issuance if an order is refunded.
- **Transactional `outbox_events`**: Ensures business state changes and messaging events commit atomically.
