"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import CartaoImpacto, { useImpacto } from "@/components/CartaoImpacto";
import FotoLote from "@/components/FotoLote";
import Icone from "@/components/Icone";
import { api, usePaginado, type Lote, type Pagina, type Reserva } from "@/lib/api";
import { dataHora, preco, restante, STATUS_LOTE, UNIDADES } from "@/lib/formato";

const FILTROS = [["", "Todos"], ["ativos", "Ativos"], ["historico", "Histórico"]] as const;
const ATIVOS = ["DISPONIVEL", "RESERVADO_PENDENTE"];

/** Lotes do doador em qualquer status (UC02 3a): base para repetir um lote anterior. */
export default function MeusLotes() {
  const { itens, erro, maisUm } = usePaginado<Lote>("/lotes/meus");
  const impacto = useImpacto();
  const [filtro, setFiltro] = useState<string>("");
  const [reservadoPor, setReservadoPor] = useState<Record<number, Reserva>>({});

  useEffect(() => {
    // ponytail: só as 100 reservas mais recentes
    api<Pagina<Reserva>>("/reservas?size=100").then((p) =>
      setReservadoPor(Object.fromEntries(p.content.filter((r) => r.status === "PENDENTE").map((r) => [r.lote.id, r]))), () => {});
  }, []);

  const visiveis = itens?.filter((l) => !filtro || (filtro === "ativos") === ATIVOS.includes(l.status));
  const aguardando = Object.keys(reservadoPor).length;

  return (
    <>
      <div>
        <h1>Meus lotes</h1>
        <p className="sub">{aguardando ? `${aguardando} aguardando retirada` : "Nenhuma retirada pendente"}</p>
      </div>

      <CartaoImpacto titulo="Seu impacto" impacto={impacto?.meu} />

      <Link href="/doador/novo" className="btn"><Icone nome="mais" /> Cadastrar novo lote</Link>

      <div className="segmento">
        {FILTROS.map(([v, t]) => (
          <button key={v} className={filtro === v ? "ativo" : ""} onClick={() => setFiltro(v)}>{t}</button>
        ))}
      </div>

      {erro && <p className="erro">{erro}</p>}
      {!visiveis ? (
        <p className="carregando">Carregando…</p>
      ) : visiveis.length === 0 ? (
        <div className="vazio">
          <span className="emoji">🥖</span>
          <strong>{itens?.length ? "Nenhum lote nesta lista" : "Você ainda não publicou nenhum lote"}</strong>
          <p>Publique em poucos toques e avisamos as instituições próximas.</p>
        </div>
      ) : (
        <ul className="lista">
          {visiveis.map((l) => {
            const r = reservadoPor[l.id];
            return (
              <li key={l.id} className="lote" style={{ flexDirection: "column" }}>
                <div style={{ display: "flex", gap: 14 }}>
                  <FotoLote lote={l} className="miniatura" />
                  <div className="corpo">
                    <div className="linha">
                      <h3>{l.nome}</h3>
                      <span className={`preco ${l.preco > 0 ? "pago" : ""}`}>{preco(l.preco)}</span>
                    </div>
                    <div className="meta">
                      <span><Icone nome="caixa" tamanho={14} /> {l.quantidade} {UNIDADES[l.unidade]}</span>
                      <span><Icone nome="relogio" tamanho={14} /> {ATIVOS.includes(l.status) ? `vence ${restante(l.validade)}` : dataHora(l.validade)}</span>
                    </div>
                    <span><span className={`selo ${l.status}`}>{STATUS_LOTE[l.status]}</span></span>
                  </div>
                </div>
                {r && (
                  <div className="reservado-por">
                    <Icone nome="coracao" tamanho={16} /> {r.instituicaoNome} retira até {dataHora(r.prazoRetirada)}
                  </div>
                )}
                <div className="acoes">
                  <Link href={`/doador/novo?de=${l.id}`} className="btn sec mini"><Icone nome="repetir" tamanho={16} /> Repetir lote</Link>
                  {r && <Link href="/doador/validar" className="btn mini"><Icone nome="qr" tamanho={16} /> Validar</Link>}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {maisUm && <button className="sec mais" onClick={maisUm}>Carregar mais</button>}
    </>
  );
}
