"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import CartaoImpacto, { useImpacto } from "@/components/CartaoImpacto";
import FotoLote from "@/components/FotoLote";
import Icone from "@/components/Icone";
import { api, type Pagina, type Reserva } from "@/lib/api";
import { dataHora, preco, STATUS_RESERVA, UNIDADES } from "@/lib/formato";
import CodigoRetirada from "../CodigoRetirada";

/** Reservas da instituição, mais recentes primeiro; as pendentes mostram QR Code e PIN. */
export default function MinhasReservas() {
  const [reservas, setReservas] = useState<Reserva[]>();
  const [erro, setErro] = useState("");
  const impacto = useImpacto();

  useEffect(() => {
    // ponytail: só a primeira página
    api<Pagina<Reserva>>("/reservas?size=50").then((p) => setReservas(p.content), (e) => setErro(e.message));
  }, []);

  if (!reservas) return erro ? <p className="erro">{erro}</p> : <p className="carregando">Carregando…</p>;
  const pendentes = reservas.filter((r) => r.status === "PENDENTE").length;

  return (
    <>
      <div>
        <h1>Minhas reservas</h1>
        <p className="sub">{pendentes ? `${pendentes} aguardando retirada` : "Nenhuma retirada pendente"}</p>
      </div>

      <CartaoImpacto titulo="Seu impacto" impacto={impacto?.meu} />

      {reservas.length === 0 ? (
        <div className="vazio">
          <span className="emoji">📦</span>
          <strong>Você ainda não reservou nenhum lote</strong>
          <Link href="/receptor" className="btn" style={{ marginTop: 8 }}>Ver lotes disponíveis</Link>
        </div>
      ) : (
        <ul className="lista">
          {reservas.map((r) => (
            <li key={r.id} className="cartao" style={{ padding: 12 }}>
              <div className="lote" style={{ padding: 0, boxShadow: "none" }}>
                <FotoLote lote={r.lote} className="miniatura" />
                <div className="corpo">
                  <h3>{r.lote.nome}</h3>
                  <p className="discreto">{r.lote.estabelecimentoNome}</p>
                  <div className="meta">
                    <span><Icone nome="caixa" tamanho={14} /> {r.lote.quantidade} {UNIDADES[r.lote.unidade]}</span>
                    <span>{preco(r.pagamento?.valor ?? 0)}</span>
                  </div>
                  <span><span className={`selo ${r.status}`}>{STATUS_RESERVA[r.status]}</span></span>
                  <p className="discreto">
                    {r.status === "COLETADA" && r.validadoEm
                      ? `Retirado ${dataHora(r.validadoEm)}${r.quantidadeEntregue != null ? ` · ${r.quantidadeEntregue} ${UNIDADES[r.lote.unidade]}` : ""}`
                      : `Reservado ${dataHora(r.criadoEm)}`}
                  </p>
                </div>
              </div>
              {r.pagamento && r.pagamento.valorEstornado > 0 && (
                <p className="discreto" style={{ marginTop: 8 }}>Estorno de {preco(r.pagamento.valorEstornado)} devolvido</p>
              )}
              {r.status === "CANCELADA" && r.motivoCancelamento && (
                <p className="discreto" style={{ marginTop: 8 }}>Motivo: {r.motivoCancelamento}</p>
              )}
              {r.status === "PENDENTE" && (
                <details>
                  <summary>Mostrar QR Code e PIN</summary>
                  <CodigoRetirada reserva={r} solto />
                </details>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
