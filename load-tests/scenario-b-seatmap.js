import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.API_URL || 'http://localhost:8080/api/v1';
const EVENT_ID = __ENV.EVENT_ID || '';

export const options = {
  vus: 200,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  if (!EVENT_ID) return;
  const res = http.get(`${BASE_URL}/events/${EVENT_ID}/seat-map`);
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
  sleep(0.3);
}
