"use client";

import { useEffect, useState } from "react";
import Icone from "@/components/Icone";
import { api, type Impacto } from "@/lib/api";
import { UNIDADES } from "@/lib/formato";

/** Indicadores de impacto (UC06 passo 11): retiradas concluídas e quantidade resgatada por unidade. */
export function useImpacto() {
  const [impacto, setImpacto] = useState<{ plataforma: Impacto; meu: Impacto }>();
  useEffect(() => {
    api<{ plataforma: Impacto; meu: Impacto }>("/impacto").then(setImpacto, () => {});
  }, []);
  return impacto;
}

export default function CartaoImpacto({ titulo, impacto }: { titulo: string; impacto?: Impacto }) {
  if (!impacto) return null;
  const quantidades = Object.entries(impacto.quantidades);
  return (
    <section className="impacto">
      <p className="impacto-titulo"><Icone nome="folha" tamanho={16} /> {titulo}</p>
      <div className="impacto-numero">{impacto.retiradas}<span> {impacto.retiradas === 1 ? "retirada concluída" : "retiradas concluídas"}</span></div>
      {quantidades.length > 0 ? (
        <div className="impacto-qtd">
          {quantidades.map(([u, q]) => <span key={u}><strong>{q}</strong> {UNIDADES[u]}</span>)}
          <span>de comida resgatada</span>
        </div>
      ) : (
        <p className="impacto-vazio">Ainda não há retiradas concluídas.</p>
      )}
    </section>
  );
}
