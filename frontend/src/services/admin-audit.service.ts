import { apiGet, type ApiCollectionResponse } from "../lib/api-client";

export type AdminAuditActor = {
  id: string;
  email: string;
  full_name: string;
  role: string;
} | null;

export type AdminAuditLog = {
  id: string;
  actor_user_id: string | null;
  actor: AdminAuditActor;
  action: string;
  entity_type: string;
  entity_id: string | null;
  metadata: Record<string, unknown> | null;
  ip_address: string | null;
  user_agent: string | null;
  created_at: string;
  createdAt?: string;
  entityType?: string;
  entityId?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
};

export type AdminAuditFilter = {
  action?: string;
  entity_type?: string;
  entity_id?: string;
  actor_user_id?: string;
  from?: string;
  to?: string;
};

export interface RawAuditLogResponse {
  id: string;
  actor?: {
    id: string;
    email: string;
    full_name?: string;
    fullName?: string;
    role?: string;
  } | null;
  action: string;
  entity_type?: string;
  entityType?: string;
  entity_id?: string | null;
  entityId?: string | null;
  metadata?: Record<string, unknown> | null;
  ip_address?: string | null;
  ipAddress?: string | null;
  user_agent?: string | null;
  userAgent?: string | null;
  created_at?: string;
  createdAt?: string;
  actor_user_id?: string | null;
  actorUserId?: string | null;
}

function normalizeAuditLog(raw: RawAuditLogResponse): AdminAuditLog {
  const actorRaw = raw.actor;
  const actor: AdminAuditActor = actorRaw
    ? {
        id: actorRaw.id,
        email: actorRaw.email,
        full_name: actorRaw.full_name || actorRaw.fullName || "",
        role: actorRaw.role || "",
      }
    : null;

  const entityType = raw.entity_type ?? raw.entityType ?? "";
  const entityId = raw.entity_id ?? raw.entityId ?? null;
  const ipAddress = raw.ip_address ?? raw.ipAddress ?? null;
  const userAgent = raw.user_agent ?? raw.userAgent ?? null;
  const createdAt = raw.created_at ?? raw.createdAt ?? "";

  return {
    id: raw.id,
    actor_user_id: raw.actor_user_id ?? raw.actorUserId ?? actor?.id ?? null,
    actor,
    action: raw.action,
    entity_type: entityType,
    entity_id: entityId,
    metadata: raw.metadata ?? null,
    ip_address: ipAddress,
    user_agent: userAgent,
    created_at: createdAt,
    createdAt,
    entityType,
    entityId,
    ipAddress,
    userAgent,
  };
}

export async function listAdminAuditLogs(
  filter: AdminAuditFilter,
  cursor?: string,
  limit = 30,
): Promise<{ items: AdminAuditLog[]; nextCursor: string | null }> {
  const query = new URLSearchParams({
    limit: String(limit),
    size: String(limit),
  });

  if (filter.action?.trim()) query.set("action", filter.action.trim());
  if (filter.entity_type?.trim()) {
    query.set("entity_type", filter.entity_type.trim());
    query.set("entityType", filter.entity_type.trim());
  }
  if (filter.entity_id?.trim()) {
    query.set("entity_id", filter.entity_id.trim());
    query.set("entityId", filter.entity_id.trim());
  }
  if (filter.actor_user_id?.trim()) {
    query.set("actor_user_id", filter.actor_user_id.trim());
    query.set("actorUserId", filter.actor_user_id.trim());
  }
  if (filter.from?.trim()) query.set("from", filter.from.trim());
  if (filter.to?.trim()) query.set("to", filter.to.trim());

  if (cursor) {
    query.set("cursor", cursor);
    query.set("page", cursor);
  }

  const response = await apiGet<ApiCollectionResponse<RawAuditLogResponse>>(
    `/admin/audit-logs?${query.toString()}`,
  );

  const rawItems = Array.isArray(response.data) ? response.data : [];
  const items = rawItems.map(normalizeAuditLog);

  const pagination = response.pagination;
  const nextCursor =
    pagination?.next_cursor ??
    (pagination?.has_more && typeof pagination.page === "number"
      ? String(pagination.page + 1)
      : null);

  return {
    items,
    nextCursor,
  };
}
