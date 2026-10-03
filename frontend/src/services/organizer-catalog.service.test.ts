import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as catalog from "./organizer-catalog.service";
import { toOrganizerConcert, toOrganizerTicket, createOrganizerTicketType, getOrganizerConcert } from "./organizer.service";

const concert: catalog.CatalogConcert = {
  id: "concert-1", title: "Live", slug: "live", venue: "Hanoi", artist_name: "Artist",
  status: "DRAFT", description: null, artist_bio: null, cover_image_url: null, seat_map_url: null,
  starts_at: "2026-12-10T12:00:00Z", ends_at: "2026-12-10T15:00:00Z",
};
const ticket: catalog.CatalogTicket = {
  id: "ticket-1", concert_id: concert.id, seat_zone_id: "zone-1", zone_code: "VIP", name: "VIP",
  price: 120000, currency: "VND", total_quantity: 50, held_quantity: 2, sold_quantity: 5,
  available_quantity: 43, max_per_user: 4, sale_start_at: concert.starts_at, sale_end_at: concert.ends_at, status: "DRAFT",
};
const fetchMock = vi.fn();
function reply(data: unknown) { fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data, pagination: { has_more: true }, meta: {} }), { status: 200 })); }
function request() {
  const [url, init] = fetchMock.mock.calls.at(-1)! as [string, RequestInit];
  return { url, init, body: typeof init.body === "string" ? JSON.parse(init.body) : init.body };
}
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("localStorage", { getItem: () => JSON.stringify({ accessToken: "test-token", expiresAt: Date.now() + 60000, user: { id: "owner" } }) });
});
afterEach(() => vi.unstubAllGlobals());

describe("Organizer catalog contract", () => {
  it("lists with supported pagination/filter fields and keeps pagination metadata", async () => {
    reply([concert]);
    const result = await catalog.listCatalogConcerts({ page: 2, status: "DRAFT", q: " live " });
    const url = new URL(request().url, "http://local");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "2", size: "20", sortBy: "createdAt", sortOrder: "desc", q: "live", status: "DRAFT" });
    expect(result.pagination.has_more).toBe(true);
    expect(new Headers(request().init.headers).get("Authorization")).toBe("Bearer test-token");
  });
  it("loads metadata by ID without public/list requests", async () => {
    reply({ concert, seat_zones: [], ticket_types: [ticket], seat_map: {}, artist_bio: null });
    const result = await getOrganizerConcert(concert.id);
    expect(request().url).toBe("/admin/concerts/concert-1/metadata");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(result.venue).toBe("Hanoi");
    expect(result.ticket_types[0].price.amount).toBe(120000);
  });
  it("creates a concert and patches only supplied fields, preserving explicit clearing", async () => {
    reply(concert);
    await catalog.createCatalogConcert({ title: "Live", slug: "live", venue: "Hanoi", artist_name: "Artist", starts_at: concert.starts_at, ends_at: concert.ends_at });
    expect(request().init.method).toBe("POST");
    expect(request().body.venue).toBe("Hanoi");
    expect(request().body).not.toHaveProperty("venue_id");
    reply(concert);
    await catalog.patchCatalogConcert(concert.id, { description: null });
    expect(request().init.method).toBe("PATCH");
    expect(request().body).toEqual({ description: null });
  });
  it("converts existing Organizer price objects into numeric catalog prices", async () => {
    reply(ticket);
    await createOrganizerTicketType(concert.id, { seat_zone_id: "zone-1", name: "VIP", price: { amount: 120000, currency: "VND" }, total_quantity: 50, max_per_user: 4, sale_start_at: concert.starts_at, sale_end_at: concert.ends_at });
    expect(request().url).toBe("/admin/concerts/concert-1/ticket-types");
    expect(request().body.price).toBe(120000);
    expect(request().body.currency).toBe("VND");
    expect(toOrganizerTicket(ticket).available_quantity).toBe(43);
  });
  it("patches zone and ticket by their own IDs with the required ticket name", async () => {
    reply({}); await catalog.patchCatalogZone("zone-1", { capacity: 60 });
    expect(request().url).toBe("/admin/seat-zones/zone-1");
    expect(request().body).toEqual({ capacity: 60 });
    reply(ticket); await catalog.patchCatalogTicket("ticket-1", { name: "VIP", total_quantity: 45, status: "CLOSED" });
    expect(request().url).toBe("/admin/ticket-types/ticket-1");
    expect(request().init.method).toBe("PATCH");
    expect(request().body).toEqual({ name: "VIP", total_quantity: 45, status: "CLOSED" });
  });
  it("publishes and cancels directly, normalizing the returned canceled status", async () => {
    reply(concert); await catalog.publishCatalogConcert(concert.id);
    expect(request().url).toBe("/admin/concerts/concert-1/publish");
    reply({ ...concert, status: "CANCELED" });
    const canceled = await catalog.cancelCatalogConcert(concert.id, "Weather");
    expect(request().url).toBe("/admin/concerts/concert-1/cancel");
    expect(request().body).toEqual({ reason: "Weather" });
    expect(toOrganizerConcert(canceled).status).toBe("CANCELLED");
  });
  it("uploads multipart with file part and without forcing a content-type boundary", async () => {
    reply({ id: "job-1", status: "PENDING" });
    await catalog.uploadCatalogBio(concert.id, new File(["%PDF-1.4"], "bio.pdf", { type: "application/pdf" }));
    expect(request().body).toBeInstanceOf(FormData);
    expect((request().body as FormData).get("file")).toBeInstanceOf(File);
    expect(new Headers(request().init.headers).has("Content-Type")).toBe(false);
  });
  it("rejects empty, non-PDF and oversized uploads without a request", async () => {
    for (const file of [new File([], "empty.pdf", { type: "application/pdf" }), new File(["hello"], "text.txt", { type: "text/plain" }), new File([new Uint8Array(10 * 1024 * 1024 + 1)], "big.pdf", { type: "application/pdf" })]) {
      await expect(catalog.uploadCatalogBio(concert.id, file)).rejects.toThrow();
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("propagates backend validation errors rather than returning success", async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ code: "ZONE_CAPACITY_EXCEEDED", detail: "Capacity exceeded" }), { status: 409 }));
    await expect(catalog.patchCatalogZone("zone-1", { capacity: 1 })).rejects.toMatchObject({ status: 409, code: "ZONE_CAPACITY_EXCEEDED" });
  });
});

