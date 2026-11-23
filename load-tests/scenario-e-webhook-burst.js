import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.API_URL || 'http://localhost:8080/api/v1';
const ORDER_CODE = __ENV.ORDER_CODE || 'ORD-TEST';
const AMOUNT = Number(__ENV.AMOUNT || 1500000);
const SIGNATURE = __ENV.SIGNATURE || '';

export const options = {
  vus: 50,
  iterations: 200,
};

export default function () {
  const res = http.post(
    `${BASE_URL}/webhooks/payos`,
    JSON.stringify({
      eventId: 'k6-burst-same-event-001',
      paymentId: 'k6-pay-001',
      orderCode: ORDER_CODE,
      amount: AMOUNT,
      status: 'PAID',
      signature: SIGNATURE,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, {
    'acknowledged': (r) => r.status === 200 || r.status === 401,
  });
}
