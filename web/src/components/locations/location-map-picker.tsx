"use client";

import { LeafletProvider, createLeafletContext, type LeafletContextInterface } from "@react-leaflet/core";
import L, { Map as LeafletMap, type LatLngExpression, type LeafletMouseEvent } from "leaflet";
import { Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { Circle, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import { cn } from "@/lib/utils";
import { searchPlaces, type PlaceSearchResult } from "@/lib/map/nominatim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export type LocationCoordinate = {
  latitude: number;
  longitude: number;
};

type LocationMapPickerProps = {
  center: LocationCoordinate;
  value?: LocationCoordinate | null;
  radiusMeters: number;
  onChange: (coordinate: LocationCoordinate) => void;
  onPlaceSelect?: (place: PlaceSearchResult) => void;
  className?: string;
};

const jakartaCenter: LocationCoordinate = {
  latitude: -6.2,
  longitude: 106.816666,
};

const markerIcon = L.divIcon({
  className: "hadivo-location-marker",
  html: "<span></span>",
  iconSize: [28, 28],
  iconAnchor: [14, 28],
});

export function LocationMapPicker({
  center,
  value,
  radiusMeters,
  onChange,
  onPlaceSelect,
  className,
}: LocationMapPickerProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PlaceSearchResult[]>([]);
  const [searchStatus, setSearchStatus] = useState<"idle" | "loading" | "success" | "empty" | "error">("idle");
  const [searchMessage, setSearchMessage] = useState("");
  const [selectedPlaceMessage, setSelectedPlaceMessage] = useState("");
  const requestRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);
  const selectedPosition = useMemo<LatLngExpression | null>(() => {
    if (!value) return null;
    return [value.latitude, value.longitude];
  }, [value]);

  const mapCenter = useMemo<LatLngExpression>(() => {
    const coordinate = value ?? center ?? jakartaCenter;
    return [coordinate.latitude, coordinate.longitude];
  }, [center, value]);

  const coordinateText = value
    ? `${value.latitude.toFixed(6)}, ${value.longitude.toFixed(6)}`
    : "Belum ada titik dipilih";

  const visibleRadius = Number.isFinite(radiusMeters) && radiusMeters > 0 ? radiusMeters : 100;

  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  async function handleSearch() {
    const keyword = query.trim();
    setSelectedPlaceMessage("");

    if (keyword.length < 3) {
      abortRef.current?.abort();
      setResults([]);
      setSearchStatus("idle");
      setSearchMessage("Masukkan minimal 3 karakter untuk mencari lokasi.");
      return;
    }

    abortRef.current?.abort();
    const requestId = requestRef.current + 1;
    const controller = new AbortController();
    requestRef.current = requestId;
    abortRef.current = controller;
    setSearchStatus("loading");
    setSearchMessage("");

    try {
      const places = await searchPlaces(keyword, {
        signal: controller.signal,
        language: typeof navigator !== "undefined" ? navigator.language : undefined,
      });

      if (requestRef.current !== requestId) return;
      setResults(places);
      setSearchStatus(places.length ? "success" : "empty");
      setSearchMessage(places.length ? "" : "Lokasi tidak ditemukan. Coba kata kunci lain.");
    } catch (error) {
      if (controller.signal.aborted) return;
      if (requestRef.current !== requestId) return;
      setResults([]);
      setSearchStatus("error");
      setSearchMessage(
        error instanceof Error
          ? error.message
          : "Pencarian lokasi sedang tidak tersedia. Anda tetap bisa memilih titik langsung dari peta.",
      );
    }
  }

  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== "Enter") return;
    event.preventDefault();
    if (searchStatus === "loading") return;
    void handleSearch();
  }

  function selectPlace(place: PlaceSearchResult) {
    const coordinate = {
      latitude: roundCoordinate(place.latitude),
      longitude: roundCoordinate(place.longitude),
    };

    onChange(coordinate);
    onPlaceSelect?.(place);
    setResults([]);
    setSearchStatus("idle");
    setSearchMessage("");
    setQuery(place.displayName);
    setSelectedPlaceMessage("Lokasi dipilih dari hasil pencarian. Silakan cek titik marker dan radius sebelum menyimpan.");
  }

  return (
    <div className={cn("space-y-3", className)}>
      <div className="space-y-2">
        <div className="flex flex-col gap-2 sm:flex-row">
          <Input
            type="search"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setSelectedPlaceMessage("");
            }}
            placeholder="Cari nama tempat atau alamat..."
            aria-label="Cari nama tempat atau alamat"
            autoComplete="off"
            onKeyDown={handleSearchKeyDown}
          />
          <Button type="button" className="shrink-0" disabled={searchStatus === "loading"} onClick={() => void handleSearch()}>
            <Search className="mr-2 h-4 w-4" />
            {searchStatus === "loading" ? "Mencari..." : "Cari Lokasi"}
          </Button>
        </div>
        {searchMessage ? <p className="text-sm text-muted-foreground">{searchMessage}</p> : null}
        {selectedPlaceMessage ? <p className="text-sm text-emerald-700">{selectedPlaceMessage}</p> : null}
        {results.length ? (
          <div className="max-h-64 overflow-y-auto rounded-md border bg-background">
            {results.map((place) => (
              <button
                key={`${place.latitude}-${place.longitude}-${place.displayName}`}
                type="button"
                className="block w-full border-b px-3 py-3 text-left text-sm last:border-b-0 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                onClick={() => selectPlace(place)}
              >
                <span className="block font-medium">{place.displayName}</span>
                {place.category || place.type ? (
                  <span className="mt-1 block text-xs text-muted-foreground">
                    {[place.category, place.type].filter(Boolean).join(" - ")}
                  </span>
                ) : null}
              </button>
            ))}
          </div>
        ) : null}
      </div>
      <div className="h-[320px] min-w-0 overflow-hidden rounded-md border bg-muted sm:h-[360px] lg:h-[400px]">
        <SafeMapContainer center={mapCenter} zoom={16} scrollWheelZoom className="h-full w-full">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <ClickHandler
            onChange={(coordinate) => {
              setSelectedPlaceMessage("");
              onChange(coordinate);
            }}
          />
          <MapSync center={mapCenter} selectedPosition={selectedPosition} />
          {selectedPosition ? (
            <>
              <Circle
                center={selectedPosition}
                radius={visibleRadius}
                pathOptions={{
                  color: "hsl(var(--primary))",
                  fillColor: "hsl(var(--primary))",
                  fillOpacity: 0.12,
                  weight: 2,
                }}
              />
              <Marker position={selectedPosition} icon={markerIcon} />
            </>
          ) : null}
        </SafeMapContainer>
      </div>
      <div className="flex flex-col gap-1 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
        <p>Klik peta untuk memilih titik absensi.</p>
        <p className="font-medium text-foreground">Titik dipilih: {coordinateText}</p>
      </div>
    </div>
  );
}

