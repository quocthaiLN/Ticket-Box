import { apiGet, apiPatch, apiPost, apiPostFormData, type ApiCollectionResponse, type ApiResponse } from "../lib/api-client";

export type CatalogConcert = {
  id: string; title: string; slug: string; venue: string; artist_name: string;
  description: string | null; artist_bio: string | null;
  starts_at: string; ends_at: string;
  status: "DRAFT" | "PUBLISHED" | "CANCELED" | "CANCELLED" | "COMPLETED";
  cover_image_url: string | null; seat_map_url: string | null;
};
export type ConcertInput = {
  title: string; slug: string; venue: string; artist_name: string;
  starts_at: string; ends_at: string; description?: string | null;
  artist_bio?: string | null; cover_image_url?: string | null; seat_map_url?: string | null;
};
export type ConcertPatch = Partial<Omit<ConcertInput, "slug">>;
export type ZoneInput = { code: string; name: string; capacity: number; description?: string | null; svg_path?: string | null; sort_order?: number };
export type CatalogZone = ZoneInput & { id: string; concert_id: string; sort_order: number };
export type TicketInput = {
  seat_zone_id: string; name: string; description?: string | null; price: number; currency: string;
  total_quantity: number; max_per_user: number; sale_start_at: string; sale_end_at: string;
};
export type TicketStatus = "DRAFT" | "ACTIVE" | "ON_SALE" | "SUSPENDED" | "CLOSED" | "SOLD_OUT";
export type TicketPatch = Partial<Omit<TicketInput, "seat_zone_id" | "currency" | "name">> & { name: string; status?: TicketStatus };
export type CatalogTicket = TicketInput & {
  id: string; concert_id: string; zone_code: string; held_quantity: number; sold_quantity: number;
  available_quantity: number; status: TicketStatus;
};
export type CatalogMetadata = {
  concert: CatalogConcert; seat_zones: CatalogZone[]; ticket_types: CatalogTicket[];
  seat_map: { svg_url: string | null; fallback_image_url: string | null }; artist_bio: string | null;
};
export type BioJob = { id: string; concert_id: string; status: "PENDING" | "PROCESSING" | "DONE" | "FAILED"; generated_bio: string | null; error_message: string | null };

export function listCatalogConcerts(params: { page?: number; q?: string; status?: string } = {}) {
  const query = new URLSearchParams({ page: String(params.page ?? 0), size: "20", sortBy: "createdAt", sortOrder: "desc" });
  if (params.q?.trim()) query.set("q", params.q.trim());
  if (params.status && params.status !== "all") query.set("status", params.status);
  return apiGet<ApiCollectionResponse<CatalogConcert>>(`/admin/concerts?${query}`);
}
export async function getCatalogMetadata(id: string) {
  return (await apiGet<ApiResponse<CatalogMetadata>>(`/admin/concerts/${id}/metadata`)).data;
}
export async function createCatalogConcert(input: ConcertInput) {
  return (await apiPost<ApiResponse<CatalogConcert>>("/admin/concerts", input)).data;
}
export async function patchCatalogConcert(id: string, input: ConcertPatch) {
  return (await apiPatch<ApiResponse<CatalogConcert>>(`/admin/concerts/${id}`, input)).data;
}
export async function publishCatalogConcert(id: string) {
  return (await apiPost<ApiResponse<CatalogConcert>>(`/admin/concerts/${id}/publish`)).data;
}
export async function cancelCatalogConcert(id: string, reason: string) {
  return (await apiPost<ApiResponse<CatalogConcert>>(`/admin/concerts/${id}/cancel`, { reason })).data;
}
export async function createCatalogZone(id: string, input: ZoneInput) {
  return (await apiPost<ApiResponse<CatalogZone>>(`/admin/concerts/${id}/seat-zones`, input)).data;
}
export async function patchCatalogZone(id: string, input: Partial<Omit<ZoneInput, "code">>) {
  return (await apiPatch<ApiResponse<CatalogZone>>(`/admin/seat-zones/${id}`, input)).data;
}
export async function createCatalogTicket(id: string, input: TicketInput) {
  return (await apiPost<ApiResponse<CatalogTicket>>(`/admin/concerts/${id}/ticket-types`, input)).data;
}
export async function patchCatalogTicket(id: string, input: TicketPatch) {
  return (await apiPatch<ApiResponse<CatalogTicket>>(`/admin/ticket-types/${id}`, input)).data;
}
export async function uploadCatalogBio(id: string, file: File) {
  if (!file.size || file.type !== "application/pdf") throw new Error("Chọn file PDF không rỗng.");
  if (file.size > 10 * 1024 * 1024) throw new Error("PDF không được vượt quá 10 MiB.");
  const form = new FormData();
  form.append("file", file);
  return (await apiPostFormData<ApiResponse<BioJob>>(`/admin/concerts/${id}/artist-bio-jobs`, form)).data;
}
export async function getCatalogBio(id: string, jobId: string) {
  return (await apiGet<ApiResponse<BioJob>>(`/admin/concerts/${id}/artist-bio-jobs/${jobId}`)).data;
}

// The editor supplies only these fields. Empty optional text means an explicit clear.
export function changedConcertFields(before: ConcertPatch, after: ConcertPatch): ConcertPatch {
  return Object.fromEntries(Object.entries(after)
    .filter(([key, value]) => before[key as keyof ConcertPatch] !== value)
    .map(([key, value]) => [key, value === "" && ["description", "artist_bio", "cover_image_url", "seat_map_url"].includes(key) ? null : value]));
}

export function pollCatalogBio(concertId: string, jobId: string, onJob: (job: BioJob) => void, onError: (error: unknown) => void) {
  let stopped = false;
  let timer: ReturnType<typeof setTimeout>;
  async function poll() {
    try {
      const job = await getCatalogBio(concertId, jobId);
      if (stopped) return;
      onJob(job);
      if (job.status === "PENDING" || job.status === "PROCESSING") timer = setTimeout(poll, 3000);
    } catch (error) { if (!stopped) onError(error); }
  }
  void poll();
  return () => { stopped = true; clearTimeout(timer); };
}
