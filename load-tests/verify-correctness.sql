-- Section 17.7: Post-Load-Test Correctness Verification Queries
-- Every query below MUST return 0 rows / 0 violations.

-- 1. No seat has more than 1 active/valid ticket
SELECT seat_id, COUNT(*) AS ticket_count
FROM tickets
WHERE seat_id IS NOT NULL AND status IN ('VALID', 'CHECKED_IN')
GROUP BY seat_id
HAVING COUNT(*) > 1;

-- 2. Total sold tickets per event never exceeds total seats
SELECT e.id AS event_id,
       COUNT(DISTINCT t.id) AS sold_tickets,
       COUNT(DISTINCT s.id) AS total_seats
FROM events e
JOIN ticket_sections ts ON ts.event_id = e.id
JOIN seats s ON s.section_id = ts.id
LEFT JOIN tickets t ON t.event_id = e.id AND t.status IN ('VALID', 'CHECKED_IN')
GROUP BY e.id
HAVING COUNT(DISTINCT t.id) > COUNT(DISTINCT s.id);

-- 3. No order has multiple SUCCESS payments
SELECT order_id, COUNT(*) AS success_count
FROM payments
WHERE status = 'SUCCESS'
GROUP BY order_id
HAVING COUNT(*) > 1;

-- 4. No duplicate tickets per order item / seat
SELECT order_id, seat_id, COUNT(*) AS cnt
FROM tickets
WHERE seat_id IS NOT NULL
GROUP BY order_id, seat_id
HAVING COUNT(*) > 1;

-- 5. No orphaned seat_claims belonging to EXPIRED or CANCELLED reservations
SELECT sc.seat_id, sc.reservation_id, r.status
FROM seat_claims sc
JOIN reservations r ON r.id = sc.reservation_id
WHERE r.status IN ('EXPIRED', 'CANCELLED');
