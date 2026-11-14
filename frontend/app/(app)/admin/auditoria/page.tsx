"use client";

import Link from "next/link";
import Icone from "@/components/Icone";
import { usePaginado, type Auditoria } from "@/lib/api";
import { ACOES, dataHora } from "@/lib/formato";

/** Log de auditoria (UC01): quem fez o quê, quando e em qual registro; os mais recentes primeiro. */
export default function AuditoriaPagina() {
  const { itens, total, erro, maisUm } = usePaginado<Auditoria>("/admin/auditoria");

  return (
    <>
      <div>
        <h1>Auditoria</h1>
        <p className="sub">{itens ? `${total} registros` : "Carregando…"}</p>
      </div>

      {erro && <p className="erro">{erro}</p>}
      {itens?.length === 0 && <div className="vazio"><span className="emoji">🛡️</span><strong>Nenhum registro ainda</strong></div>}
      <ul className="lista">
        {itens?.map((a) => {
          const acao = ACOES[a.acao] ?? { texto: a.acao, icone: "escudo" as const };
          return (
            <li key={a.id} className="item" style={{ alignItems: "flex-start" }}>
              <span className="icone-bola"><Icone nome={acao.icone} tamanho={20} /></span>
              <div className="corpo">
                <h3>{acao.texto}</h3>
                {a.detalhes && <p className="discreto">{a.detalhes}</p>}
                <p className="discreto">
                  {a.entidade === "USUARIO" ? <Link href={`/admin/usuarios/${a.entidadeId}`}>usuário #{a.entidadeId}</Link> : `${a.entidade.toLowerCase()} #${a.entidadeId}`}
                  {" · por "}
                  {a.autorId ? <Link href={`/admin/usuarios/${a.autorId}`}>usuário #{a.autorId}</Link> : "sistema"}
                  {" · "}{dataHora(a.criadoEm)}
                </p>
              </div>
            </li>
          );
        })}
      </ul>
      {maisUm && <button className="sec mais" onClick={maisUm}>Carregar mais</button>}
    </>
  );
}
