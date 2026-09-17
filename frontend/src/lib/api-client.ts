import {
  clearAuthSession,
  getAccessToken,
  storeAuthSession,
  type AuthUser,
} from "./auth-session";

export type PaginationMeta = {
  page?: number;
  page_size?: number;
  total_items?: number;
  total_pages?: number;
  has_more?: boolean;
  next_cursor?: string | null;
  limit?: number;
};

export type ApiResponse<TData> = {
  data: TData;
  pagination?: PaginationMeta;
  meta: {
    request_id: string;
    [key: string]: unknown;
  };
};

export type ApiCollectionResponse<TData> = ApiResponse<TData[]> & {
  pagination: PaginationMeta;
};

export type Venue = {
  id: string;
  name: string;
  address: string;
  city: string;
  capacity?: number;
  map_url?: string;
};

export type ConcertStatus = "DRAFT" | "PUBLISHED" | "CANCELLED" | "COMPLETED";

export type ConcertSummary = {
  id: string;
  title: string;
  slug: string;
  description?: string;
  artist_name: string;
  starts_at: string;
  ends_at: string;
  status: ConcertStatus;
  cover_image_url?: string;
  guest_drive_folder_id?: string;
  venue: string | Pick<Venue, "id" | "name" | "city">;
  organizer_id?: string;
  organizer_name?: string;
  ticket_price_range?: {
    min_amount: number;
    max_amount: number;
    currency: string;
  };
};

// Nghệ sĩ trong lineup (concert nhiều nghệ sĩ); vắng → fallback field đơn.
export type ConcertArtist = {
  name: string;
  bio: string;
  image_url: string | null;
};

export type ConcertDetail = {
  id: string;
  title: string;
  slug: string;
  venue: string | Venue;
  description?: string;
  artist_name: string;
  artist_bio?: string;
  artist_bio_image_url?: string;
  artists?: ConcertArtist[];
  starts_at: string;
  ends_at: string;
  status: ConcertStatus;
  cover_image_url?: string;
  seat_map_url?: string;
  seat_map_image_url?: string;
  organizer_id?: string;
  organizer_name?: string;
  created_at?: string;
  updated_at?: string;
};

export type SeatZone = {
  id: string;
  concert_id?: string;
  code: string;
  name: string;
  description?: string;
  capacity: number;
  svg_path?: string;
  sort_order: number;
};

export type TicketType = {
  id: string;
  concert_id?: string;
  seat_zone_id: string;
  zone_code?: string;
  name: string;
  description?: string;
  price: number | {
    amount: number;
    currency: "VND";
  };
  currency?: string;
  total_quantity?: number;
  held_quantity?: number;
  sold_quantity?: number;
  available_quantity?: number;
  max_per_user: number;
  sale_start_at?: string;
  sale_end_at?: string;
  status: "DRAFT" | "ON_SALE" | "SOLD_OUT" | "CLOSED" | string;
};

export type SeatMapResponse = {
  concert_id: string;
  svg_url?: string;
  fallback_image_url?: string;
  zones: SeatZone[];
};

export type Inventory = {
  concert_id: string;
  as_of: string;
  items: Array<{
    ticket_type_id: string;
    seat_zone_id: string;
    zone_code: string;
    available_quantity: number;
    status: "ON_SALE" | "SOLD_OUT" | "CLOSED" | string;
    display_status:
      | "AVAILABLE"
      | "LOW_STOCK"
      | "SOLD_OUT"
      | "CLOSED"
      | "UPDATING"
      | string;
  }>;
};

export type TicketQuotaItem = {
  ticket_type_id: string;
  max_per_user: number;
  held_quantity: number;
  paid_quantity: number;
  remaining_quantity: number;
};

export type TicketQuota = {
  concert_id: string;
  items: TicketQuotaItem[];
};

export type ConcertMetadata = {
  concert: ConcertDetail;
  venue?: Venue;
  seat_zones: SeatZone[];
  ticket_types: TicketType[];
  seat_map: {
    svg_url?: string;
    fallback_image_url?: string;
  };
  artist_bio?: string;
  artist_bio_image_url?: string;
  artists?: ConcertArtist[];
};

const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL ?? "";

// Sends a GET request to the TicketBox API and parses the JSON response.
export async function apiGet<TData>(
  path: string,
  init?: RequestInit,
): Promise<TData> {
  return apiRequest<TData>(path, { method: "GET", ...init });
}

