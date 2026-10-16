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
        { duration: '1m', target: 200 },   // Warm-up stage
        { duration: '2m', target: 1000 },  // Rush stage (gate opens)
        { duration: '3m', target: 1000 },  // Sustained peak (measure P50, P95, P99)
        { duration: '1m', target: 0 },     // Ramp-down
      ],
      gracefulRampDown: '30s',
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
  // STEP 1: User Login
  // --------------------------------------------------------------------------
  const token = loginUser(baseUrl, user.email, user.password);
  if (!token) {
    systemErrors.add(1);
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
      orderId = body.data ? body.data.id : null;
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
        'payment URL present': (r) => {
          try {
            return !!r.json().data.paymentUrl;
          } catch (e) {
            return false;
          }
        },
      });

      if (!paySuccess) {
        systemErrors.add(1);
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
    // Unexpected system error (500, 502, 504)
    systemErrors.add(1);
    successfulFlows.add(0);
  }

  // Pacing before next iteration
  sleep(Math.random() * 0.5 + 0.2);
}
