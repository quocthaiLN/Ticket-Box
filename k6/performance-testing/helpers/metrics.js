import { Trend, Counter, Rate } from 'k6/metrics';

// Catalog Latency Trends
export const catalogListDuration = new Trend('catalog_list_duration', true);
export const catalogDetailDuration = new Trend('catalog_detail_duration', true);
export const catalogSeatMapDuration = new Trend('catalog_seatmap_duration', true);
export const catalogTicketTypesDuration = new Trend('catalog_tickettypes_duration', true);
export const catalogInventoryDuration = new Trend('catalog_inventory_duration', true);
export const catalogQuotaDuration = new Trend('catalog_quota_duration', true);

// Auth & Order Latency Trends
export const authLoginDuration = new Trend('auth_login_duration', true);
export const orderHoldDuration = new Trend('order_hold_duration', true);
export const orderGetDuration = new Trend('order_get_duration', true);
export const paymentCreateDuration = new Trend('payment_create_duration', true);

// Business & Technical Counters
export const ordersHeldSuccess = new Counter('orders_held_success');
export const ordersSoldOut = new Counter('orders_sold_out');
export const systemErrors = new Counter('system_errors');
export const successfulFlows = new Rate('successful_flows_rate');

// HTTP Status Breakdown Counters
export const http429RateLimited = new Counter('http_429_rate_limited');
export const http500ServerError = new Counter('http_500_server_error');
export const http503Unavailable = new Counter('http_503_unavailable');
export const http5xxOther = new Counter('http_5xx_other');

/**
 * Tracks error status code into specific breakdown counters
 * @param {number} status
 */
export function trackErrorStatus(status) {
  if (status === 429) {
    http429RateLimited.add(1);
  } else if (status === 500) {
    http500ServerError.add(1);
  } else if (status === 503) {
    http503Unavailable.add(1);
  } else if (status >= 500) {
    http5xxOther.add(1);
  }
  systemErrors.add(1);
}
