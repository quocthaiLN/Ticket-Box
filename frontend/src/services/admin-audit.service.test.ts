import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listAdminAuditLogs } from "./admin-audit.service";

const fetchMock = vi.fn();

function reply(data: unknown, pagination?: unknown) {
  fetchMock.mockResolvedValueOnce(
    new Response(
      JSON.stringify({
        data,
        pagination: pagination ?? { page: 0, page_size: 20, total_items: 1, total_pages: 1, has_more: false },
        meta: { request_id: "req_test" },
      }),
      { status: 200 },
    ),
  );
}

function getRequest() {
  const [url, init] = fetchMock.mock.calls.at(-1)! as [string, RequestInit];
  return { url, init };
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("localStorage", {
    getItem: () =>
      JSON.stringify({
        accessToken: "test-token",
        expiresAt: Date.now() + 60000,
        user: { id: "admin-1", role: "ADMIN" },
      }),
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("admin-audit.service", () => {
  it("normalizes camelCase backend response to snake_case and preserves fields", async () => {
    reply([
      {
        id: "log-1",
        actor: {
          id: "u-1",
          email: "admin@ticketbox.vn",
          fullName: "Admin User",
        },
        action: "UPDATE_CONCERT",
        entityType: "CONCERT",
        entityId: "concert-123",
        ipAddress: "127.0.0.1",
        userAgent: "Chrome/120.0",
        createdAt: "2026-10-14T01:00:00",
      },
    ]);

    const result = await listAdminAuditLogs({});
    expect(result.items).toHaveLength(1);
    const item = result.items[0];

    expect(item.id).toBe("log-1");
    expect(item.created_at).toBe("2026-10-14T01:00:00");
    expect(item.entity_type).toBe("CONCERT");
    expect(item.entity_id).toBe("concert-123");
    expect(item.ip_address).toBe("127.0.0.1");
    expect(item.user_agent).toBe("Chrome/120.0");
    expect(item.actor?.full_name).toBe("Admin User");
    expect(item.actor?.email).toBe("admin@ticketbox.vn");
  });

  it("handles null actor and missing optional fields safely", async () => {
    reply([
      {
        id: "log-2",
        actor: null,
        action: "SYSTEM_JOB",
      },
    ]);

    const result = await listAdminAuditLogs({});
    expect(result.items).toHaveLength(1);
    const item = result.items[0];

    expect(item.id).toBe("log-2");
    expect(item.actor).toBeNull();
    expect(item.created_at).toBe("");
    expect(item.entity_type).toBe("");
    expect(item.entity_id).toBeNull();
  });

  it("maps query filters with both casing options and supports pagination", async () => {
    reply([], { page: 1, page_size: 15, has_more: true });

    const result = await listAdminAuditLogs(
      {
        action: "LOGIN",
        entity_type: "USER",
        entity_id: "u-99",
        actor_user_id: "admin-1",
      },
      "1",
      15,
    );

    const { url } = getRequest();
    const search = new URL(url, "http://localhost").searchParams;

    expect(search.get("action")).toBe("LOGIN");
    expect(search.get("entity_type")).toBe("USER");
    expect(search.get("entityType")).toBe("USER");
    expect(search.get("entity_id")).toBe("u-99");
    expect(search.get("entityId")).toBe("u-99");
    expect(search.get("actor_user_id")).toBe("admin-1");
    expect(search.get("actorUserId")).toBe("admin-1");
    expect(search.get("size")).toBe("15");
    expect(search.get("limit")).toBe("15");
    expect(search.get("page")).toBe("1");
    expect(result.nextCursor).toBe("2");
  });
});