export async function apiPost<TData>(
  path: string,
  body?: unknown,
  init?: RequestInit,
): Promise<TData> {
  return apiRequest<TData>(path, jsonInit("POST", body, init));
}

export async function apiPatch<TData>(
  path: string,
  body?: unknown,
  init?: RequestInit,
): Promise<TData> {
  return apiRequest<TData>(path, jsonInit("PATCH", body, init));
}

export async function apiPut<TData>(
  path: string,
  body?: unknown,
  init?: RequestInit,
): Promise<TData> {
  return apiRequest<TData>(path, jsonInit("PUT", body, init));
}

export async function apiDelete<TData>(
  path: string,
  init?: RequestInit,
): Promise<TData> {
  return apiRequest<TData>(path, { method: "DELETE", ...init });
}

export async function apiUploadFile<TData>(
  path: string,
  file: File,
): Promise<TData> {
  return apiRequest<TData>(path, {
    method: "POST",
    body: file,
    headers: {
      "Content-Type": file.type || "application/octet-stream",
      "X-File-Name": encodeURIComponent(file.name),
    },
  });
}

export type ListConcertsParams = {
  q?: string;
  city?: string;
  from?: string;
  to?: string;
  page?: number | string;
  size?: number | string;
  sortBy?: string;
  sortOrder?: "asc" | "desc" | string;
  [key: string]: unknown;
};

export async function listConcerts(params: ListConcertsParams = {}) {
  const queryParams: Record<string, string> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && String(value).trim().length > 0) {
      queryParams[key] = String(value);
    }
  }
  const response = await apiGet<ApiResponse<ConcertSummary[]>>(
    `/concerts${queryString(queryParams)}`,
  );
  return response.data;
}

export async function getConcert(concertId: string) {
  const response = await apiGet<ApiResponse<ConcertDetail>>(
    `/concerts/${concertId}`,
  );
  return response.data;
}

export async function getConcertMetadata(concertId: string) {
  const response = await apiGet<ApiResponse<ConcertMetadata>>(
    `/concerts/${concertId}/metadata`,
  );
  return response.data;
}

export async function getConcertSeatMap(concertId: string) {
  const response = await apiGet<ApiResponse<SeatMapResponse>>(
    `/concerts/${concertId}/seat-map`,
  );
  return response.data;
}

export async function listTicketTypes(
  concertId: string,
  includeClosed = false,
) {
  const response = await apiGet<ApiResponse<TicketType[]>>(
    `/concerts/${concertId}/ticket-types${includeClosed ? "?includeClosed=true" : ""}`,
  );
  return response.data;
}

export async function getInventory(concertId: string) {
  const response = await apiGet<ApiResponse<Inventory>>(
    `/concerts/${concertId}/inventory`,
  );
  return response.data;
}

/** Returns the authenticated user's remaining purchase quota for each ticket type. */
export async function getConcertQuota(concertId: string) {
  const response = await apiGet<ApiResponse<TicketQuota>>(
    `/concerts/${concertId}/quota`,
  );
  return response.data;
}

export async function listVenues() {
  const response = await apiGet<ApiCollectionResponse<Venue>>(
    "/admin/venues?limit=100",
  );
  return response.data;
}

export async function listAdminConcerts() {
  const response = await apiGet<ApiCollectionResponse<ConcertSummary>>(
    "/admin/concerts?limit=100&sort=-starts_at",
  );
  return response.data;
}

export function createVenue(input: Omit<Venue, "id">) {
  return apiPost<ApiResponse<Venue>>("/admin/venues", input);
}

export function createConcert(input: Record<string, unknown>) {
  return apiPost<ApiResponse<ConcertDetail>>("/admin/concerts", input);
}

export function publishConcert(concertId: string) {
  return apiPost<ApiResponse<ConcertDetail>>(
    `/admin/concerts/${concertId}/publish`,
  );
}

export function cancelConcert(concertId: string) {
  return apiPost<ApiResponse<ConcertDetail>>(
    `/admin/concerts/${concertId}/cancel`,
  );
}

export function createSeatZone(
  concertId: string,
  input: Record<string, unknown>,
) {
  return apiPost<ApiResponse<SeatZone>>(
    `/admin/concerts/${concertId}/seat-zones`,
    input,
  );
}

