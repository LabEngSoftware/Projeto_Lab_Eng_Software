"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import AcoesAnalise from "@/components/AcoesAnalise";
import Icone from "@/components/Icone";
import { api, ApiError, usePaginado, type Auditoria, type Usuario } from "@/lib/api";
import { ACOES, cnpj, dataHora, PERFIS, STATUS_ANALISE } from "@/lib/formato";

const ICONES = { ESTABELECIMENTO: "loja", INSTITUICAO: "coracao", ADMIN: "escudo" } as const;

/** UC01 fluxos 3a-3c: consultar, editar e inativar; RF06: analisar instituição; histórico no log de auditoria. */
export default function DetalheUsuario() {
  const { id } = useParams<{ id: string }>();
  const [u, setU] = useState<Usuario>();
  const [erro, setErro] = useState("");
  const [editando, setEditando] = useState(false);
  const [inativando, setInativando] = useState(false);
  const [erros, setErros] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);
  const historico = usePaginado<Auditoria>(`/admin/auditoria?usuarioId=${id}`, 10);

  const carregar = useCallback(() => {
    api<Usuario>(`/admin/usuarios/${id}`).then(setU, (e) => setErro(e.message));
  }, [id]);
  useEffect(carregar, [carregar]);

  async function salvar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviando(true);
    setErro("");
    setErros({});
    try {
      setU(await api<Usuario>(`/admin/usuarios/${id}`, { method: "PUT", body: Object.fromEntries(new FormData(e.currentTarget)) }));
      setEditando(false);
      historico.recarregar();
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.erros).length) setErros(err.erros);
      else setErro((err as Error).message);
    }
    setEnviando(false);
  }

  async function inativar() {
    setEnviando(true);
    setErro("");
    try {
      await api(`/admin/usuarios/${id}`, { method: "DELETE" });
      setInativando(false);
      carregar();
      historico.recarregar();
    } catch (err) {
      setErro((err as Error).message);
      setInativando(false);
    }
    setEnviando(false);
  }

  if (!u) return erro ? <p className="erro">{erro}</p> : <p className="carregando">Carregando…</p>;
  const analisavel = u.perfil === "INSTITUICAO" && u.ativo && u.statusAnalise !== "APROVADA";

  return (
    <>
      <Link href="/admin/usuarios" className="discreto"><Icone nome="voltar" tamanho={14} /> Usuários</Link>

      <div className="info-item">
        <span className="icone-bola grande" style={{ margin: 0, flex: "none" }}><Icone nome={ICONES[u.perfil]} tamanho={32} /></span>
        <div className="pilha" style={{ gap: 6 }}>
          <h1>{u.nome}</h1>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            <span className="selo neutro">{PERFIS[u.perfil]}</span>
            {u.statusAnalise && <span className={`selo ${u.statusAnalise}`}>{STATUS_ANALISE[u.statusAnalise]}</span>}
            <span className={`selo ${u.ativo ? "ATIVO" : "INATIVO"}`}>{u.ativo ? "Ativo" : "Inativo"}</span>
          </div>
        </div>
      </div>

      {erro && <p className="erro">{erro}</p>}

      {analisavel && (
        <div className="cartao pilha">
          <h2>{u.statusAnalise === "REJEITADA" ? "Cadastro rejeitado" : "Aguardando análise"}</h2>
          <p className="sub">
            {u.statusAnalise === "REJEITADA" ? "Você pode reconsiderar e aprovar a instituição." : "A instituição só reserva lotes depois de aprovada."}
          </p>
          <AcoesAnalise id={u.id} rejeitada={u.statusAnalise === "REJEITADA"} depois={() => { carregar(); historico.recarregar(); }} />
        </div>
      )}

      {editando ? (
        <form className="cartao" onSubmit={salvar}>
          <h2>Editar dados</h2>
          {([["nome", "Nome", u.nome], ["email", "E-mail", u.email], ["telefone", "Telefone", u.telefone], ["endereco", "Endereço", u.endereco]] as const).map(([n, t, v]) => (
            <label key={n}>{t}
              <input name={n} defaultValue={v ?? ""} required type={n === "email" ? "email" : "text"} />
              {erros[n] && <span className="erro">{erros[n]}</span>}
            </label>
          ))}
          <button disabled={enviando}>{enviando ? "Salvando…" : "Salvar"}</button>
          <button type="button" className="sec" onClick={() => { setEditando(false); setErros({}); }}>Cancelar</button>
        </form>
      ) : (
        <div className="cartao pilha">
          <div className="cabecalho">
            <h2>Dados</h2>
            <button className="sec mini" onClick={() => setEditando(true)}><Icone nome="editar" tamanho={16} /> Editar</button>
          </div>
          <dl className="dados" style={{ margin: 0 }}>
            {u.documento && <div><dt>CNPJ</dt><dd>{cnpj(u.documento)}</dd></div>}
            <div><dt>E-mail</dt><dd>{u.email}</dd></div>
            {u.telefone && <div><dt>Telefone</dt><dd>{u.telefone}</dd></div>}
            {u.endereco && <div><dt>Endereço</dt><dd>{u.endereco}</dd></div>}
            <div><dt>Reputação</dt><dd>{u.score} pontos</dd></div>
            <div><dt>Cadastro</dt><dd>{dataHora(u.criadoEm)}</dd></div>
          </dl>
        </div>
      )}

      {u.ativo && (inativando ? (
        <div className="aviso-atraso">
          <strong>Inativar {u.nome}?</strong>
          <p>O acesso é revogado na hora e os lotes saem das buscas. Não há como reativar.</p>
          <button className="perigo mini" disabled={enviando} onClick={inativar}>Sim, inativar</button>
          <button className="fantasma" onClick={() => setInativando(false)}>Cancelar</button>
        </div>
      ) : (
        <button className="perigo" onClick={() => setInativando(true)}>Inativar usuário</button>
      ))}

      <h2>Histórico</h2>
      {!historico.itens ? (
        <p className="carregando">Carregando…</p>
      ) : historico.itens.length === 0 ? (
        <p className="discreto">Nenhum registro de auditoria para este usuário.</p>
      ) : (
        <ul className="lista">
          {historico.itens.map((a) => <LinhaAuditoria key={a.id} a={a} />)}
        </ul>
      )}
      {historico.maisUm && <button className="sec mais" onClick={historico.maisUm}>Carregar mais</button>}
    </>
  );
}

function LinhaAuditoria({ a }: { a: Auditoria }) {
  const acao = ACOES[a.acao] ?? { texto: a.acao, icone: "escudo" as const };
  return (
    <li className="item">
      <span className="icone-bola"><Icone nome={acao.icone} tamanho={20} /></span>
      <div className="corpo">
        <h3>{acao.texto}</h3>
        {a.detalhes && <p className="discreto">{a.detalhes}</p>}
        <p className="discreto">{dataHora(a.criadoEm)}</p>
      </div>
    </li>
  );
}