function SafeMapContainer({
  center,
  zoom,
  scrollWheelZoom,
  className,
  children,
}: {
  center: LatLngExpression;
  zoom: number;
  scrollWheelZoom?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const mapRef = useRef<LeafletMap | null>(null);
  const initialViewRef = useRef({ center, zoom, scrollWheelZoom });
  const [context, setContext] = useState<LeafletContextInterface | null>(null);

  const setContainerRef = useCallback((node: HTMLDivElement | null) => {
    if (!node || mapRef.current) return;

    const initialView = initialViewRef.current;
    const map = new LeafletMap(node, {
      scrollWheelZoom: initialView.scrollWheelZoom,
    });

    mapRef.current = map;
    map.setView(initialView.center, initialView.zoom);
    setContext(createLeafletContext(map));
  }, []);

  useEffect(() => {
    return () => {
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  return (
    <div ref={setContainerRef} className={className}>
      {context ? <LeafletProvider value={context}>{children}</LeafletProvider> : null}
    </div>
  );
}

function ClickHandler({ onChange }: { onChange: (coordinate: LocationCoordinate) => void }) {
  useMapEvents({
    click(event: LeafletMouseEvent) {
      onChange({
        latitude: roundCoordinate(event.latlng.lat),
        longitude: roundCoordinate(event.latlng.lng),
      });
    },
  });

  return null;
}

function MapSync({
  center,
  selectedPosition,
}: {
  center: LatLngExpression;
  selectedPosition: LatLngExpression | null;
}) {
  const map = useMap();

  useEffect(() => {
    const timeout = window.setTimeout(() => map.invalidateSize(), 0);
    return () => window.clearTimeout(timeout);
  }, [map]);

  useEffect(() => {
    map.setView(selectedPosition ?? center, selectedPosition ? Math.max(map.getZoom(), 16) : map.getZoom(), {
      animate: true,
    });
  }, [center, map, selectedPosition]);

  return null;
}

function roundCoordinate(value: number) {
  return Number(value.toFixed(6));
}
