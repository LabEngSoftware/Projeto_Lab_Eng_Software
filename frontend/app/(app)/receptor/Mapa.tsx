"use client";

import "leaflet/dist/leaflet.css";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import type { Coordenadas, Lote } from "@/lib/api";
import { CATEGORIAS, preco } from "@/lib/formato";

const CORES = { ALTA: "#c0362c", MEDIA: "#f08a24", BAIXA: "#17694a" };

/** Lotes no mapa (OpenStreetMap), coloridos pela urgência; clicar abre o detalhe. */
export default function Mapa({ lotes, centro }: { lotes: Lote[]; centro?: Coordenadas | null }) {
  const div = useRef<HTMLDivElement>(null);
  const router = useRouter();

  useEffect(() => {
    let mapa: import("leaflet").Map | undefined;
    let cancelado = false;
    // o Leaflet usa window: carregado só no navegador
    import("leaflet").then((L) => {
      if (cancelado || !div.current) return;
      mapa = L.map(div.current);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      }).addTo(mapa);

      const pontos: [number, number][] = [];
      if (centro) {
        pontos.push([centro.latitude, centro.longitude]);
        L.circleMarker(pontos[0], { radius: 8, color: "#fff", weight: 3, fillColor: "#2563eb", fillOpacity: 1 })
          .addTo(mapa).bindTooltip("Você");
      }
      for (const l of lotes) {
        if (!l.coordenadas) continue;
        const p: [number, number] = [l.coordenadas.latitude, l.coordenadas.longitude];
        pontos.push(p);
        L.circleMarker(p, { radius: 12, color: "#fff", weight: 3, fillColor: CORES[l.urgencia], fillOpacity: 1 })
          .addTo(mapa)
          .bindTooltip(`${CATEGORIAS[l.categoria].emoji} ${l.nome} · ${preco(l.preco)}`, { direction: "top", offset: [0, -10] })
          .on("click", () => router.push(`/receptor/lotes/${l.id}`));
      }
      if (pontos.length) mapa.fitBounds(pontos, { maxZoom: 15, padding: [48, 48] });
      else mapa.setView([-23.55, -46.63], 12);
    });
    return () => {
      cancelado = true;
      mapa?.remove();
    };
  }, [lotes, centro, router]);

  return <div ref={div} className="mapa" />;
}
