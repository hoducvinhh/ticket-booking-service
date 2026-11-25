# Technical Challenges & System Design Trade-offs

## System Design Trade-offs (Section 19)
1. **Modular Monolith vs Microservices**: Chosen Modular Monolith for single-transaction ACID consistency across booking, orders, and outbox, simpler debugging, and zero distributed transaction overhead, while keeping 14 clean module boundaries ready for future extraction.
2. **PostgreSQL Pessimistic Locking vs Redis Distributed Lock**: PostgreSQL `SELECT ... FOR UPDATE` (in sorted seat ID order) + `seat_claims` PK is the correctness authority; Redis `SET NX PX` is an optional coordination optimization.
3. **Delayed Queue vs Scheduled Sweeper**: RabbitMQ native TTL + DLX handles exact 10-minute expiration without full-table polling, while a `@Scheduled` sweeper (`LIMIT 100`) recovers any missed jobs if the broker restarts.
4. **WebSocket (STOMP) vs SSE**: Selected STOMP over WebSocket to handle multi-event realtime updates (`SEAT_HELD`, `SEAT_RELEASED`, `SEAT_SOLD`, `QUEUE_POSITION_UPDATED`, `USER_ADMITTED`, `RESERVATION_EXPIRING`), with mandatory REST refetch on reconnect.
5. **RabbitMQ vs Redis Pub/Sub**: Selected RabbitMQ for durable queues, acknowledgments, bounded retries, and Dead Letter Queues (`*.dlq`).

## Technical Challenges Solved (Section 23.5)
- **Challenge 1 — Double Booking & Deadlocks**: Solved via deterministic `seatIds` ascending sort + `PESSIMISTIC_WRITE` + `seat_claims` PK (`409 Conflict`).
- **Challenge 2 — Duplicate Checkout & Webhook Bursts**: Solved via `idempotency_records` (payload hash comparison) and atomic `webhook_events` `ON CONFLICT DO NOTHING` + late-payment `REFUND_PENDING` workflow.
- **Challenge 3 — Reservation Expiration**: Solved via RabbitMQ `reservation.delay.queue` → `reservation.expire.queue` + `@Scheduled` recovery sweeper verifying `reservation_id` before releasing claims.
- **Challenge 4 — Database Read Contention**: Solved via Redis `event:{eventId}:seat-map` caching with immediate cache eviction on state mutation + indexed queries.
