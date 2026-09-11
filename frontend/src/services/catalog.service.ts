import {
  getConcert,
  getConcertMetadata,
  getInventory,
  getMyTicketQuota,
  listConcerts,
  type ConcertDetail,
  type ConcertMetadata,
  type Inventory,
  type TicketQuota,
} from "../lib/api-client";
import {
  mapDetailConcert,
  mapSummaryConcert,
  type UiConcert,
} from "../lib/catalog-ui";

export type LoadEventsInput = {
  search?: string;
  city?: string;
  page?: number;
  size?: number;
};

export async function getHomeCatalogConcerts(): Promise<UiConcert[]> {
  const concerts = await listConcerts({ sortBy: "startsAt", sortOrder: "asc", size: 20 });
  return concerts.map(mapSummaryConcert);
}

export async function getEventsCatalog(input: LoadEventsInput = {}): Promise<UiConcert[]> {
  const concerts = await listConcerts({
    q: input.search ?? "",
    city: input.city === "all" ? "" : input.city ?? "",
    sortBy: "startsAt",
    sortOrder: "asc",
    page: input.page ?? 0,
    size: input.size ?? 50,
  });

  return concerts.map(mapSummaryConcert);
}

export async function getCatalogConcertDetail(concertId: string): Promise<UiConcert> {
  const concert = await getConcert(concertId);
  const [metadataResult, inventoryResult] = await Promise.allSettled([
    getConcertMetadata(concert.id),
    getInventory(concert.id),
  ]);
  const metadata =
    metadataResult.status === "fulfilled"
      ? metadataResult.value
      : emptyMetadata(concert);
  const inventory =
    inventoryResult.status === "fulfilled"
      ? inventoryResult.value
      : emptyInventory(concert.id);

  return mapDetailConcert(concert, metadata, inventory);
}

export async function getCatalogTicketQuota(concertId: string): Promise<TicketQuota> {
  return getMyTicketQuota(concertId);
}

function emptyMetadata(concert: ConcertDetail): ConcertMetadata {
  return {
    concert,
    seat_zones: [],
    ticket_types: [],
    seat_map: {
      svg_url: concert.seat_map_url,
    },
    artist_bio: concert.artist_bio,
  };
}

function emptyInventory(concertId: string): Inventory {
  return {
    concert_id: concertId,
    as_of: new Date().toISOString(),
    items: [],
  };
}

const POPULAR_CITIES = ["Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng"];

export function getCityFilters(concerts: UiConcert[], allLabel = "All") {
  const found = new Set<string>();
  for (const concert of concerts) {
    for (const city of POPULAR_CITIES) {
      if (concert.venue.toLowerCase().includes(city.toLowerCase())) {
        found.add(city);
      }
    }
  }
  const list = found.size > 0 ? Array.from(found) : POPULAR_CITIES;
  return [allLabel, ...list];
}

export function filterHomeConcerts(
  concerts: UiConcert[],
  input: { searchQuery: string; activeFilter: string; allLabel?: string },
) {
  const allLabel = input.allLabel ?? "All";
  const query = input.searchQuery.trim().toLowerCase();

  return concerts.filter((concert) => {
    const matchesSearch =
      !query ||
      concert.title.toLowerCase().includes(query) ||
      concert.artistName.toLowerCase().includes(query) ||
      concert.venue.toLowerCase().includes(query);
    const matchesFilter =
      input.activeFilter === allLabel ||
      concert.venue.toLowerCase().includes(input.activeFilter.toLowerCase());

    return matchesSearch && matchesFilter;
  });
}
