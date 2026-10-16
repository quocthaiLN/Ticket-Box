import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { config } from '../config/environments.js';
import { uuidv4 } from '../helpers/uuid.js';
import { loginUser } from '../helpers/auth.js';
import {
  catalogDetailDuration,
  catalogSeatMapDuration,
  catalogTicketTypesDuration,
  orderHoldDuration,
  orderGetDuration,
  paymentCreateDuration,
  ordersHeldSuccess,
  ordersSoldOut,
  systemErrors,
  successfulFlows,
  trackErrorStatus,
} from '../helpers/metrics.js';

// Load 10,000 user credentials into shared memory (read-only buffer for all VUs)
const users = new SharedArray('users', function () {
  return JSON.parse(open('../data/users.json'));
});

export const options = {
  scenarios: {
    flash_sale_e2e: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 1000 },  // Spike tức thì lên đỉnh ngay khi mở cổng
        { duration: '45s', target: 1000 },  // Duy trì đỉnh trong phút đầu (70% lưu lượng dồn vào đây)
        { duration: '15s', target: 300 },   // Giảm tải xuống mức 30%
        { duration: '3m45s', target: 300 }, // Duy trì 300 VUs trong 4 phút tiếp theo
        { duration: '15s', target: 0 },     // Hạ nhiệt hoàn tất đợt mở bán 5 phút
      ],
      gracefulRampDown: '15s',
    },
  },
  // Exploratory benchmarking: display full percentile distribution
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const baseUrl = config.baseUrl;
  const concertId = config.targetConcertId;
  const user = users[(__VU - 1) % users.length];

  // --------------------------------------------------------------------------
  // STEP 1: User Authentication (Pre-signed JWT token or fallback to login)
  // --------------------------------------------------------------------------
  let token = user.token;
  if (!token) {
    token = loginUser(baseUrl, user.email, user.password);
  }
  if (!token) {
    trackErrorStatus(500);
    sleep(1);
    return;
  }

  const authHeaders = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // Realistic user pacing between page loads (100ms - 300ms)
  sleep(Math.random() * 0.2 + 0.1);

  // --------------------------------------------------------------------------
  // STEP 2: View Concert Details
  // --------------------------------------------------------------------------
  {
    const res = http.get(`${baseUrl}/concerts/${concertId}`, {
      headers: authHeaders,
      tags: { name: 'GET /concerts/:id' },
    });
    catalogDetailDuration.add(res.timings.duration);
    check(res, {
      'view concert status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  sleep(Math.random() * 0.2 + 0.1);

  // --------------------------------------------------------------------------
  // STEP 3: View Seat Map
  // --------------------------------------------------------------------------
  {
    const res = http.get(`${baseUrl}/concerts/${concertId}/seat-map`, {
      headers: authHeaders,
      tags: { name: 'GET /concerts/:id/seat-map' },
    });
    catalogSeatMapDuration.add(res.timings.duration);
    check(res, {
      'view seat-map status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  sleep(Math.random() * 0.2 + 0.1);

  // --------------------------------------------------------------------------
  // STEP 4: Browse Ticket Types
  // --------------------------------------------------------------------------
  {
    const res = http.get(`${baseUrl}/concerts/${concertId}/ticket-types`, {
      headers: authHeaders,
      tags: { name: 'GET /concerts/:id/ticket-types' },
    });
    catalogTicketTypesDuration.add(res.timings.duration);
    check(res, {
      'view ticket-types status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  sleep(Math.random() * 0.3 + 0.2);

  // --------------------------------------------------------------------------
  // STEP 5: Select Ticket & Send Hold Order (POST /orders)
  // --------------------------------------------------------------------------
  // Pick random ticket type to simulate distributed demand across the concert
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
      sleep(Math.random() * 0.2 + 0.1);

      // ----------------------------------------------------------------------
      // STEP 6: Request Payment URL (POST /orders/{id}/payments via VNPay)
      // ----------------------------------------------------------------------
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

      const paySuccess = check(payRes, {
        'payment created status 201': (r) => r.status === 201,
        'checkout URL present': (r) => {
          try {
            const data = r.json().data;
            return !!(data.checkout_url || data.paymentUrl);
          } catch (e) {
            return false;
          }
        },
      });

      if (!paySuccess) {
        trackErrorStatus(payRes.status);
      } else {
        // ------------------------------------------------------------------
        // STEP 7: Verify Order Status (GET /orders/{id})
        // ------------------------------------------------------------------
        const getRes = http.get(`${baseUrl}/orders/${orderId}`, {
          headers: authHeaders,
          tags: { name: 'GET /orders/:id' },
        });
        orderGetDuration.add(getRes.timings.duration);

        check(getRes, {
          'get order status 200': (r) => r.status === 200,
        });

        successfulFlows.add(1);
      }
    }
  } else if (holdRes.status === 400 || holdRes.status === 409) {
    // Expected business failure when inventory is exhausted or quota reached
    ordersSoldOut.add(1);
    successfulFlows.add(0);
  } else {
    // Unexpected system error (429, 500, 503, 504)
    trackErrorStatus(holdRes.status);
    successfulFlows.add(0);
  }

  // Pacing before next iteration
  sleep(Math.random() * 0.5 + 0.2);
}
