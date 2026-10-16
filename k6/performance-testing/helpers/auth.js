import http from 'k6/http';
import { check } from 'k6';
import { authLoginDuration, systemErrors } from './metrics.js';

/**
 * Logs in a user and returns the JWT accessToken.
 * @param {string} baseUrl
 * @param {string} email
 * @param {string} password
 * @returns {string|null} JWT accessToken or null on failure
 */
export function loginUser(baseUrl, email, password) {
  const url = `${baseUrl}/auth/login`;
  const payload = JSON.stringify({ email, password });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    tags: { name: 'POST /auth/login' },
  };

  const res = http.post(url, payload, params);
  authLoginDuration.add(res.timings.duration);

  const success = check(res, {
    'login status is 200': (r) => r.status === 200,
    'login has accessToken': (r) => {
      try {
        const body = r.json();
        return body && body.data && !!(body.data.access_token || body.data.accessToken);
      } catch (e) {
        return false;
      }
    },
  });

  if (!success) {
    systemErrors.add(1);
    return null;
  }

  const data = res.json().data;
  return data.access_token || data.accessToken;
}
