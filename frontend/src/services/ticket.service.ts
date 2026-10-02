import { apiGet, type ApiCollectionResponse, type ApiResponse } from "../lib/api-client";

export type TicketStatus = "ISSUED" | "CHECKED_IN" | "CANCELLED" | "EXPIRED";

export type TicketListItem = {
  id: string;
  status: TicketStatus;
  issuedAt: string;
  concert: {
    id: string;
    title: string;
    artistName: string;
    venue: string;
    startsAt: string;
    endsAt: string;
    coverImageUrl: string | null;
  };
  ticketType: {
    id: string;
    name: string;
    price: number;
    currency: string;
  };
  seatZone: {
    id: string;
    code: string;
    name: string;
  };
  qr: {
    available: boolean;
    displayUrl: string | null;
  };
};

export type TicketQr = {
  ticketId: string;
  content: string;
};

export type TicketErrorCode = "TICKET_NOT_FOUND" | "TICKET_QR_UNAVAILABLE";

export function getTicketErrorMessage(code?: string) {
  if (code === "TICKET_NOT_FOUND") return "Không tìm thấy vé này.";
  if (code === "TICKET_QR_UNAVAILABLE") return "Mã QR không còn khả dụng cho vé này.";
  return "Không thể tải mã QR. Vui lòng thử lại sau.";
}

export async function listMyTickets() {
  const pageSize = 100;
  const tickets: TicketListItem[] = [];
  let page = 0;
  let hasMore = true;

  while (hasMore) {
    const response = await apiGet<ApiCollectionResponse<TicketListItem>>(
      `/my-tickets?page=${page}&size=${pageSize}`,
    );
    tickets.push(...response.data);
    hasMore = response.pagination.has_more ?? false;
    page += 1;
  }

  return tickets;
}

export async function getMyTicketQr(ticketId: string) {
  const response = await apiGet<ApiResponse<TicketQr>>(`/my-tickets/${ticketId}/qr`);
  return response.data;
}
