"use client";

import { LeafletProvider, createLeafletContext, type LeafletContextInterface } from "@react-leaflet/core";
import L, { Map as LeafletMap, type LatLngExpression, type LeafletMouseEvent } from "leaflet";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Circle, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import { cn } from "@/lib/utils";

export type LocationCoordinate = {
  latitude: number;
  longitude: number;
};

type LocationMapPickerProps = {
  center: LocationCoordinate;
  value?: LocationCoordinate | null;
  radiusMeters: number;
  onChange: (coordinate: LocationCoordinate) => void;
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
  className,
}: LocationMapPickerProps) {
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

  return (
    <div className={cn("space-y-3", className)}>
      <div className="h-[320px] min-w-0 overflow-hidden rounded-md border bg-muted sm:h-[360px] lg:h-[400px]">
        <SafeMapContainer center={mapCenter} zoom={16} scrollWheelZoom className="h-full w-full">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <ClickHandler onChange={onChange} />
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