export function createTicketType(
  concertId: string,
  input: Record<string, unknown>,
) {
  return apiPost<ApiResponse<TicketType>>(
    `/admin/concerts/${concertId}/ticket-types`,
    input,
  );
}

let refreshPromise: Promise<string | null> | null = null;

async function tryRefreshToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const res = await fetch(`${apiBaseUrl}/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
      });

      if (!res.ok) {
        clearAuthSession();
        return null;
      }

      const body = (await res.json()) as {
        data: {
          access_token: string;
          expires_in: number;
          user: AuthUser;
        };
      };

      if (body?.data?.access_token) {
        storeAuthSession({
          accessToken: body.data.access_token,
          expiresIn: body.data.expires_in,
          user: body.data.user,
        });
        return body.data.access_token;
      }

      clearAuthSession();
      return null;
    } catch {
      clearAuthSession();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

async function apiRequest<TData>(
  path: string,
  init: RequestInit,
): Promise<TData> {
  const token = getAccessToken();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    credentials: init.credentials ?? "same-origin",
    headers,
  });

  if (response.status === 401 && !path.startsWith("/auth/")) {
    const newToken = await tryRefreshToken();
    if (newToken) {
      headers.set("Authorization", `Bearer ${newToken}`);
      const retryResponse = await fetch(`${apiBaseUrl}${path}`, {
        ...init,
        credentials: init.credentials ?? "same-origin",
        headers,
      });

      if (retryResponse.ok) {
        if (retryResponse.status === 204) {
          return undefined as TData;
        }
        return retryResponse.json() as Promise<TData>;
      }
    }
  }

  if (!response.ok) {
    const { message, code } = await parseErrorResponse(response);
    const retryAfterHeader = response.headers.get("Retry-After");
    const retryAfter = retryAfterHeader === null ? undefined : Number(retryAfterHeader);
    throw new ApiClientError(
      message,
      code,
      response.status,
      Number.isFinite(retryAfter) ? retryAfter : undefined,
    );
  }

  if (response.status === 204) {
    return undefined as TData;
  }

  return response.json() as Promise<TData>;
}

function jsonInit(
  method: string,
  body?: unknown,
  init?: RequestInit,
): RequestInit {
  return {
    ...init,
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  };
}

function queryString(params: Record<string, string>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value.trim().length > 0) search.set(key, value);
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

// Lỗi API kèm `code` từ ProblemDetails để UI map sang thông báo thân thiện
// (message thô của server có thể chứa UUID/tiếng Anh — không nên hiển thị thẳng).
export class ApiClientError extends Error {
  constructor(
    message: string,
    public code?: string,
    public status?: number,
    public retryAfter?: number,
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

export function getApiErrorCode(err: unknown): string | undefined {
  return err instanceof ApiClientError ? err.code : undefined;
}

async function parseErrorResponse(response: Response): Promise<{ message: string; code?: string }> {
  const fallback = `TicketBox API request failed: ${response.status}`;
  const text = await response.text();
  if (!text) return { message: fallback };

  try {
    const problem = JSON.parse(text) as {
      error?: {
        code?: string;
        message?: string;
        details?: unknown;
      };
      detail?: string;
      title?: string;
      code?: string;
      message?: string;
      errors?: Array<{ field?: string; message?: string }> | Record<string, string>;
    };

    // 1. Check Spring Boot ErrorResponse { error: { code, message, details } }
    if (problem.error) {
      let detailMsg: string | undefined;
      if (problem.error.details && typeof problem.error.details === "object") {
        const firstVal = Object.values(problem.error.details as Record<string, unknown>)[0];
        if (typeof firstVal === "string") {
          detailMsg = firstVal;
        }
      }
      const message = detailMsg ?? problem.error.message ?? fallback;
      return { message, code: problem.error.code };
    }

    // 2. Check ProblemDetails RFC 7807 / standard array errors
    const arrayErrors = Array.isArray(problem.errors) ? problem.errors : undefined;
    const firstFieldError = arrayErrors?.find((item) => item.message);
    const message = firstFieldError?.message ?? problem.message ?? problem.detail ?? problem.title ?? fallback;
    return { message, code: problem.code };
  } catch {
    return { message: text };
  }
}
