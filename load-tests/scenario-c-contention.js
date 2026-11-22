import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.API_URL || 'http://localhost:8080/api/v1';
const EVENT_ID = __ENV.EVENT_ID || '';
const SEAT_ID = __ENV.SEAT_ID || '';
const TOKEN = __ENV.TOKEN || '';

export const options = {
  vus: 100,
  iterations: 100,
  thresholds: {
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/events/${EVENT_ID}/reservations`,
    JSON.stringify({ seatIds: [SEAT_ID] }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${TOKEN}`,
      },
    }
  );
  check(res, {
    '201 created or 409 conflict': (r) => r.status === 201 || r.status === 409,
  });
}
