import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { config } from '../config/environments.js';
import {
  catalogListDuration,
  catalogDetailDuration,
  catalogSeatMapDuration,
  catalogTicketTypesDuration,
  catalogInventoryDuration,
  catalogQuotaDuration,
  systemErrors,
} from '../helpers/metrics.js';
import { loginUser } from '../helpers/auth.js';

// Load 10,000 user credentials into shared memory (read-only buffer for all VUs)
const users = new SharedArray('users', function () {
  return JSON.parse(open('../data/users.json'));
});

export const options = {
  scenarios: {
    catalog_benchmark: {
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
  // Exploratory benchmark: summary displays P50, P90, P95, P99
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const baseUrl = config.baseUrl;
  const concertId = config.targetConcertId;
  const headers = config.defaultHeaders;

  // 1. GET /concerts
  {
    const res = http.get(`${baseUrl}/concerts?page=0&size=20`, {
      headers,
      tags: { name: 'GET /concerts' },
    });
    catalogListDuration.add(res.timings.duration);
    check(res, {
      'concerts list status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  sleep(0.1);

  // 2. GET /concerts/{id}
  {
    const res = http.get(`${baseUrl}/concerts/${concertId}`, {
      headers,
      tags: { name: 'GET /concerts/:id' },
    });
    catalogDetailDuration.add(res.timings.duration);
    check(res, {
      'concert detail status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  sleep(0.1);

  // 3. GET /concerts/{id}/seat-map
  {
    const res = http.get(`${baseUrl}/concerts/${concertId}/seat-map`, {
      headers,
      tags: { name: 'GET /concerts/:id/seat-map' },
    });
    catalogSeatMapDuration.add(res.timings.duration);
    check(res, {
      'seat-map status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  sleep(0.1);

  // 4. GET /concerts/{id}/ticket-types
  {
    const res = http.get(`${baseUrl}/concerts/${concertId}/ticket-types`, {
      headers,
      tags: { name: 'GET /concerts/:id/ticket-types' },
    });
    catalogTicketTypesDuration.add(res.timings.duration);
    check(res, {
      'ticket-types status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  sleep(0.1);

  // 5. GET /concerts/{id}/inventory
  {
    const res = http.get(`${baseUrl}/concerts/${concertId}/inventory`, {
      headers,
      tags: { name: 'GET /concerts/:id/inventory' },
    });
    catalogInventoryDuration.add(res.timings.duration);
    check(res, {
      'inventory status 200': (r) => r.status === 200,
    }) || systemErrors.add(1);
  }

  // 6. Quota check (requires authentication, 1 in 5 iterations to test quota endpoint)
  if (__ITER % 5 === 0) {
    const user = users[(__VU - 1) % users.length];
    const token = loginUser(baseUrl, user.email, user.password);
    if (token) {
      const authHeaders = Object.assign({}, headers, {
        Authorization: `Bearer ${token}`,
      });
      const res = http.get(`${baseUrl}/concerts/${concertId}/quota`, {
        headers: authHeaders,
        tags: { name: 'GET /concerts/:id/quota' },
      });
      catalogQuotaDuration.add(res.timings.duration);
      check(res, {
        'quota status 200': (r) => r.status === 200,
      }) || systemErrors.add(1);
    }
  }

  sleep(0.2);
}
