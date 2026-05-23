export type PlaceSearchResult = {
  displayName: string;
  latitude: number;
  longitude: number;
  type?: string;
  category?: string;
};

type SearchPlacesOptions = {
  signal?: AbortSignal;
  language?: string;
};

type NominatimSearchItem = {
  display_name?: string;
  lat?: string;
  lon?: string;
  type?: string;
  class?: string;
};

const nominatimSearchUrl = "https://nominatim.openstreetmap.org/search";

export async function searchPlaces(query: string, options: SearchPlacesOptions = {}): Promise<PlaceSearchResult[]> {
  const keyword = query.trim();
  if (keyword.length < 3) return [];

  const params = new URLSearchParams({
    q: keyword,
    format: "json",
    limit: "5",
    addressdetails: "1",
  });

  if (options.language) {
    params.set("accept-language", options.language);
  }

  const response = await fetch(`${nominatimSearchUrl}?${params.toString()}`, {
    signal: options.signal,
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error("Pencarian lokasi sedang tidak tersedia. Anda tetap bisa memilih titik langsung dari peta.");
  }

  const items = (await response.json()) as NominatimSearchItem[];
  return items.map(toPlaceSearchResult).filter((item): item is PlaceSearchResult => item != null);
}

function toPlaceSearchResult(item: NominatimSearchItem): PlaceSearchResult | null {
  const latitude = Number(item.lat);
  const longitude = Number(item.lon);

  if (!item.display_name || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null;
  }

  return {
    displayName: item.display_name,
    latitude,
    longitude,
    type: item.type,
    category: item.class,
  };
}
