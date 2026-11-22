import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.API_URL || 'http://localhost:8080/api/v1';
const EVENT_ID = __ENV.EVENT_ID || '';
const SEAT_IDS = (__ENV.SEAT_IDS || '').split(',').filter(Boolean);
const TOKEN = __ENV.TOKEN || '';

// Scenario D: Flash Sale — 2,000 VUs competing for 50 seats over 60 seconds
export const options = {
  stages: [
    { duration: '10s', target: 500 },
    { duration: '40s', target: 2000 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  if (!EVENT_ID || SEAT_IDS.length === 0) return;
  const seatId = SEAT_IDS[Math.floor(Math.random() * SEAT_IDS.length)];
  const res = http.post(
    `${BASE_URL}/events/${EVENT_ID}/reservations`,
    JSON.stringify({ seatIds: [seatId] }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${TOKEN}`,
      },
    }
  );
  check(res, {
    'valid booking response (201/409/429)': (r) =>
      r.status === 201 || r.status === 409 || r.status === 429,
  });
}