describe("partial edits and bio polling lifecycle", () => {
  afterEach(() => vi.useRealTimers());
  it("only sends modified concert fields and clears optional text with null", () => {
    const before = { title: "Live", description: "Old", venue: "Hanoi", starts_at: concert.starts_at };
    expect(catalog.changedConcertFields(before, { ...before, description: "" })).toEqual({ description: null });
    expect(catalog.changedConcertFields(before, before)).toEqual({});
  });
  it("polls serially every 3 seconds and stops on DONE", async () => {
    vi.useFakeTimers();
    reply({ id: "job", status: "PROCESSING" });
    reply({ id: "job", status: "DONE", generated_bio: "Bio" });
    const onJob = vi.fn(); const onError = vi.fn();
    const stop = catalog.pollCatalogBio(concert.id, "job", onJob, onError);
    await vi.advanceTimersByTimeAsync(0);
    expect(onJob).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(2999);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(onJob).toHaveBeenLastCalledWith(expect.objectContaining({ status: "DONE" }));
    await vi.advanceTimersByTimeAsync(9000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(onError).not.toHaveBeenCalled(); stop();
  });
  it("does not start overlapping requests or deliver an in-flight result after cleanup", async () => {
    vi.useFakeTimers();
    let resolve!: (response: Response) => void;
    fetchMock.mockReturnValueOnce(new Promise<Response>(done => { resolve = done; }));
    const onJob = vi.fn(); const stop = catalog.pollCatalogBio(concert.id, "job", onJob, vi.fn());
    await vi.advanceTimersByTimeAsync(10000);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    stop(); resolve(new Response(JSON.stringify({ data: { status: "PROCESSING" } })));
    await vi.advanceTimersByTimeAsync(10000);
    expect(onJob).not.toHaveBeenCalled(); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it("stops after failure and can resume from a saved job ID without uploading again", async () => {
    vi.useFakeTimers();
    fetchMock.mockRejectedValueOnce(new Error("Offline"));
    const onError = vi.fn(); const stop = catalog.pollCatalogBio(concert.id, "saved-job", vi.fn(), onError);
    await vi.advanceTimersByTimeAsync(10000);
    expect(onError).toHaveBeenCalledOnce(); expect(fetchMock).toHaveBeenCalledTimes(1); stop();
    reply({ id: "saved-job", status: "FAILED", error_message: "Invalid PDF" });
    const onJob = vi.fn(); const stopRetry = catalog.pollCatalogBio(concert.id, "saved-job", onJob, onError);
    await vi.advanceTimersByTimeAsync(10000);
    expect(request().url).toBe("/admin/concerts/concert-1/artist-bio-jobs/saved-job");
    expect(request().init.method).toBe("GET");
    expect(fetchMock).toHaveBeenCalledTimes(2); expect(onJob).toHaveBeenCalledOnce(); stopRetry();
  });
});
