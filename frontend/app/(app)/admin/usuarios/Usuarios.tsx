"use client";

import Link from "next/link";
import { useState } from "react";
import Icone from "@/components/Icone";
import { usePaginado, type Usuario } from "@/lib/api";
import { PERFIS, STATUS_ANALISE } from "@/lib/formato";

const PERFIL_CHIPS = [["", "Todos"], ["ESTABELECIMENTO", "Doadores"], ["INSTITUICAO", "Instituições"], ["ADMIN", "Admins"]] as const;
const SITUACAO_CHIPS = [["", "Todas"], ["PENDENTE", "Em análise"], ["APROVADA", "Aprovadas"], ["REJEITADA", "Rejeitadas"], ["INATIVO", "Inativos"]] as const;
const ICONES = { ESTABELECIMENTO: "loja", INSTITUICAO: "coracao", ADMIN: "escudo" } as const;

/** UC01: busca de usuários por nome, perfil e situação. */
export default function Usuarios({ perfilInicial, situacaoInicial }: { perfilInicial: string; situacaoInicial: string }) {
  const [nome, setNome] = useState("");
  const [perfil, setPerfil] = useState(perfilInicial);
  const [situacao, setSituacao] = useState(situacaoInicial);

  const q = new URLSearchParams({ sort: "criadoEm,desc" });
  if (nome) q.set("nome", nome);
  if (perfil) q.set("perfil", perfil);
  if (situacao === "INATIVO") q.set("ativo", "false");
  else if (situacao) q.set("statusAnalise", situacao);
  const { itens, total, erro, maisUm } = usePaginado<Usuario>(`/admin/usuarios?${q}`);

  return (
    <>
      <div className="cabecalho">
        <div>
          <h1>Usuários</h1>
          <p className="sub">{itens ? `${total} ${total === 1 ? "usuário" : "usuários"}` : "Carregando…"}</p>
        </div>
        <Link href="/admin/usuarios/novo" className="btn mini"><Icone nome="mais" tamanho={16} /> Novo</Link>
      </div>

      <form className="busca" onSubmit={(e) => { e.preventDefault(); setNome(String(new FormData(e.currentTarget).get("nome")).trim()); }}>
        <div className="campo">
          <Icone nome="busca" />
          <input name="nome" type="search" placeholder="Buscar por nome" enterKeyHint="search" />
        </div>
      </form>

      <div className="pilha" style={{ gap: 8 }}>
        <div className="chips">
          {PERFIL_CHIPS.map(([v, t]) => <button key={v} className={`chip ${perfil === v ? "ativo" : ""}`} onClick={() => setPerfil(v)}>{t}</button>)}
        </div>
        <div className="chips">
          {SITUACAO_CHIPS.map(([v, t]) => <button key={v} className={`chip ${situacao === v ? "ativo" : ""}`} onClick={() => setSituacao(v)}>{t}</button>)}
        </div>
      </div>

      {erro && <p className="erro">{erro}</p>}
      {itens && itens.length === 0 ? (
        <div className="vazio">
          <span className="emoji">🔎</span>
          <strong>Nenhum usuário encontrado</strong>
          <p>Ajuste a busca ou os filtros.</p>
        </div>
      ) : (
        <ul className="lista">
          {itens?.map((u) => (
            <li key={u.id}>
              <Link href={`/admin/usuarios/${u.id}`} className="item">
                <span className={`icone-bola ${u.statusAnalise === "PENDENTE" ? "laranja" : ""}`}><Icone nome={ICONES[u.perfil]} tamanho={20} /></span>
                <div className="corpo">
                  <h3>{u.nome}</h3>
                  <p className="discreto">{PERFIS[u.perfil]} · {u.email}</p>
                </div>
                {!u.ativo ? <span className="selo INATIVO">Inativo</span>
                  : u.statusAnalise && <span className={`selo ${u.statusAnalise}`}>{STATUS_ANALISE[u.statusAnalise]}</span>}
              </Link>
            </li>
          ))}
        </ul>
      )}
      {maisUm && <button className="sec mais" onClick={maisUm}>Carregar mais</button>}
    </>
  );
}
