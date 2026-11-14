"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import FotoLote from "@/components/FotoLote";
import Icone from "@/components/Icone";
import { api, useUsuario, type Lote, type Pagina } from "@/lib/api";
import { CATEGORIAS, preco, restante, UNIDADES } from "@/lib/formato";
import Mapa from "./Mapa";

const RAIOS = [2, 5, 10, 20, 50];
const VALIDADES = [[0, "Qualquer"], [6, "Até 6 h"], [24, "Até 24 h"], [72, "Até 3 dias"]] as const;
const PRECOS = [["", "Qualquer"], ["0", "Só grátis"], ["10", "Até R$ 10"], ["30", "Até R$ 30"]] as const;

/** Feed de lotes disponíveis (RF03), mais urgentes primeiro. */
export default function Feed() {
  const usuario = useUsuario();
  const [lotes, setLotes] = useState<Lote[]>();
  const [erro, setErro] = useState("");
  const [verMapa, setVerMapa] = useState(false);
  const [abrirFiltros, setAbrirFiltros] = useState(false);
  const [nome, setNome] = useState("");
  const [categoria, setCategoria] = useState("");
  const [raioKm, setRaioKm] = useState(10);
  const [horas, setHoras] = useState(0);
  const [precoMax, setPrecoMax] = useState("");

  useEffect(() => {
    // ponytail: só a primeira página; paginar se o raio trouxer mais de 50
    const q = new URLSearchParams({ size: "50", raioKm: String(raioKm) });
    if (nome) q.set("nome", nome);
    if (categoria) q.set("categoria", categoria);
    if (precoMax) q.set("precoMax", precoMax);
    if (horas) q.set("venceAte", new Date(Date.now() + horas * 3_600_000).toISOString());
    api<Pagina<Lote>>(`/lotes?${q}`).then(
      (p) => { setErro(""); setLotes(p.content); },
      (e) => setErro(e.message),
    );
  }, [nome, categoria, raioKm, horas, precoMax]);

  const ativos = Number(raioKm !== 10) + Number(horas > 0) + Number(precoMax !== "");

  return (
    <>
      <div>
        <h1>Lotes perto de você</h1>
        <p className="sub">Reserve antes que outra instituição pegue.</p>
      </div>

      <form className="busca" onSubmit={(e) => { e.preventDefault(); setNome(String(new FormData(e.currentTarget).get("nome")).trim()); }}>
        <div className="campo">
          <Icone nome="busca" />
          <input name="nome" type="search" placeholder="Pão, frutas, marmita…" enterKeyHint="search" />
        </div>
        <button type="button" className={abrirFiltros || ativos ? "" : "sec"} aria-label="Filtros" onClick={() => setAbrirFiltros(!abrirFiltros)}>
          <Icone nome="filtro" />{ativos > 0 && ativos}
        </button>
      </form>

      {abrirFiltros && (
        <div className="cartao painel-filtros">
          <Grupo titulo="Distância" opcoes={RAIOS.map((km) => [km, `${km} km`] as const)} valor={raioKm} mudar={setRaioKm} />
          <Grupo titulo="Validade" opcoes={VALIDADES} valor={horas} mudar={setHoras} />
          <Grupo titulo="Preço" opcoes={PRECOS} valor={precoMax} mudar={setPrecoMax} />
        </div>
      )}

      <div className="chips">
        <button className={`chip ${categoria ? "" : "ativo"}`} onClick={() => setCategoria("")}>Todos</button>
        {Object.entries(CATEGORIAS).map(([v, c]) => (
          <button key={v} className={`chip ${categoria === v ? "ativo" : ""}`} onClick={() => setCategoria(v)}>
            {c.emoji} {c.nome}
          </button>
        ))}
      </div>

      <div className="segmento">
        <button className={verMapa ? "" : "ativo"} onClick={() => setVerMapa(false)}><Icone nome="lista" tamanho={18} /> Lista</button>
        <button className={verMapa ? "ativo" : ""} onClick={() => setVerMapa(true)}><Icone nome="mapa" tamanho={18} /> Mapa</button>
      </div>

      {erro && <p className="erro">{erro}</p>}
      {!lotes ? (
        <p className="carregando">Buscando lotes…</p>
      ) : verMapa ? (
        <Mapa lotes={lotes} centro={usuario?.coordenadas} />
      ) : lotes.length === 0 ? (
        <div className="vazio">
          <span className="emoji">🧺</span>
          <strong>Nenhum lote por aqui agora</strong>
          <p>Tente aumentar a distância ou limpar os filtros. Avisamos quando surgir algo perto.</p>
        </div>
      ) : (
        <ul className="lista">
          {lotes.map((l) => (
            <li key={l.id}>
              <Link href={`/receptor/lotes/${l.id}`} className="lote">
                <FotoLote lote={l} className="miniatura" />
                <div className="corpo">
                  <div className="linha">
                    <h3>{l.nome}</h3>
                    <span className={`preco ${l.preco > 0 ? "pago" : ""}`}>{preco(l.preco)}</span>
                  </div>
                  <p className="discreto">{l.estabelecimentoNome}</p>
                  <div className="meta">
                    <span><Icone nome="caixa" tamanho={14} /> {l.quantidade} {UNIDADES[l.unidade]}</span>
                    {l.distanciaKm != null && <span><Icone nome="pino" tamanho={14} /> {l.distanciaKm} km</span>}
                  </div>
                  <span><span className={`selo ${l.urgencia}`}><Icone nome="relogio" tamanho={12} /> Vence {restante(l.validade)}</span></span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function Grupo<T extends string | number>({ titulo, opcoes, valor, mudar }: {
  titulo: string; opcoes: readonly (readonly [T, string])[]; valor: T; mudar: (v: T) => void;
}) {
  return (
    <div>
      <h3>{titulo}</h3>
      <div className="chips">
        {opcoes.map(([v, t]) => (
          <button key={String(v)} type="button" className={`chip ${valor === v ? "ativo" : ""}`} onClick={() => mudar(v)}>{t}</button>
        ))}
      </div>
    </div>
  );
}
