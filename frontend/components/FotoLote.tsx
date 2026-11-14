import type { Lote } from "@/lib/api";
import { CATEGORIAS } from "@/lib/formato";

/** Foto do lote ou, sem foto, o emoji da categoria sobre a cor dela. */
export default function FotoLote({ lote, className }: { lote: Lote; className: string }) {
  const c = CATEGORIAS[lote.categoria];
  return (
    <div className={className} style={{ background: c.fundo }}>
      {/* eslint-disable-next-line @next/next/no-img-element -- foto servida pelo backend via /api */}
      {lote.fotoUrl ? <img src={`/api${lote.fotoUrl}`} alt={lote.nome} /> : <span aria-hidden>{c.emoji}</span>}
    </div>
  );
}
