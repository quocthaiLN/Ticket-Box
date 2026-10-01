import { apiGet, apiPost, type ApiResponse } from "../lib/api-client";

export type PaymentProvider = "VNPAY" | "MOMO";
export type OrderStatus = "HELD" | "CONFIRMED" | "CANCELLED" | "EXPIRED";
export type PaymentStatus = "CREATING" | "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "REFUNDED";

export type CreateOrderItemInput = {
  ticket_type_id: string;
  quantity: number;
};

export type CreateOrderInput = {
  concert_id: string;
  items: CreateOrderItemInput[];
};

export type CreateOrderResult = {
  order_id: string;
  status: OrderStatus;
  total_amount: string;
  currency: string;
  hold_expires_at: string | null;
  items: Array<{
    ticket_type_id: string;
    quantity: number;
    unit_price: string;
    line_total: string;
  }>;
};

export type CreatePaymentResult = {
  payment_id: string;
  provider: PaymentProvider;
  status: PaymentStatus;
  checkout_url: string | null;
  order_id: string;
  hold_expires_at: string;
};

export type PaymentDetail = {
  payment_id: string;
  order_id: string;
  provider: PaymentProvider;
  status: PaymentStatus;
  amount: string;
  currency: string;
  checkout_url: string | null;
  refund_required: boolean;
  hold_expires_at: string | null;
  paid_at: string | null;
  failure_reason: string | null;
  created_at: string;
  updated_at: string;
};

export type OrderDetail = {
  order_id: string;
  status: OrderStatus;
};

export type CancelOrderResult = {
  order_id: string;
  status: OrderStatus;
  cancelled_at: string;
};

export async function createOrder(input: CreateOrderInput, idempotencyKey: string) {
  const response = await apiPost<ApiResponse<CreateOrderResult>>("/orders", input, {
    headers: {
      "Idempotency-Key": idempotencyKey,
    },
  });
  return response.data;
}

export async function getOrder(orderId: string) {
  const response = await apiGet<ApiResponse<OrderDetail>>(`/orders/${orderId}`);
  return response.data;
}

export async function getPayment(paymentId: string) {
  const response = await apiGet<ApiResponse<PaymentDetail>>(`/payments/${paymentId}`);
  return response.data;
}

/** Creates a payment attempt for an order already held by POST /orders. */
export async function createPayment(
  orderId: string,
  paymentProvider: PaymentProvider,
  idempotencyKey: string,
) {
  const response = await apiPost<ApiResponse<CreatePaymentResult>>(
    `/orders/${orderId}/payments`,
    { provider: paymentProvider },
    { headers: { "Idempotency-Key": idempotencyKey } },
  );
  return response.data;
}

export async function cancelOrder(orderId: string) {
  const response = await apiPost<ApiResponse<CancelOrderResult>>(`/orders/${orderId}/cancel`);
  return response.data;
}

export function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
