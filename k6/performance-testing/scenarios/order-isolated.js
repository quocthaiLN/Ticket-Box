import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { config } from '../config/environments.js';
import { uuidv4 } from '../helpers/uuid.js';
import { loginUser } from '../helpers/auth.js';
import {
  orderHoldDuration,
  orderGetDuration,
  paymentCreateDuration,
  ordersHeldSuccess,
  ordersSoldOut,
  systemErrors,
} from '../helpers/metrics.js';

// Load 10,000 user credentials into shared memory
const users = new SharedArray('users', function () {
  return JSON.parse(open('../data/users.json'));
});

export const options = {
  scenarios: {
    order_benchmark: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 500 },   // Spike vọt lên 500 VUs trong 10s
        { duration: '50s', target: 500 },   // Duy trì đỉnh trong phút đầu tiên
        { duration: '10s', target: 150 },   // Giảm tải về 30%
        { duration: '2m', target: 150 },    // Duy trì 2 phút tiếp theo
        { duration: '10s', target: 0 },     // Hạ nhiệt
      ],
      gracefulRampDown: '10s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const baseUrl = config.baseUrl;
  const concertId = config.targetConcertId;
  const user = users[(__VU - 1) % users.length];

  // 1. Authenticate user to obtain Bearer token
  const token = loginUser(baseUrl, user.email, user.password);
  if (!token) {
    sleep(1);
    return;
  }

  const authHeaders = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 2. Select a random ticket type among the available types
  const ticketType = config.ticketTypes[Math.floor(Math.random() * config.ticketTypes.length)];
  const quantity = Math.floor(Math.random() * 2) + 1; // 1 or 2 tickets

  const orderPayload = JSON.stringify({
    concert_id: concertId,
    items: [
      {
        ticket_type_id: ticketType.id,
        quantity: quantity,
      },
    ],
  });

  const orderIdempotencyKey = uuidv4();
  const holdHeaders = Object.assign({}, authHeaders, {
    'Idempotency-Key': orderIdempotencyKey,
  });

  // 3. POST /orders (Hold Tickets)
  const holdStart = Date.now();
  const holdRes = http.post(`${baseUrl}/orders`, orderPayload, {
    headers: holdHeaders,
    tags: { name: 'POST /orders' },
  });
  orderHoldDuration.add(Date.now() - holdStart);

  if (holdRes.status === 201) {
    ordersHeldSuccess.add(1);

    let orderId = null;
    try {
      const body = holdRes.json();
      orderId = body.data ? (body.data.order_id || body.data.id) : null;
    } catch (e) {
      systemErrors.add(1);
    }

    if (orderId) {
      sleep(0.1);

      // 4. GET /orders/{orderId}
      const getRes = http.get(`${baseUrl}/orders/${orderId}`, {
        headers: authHeaders,
        tags: { name: 'GET /orders/:id' },
      });
      orderGetDuration.add(getRes.timings.duration);

      // 5. POST /orders/{orderId}/payments (Create Payment Request)
      const paymentPayload = JSON.stringify({
        provider: 'VNPAY',
        bankCode: 'VNBANK',
        locale: 'vn',
      });

      const paymentIdempotencyKey = uuidv4();
      const paymentHeaders = Object.assign({}, authHeaders, {
        'Idempotency-Key': paymentIdempotencyKey,
      });

      const payStart = Date.now();
      const payRes = http.post(`${baseUrl}/orders/${orderId}/payments`, paymentPayload, {
        headers: paymentHeaders,
        tags: { name: 'POST /orders/:id/payments' },
      });
      paymentCreateDuration.add(Date.now() - payStart);

      check(payRes, {
        'payment created status 201': (r) => r.status === 201,
        'payment has checkout_url': (r) => {
          try {
            const data = r.json().data;
            return !!(data.checkout_url || data.paymentUrl);
          } catch (e) {
            return false;
          }
        },
      }) || systemErrors.add(1);
    }
  } else if (holdRes.status === 400 || holdRes.status === 409) {
    // Sold out, limit exceeded, or business conflict
    ordersSoldOut.add(1);
  } else {
    // System error (5xx or connection issue)
    systemErrors.add(1);
  }

  sleep(0.2);
}
