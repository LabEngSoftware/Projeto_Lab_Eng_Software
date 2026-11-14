"use client";

import Link from "next/link";
import AcoesAnalise from "@/components/AcoesAnalise";
import CartaoImpacto, { useImpacto } from "@/components/CartaoImpacto";
import Icone from "@/components/Icone";
import { usePaginado, type Usuario } from "@/lib/api";
import { cnpj, dataHora } from "@/lib/formato";

/** Painel do administrador: impacto da plataforma, números de cadastro e a fila de aprovação (RF06). */
export default function Painel() {
  const impacto = useImpacto();
  const pendentes = usePaginado<Usuario>("/admin/usuarios?statusAnalise=PENDENTE&ativo=true&sort=criadoEm", 10);
  const doadores = usePaginado<Usuario>("/admin/usuarios?perfil=ESTABELECIMENTO", 1);
  const instituicoes = usePaginado<Usuario>("/admin/usuarios?perfil=INSTITUICAO", 1);

  return (
    <>
      <div>
        <h1>Painel</h1>
        <p className="sub">Visão geral da plataforma.</p>
      </div>

      <CartaoImpacto titulo="Impacto da plataforma" impacto={impacto?.plataforma} />

      <div className="stats">
        <Link href="/admin/usuarios?perfil=ESTABELECIMENTO" className="cartao stat"><strong>{doadores.total}</strong><span>Doadores</span></Link>
        <Link href="/admin/usuarios?perfil=INSTITUICAO" className="cartao stat"><strong>{instituicoes.total}</strong><span>Instituições</span></Link>
        <Link href="/admin/usuarios?situacao=PENDENTE" className={`cartao stat ${pendentes.total ? "alerta" : ""}`}><strong>{pendentes.total}</strong><span>Em análise</span></Link>
      </div>

      <div className="cabecalho">
        <h2>Aguardando aprovação</h2>
        <Link href="/admin/usuarios">Ver todos</Link>
      </div>

      {pendentes.erro && <p className="erro">{pendentes.erro}</p>}
      {!pendentes.itens ? (
        <p className="carregando">Carregando…</p>
      ) : pendentes.itens.length === 0 ? (
        <div className="vazio">
          <span className="emoji">✅</span>
          <strong>Nenhuma instituição aguardando análise</strong>
          <p>Novos cadastros de ONGs, igrejas e abrigos aparecem aqui.</p>
        </div>
      ) : (
        <ul className="lista">
          {pendentes.itens.map((u) => (
            <li key={u.id} className="cartao pilha">
              <div className="info-item">
                <span className="icone-bola laranja"><Icone nome="coracao" tamanho={20} /></span>
                <div>
                  <strong>{u.nome}</strong>
                  <span>CNPJ {cnpj(u.documento)}</span><br />
                  <span>{u.endereco}</span><br />
                  <span>Cadastro em {dataHora(u.criadoEm)}</span>
                </div>
              </div>
              <AcoesAnalise id={u.id} depois={() => { pendentes.recarregar(); }} />
              <Link href={`/admin/usuarios/${u.id}`} className="centro">Ver detalhes</Link>
            </li>
          ))}
        </ul>
      )}
      {pendentes.maisUm && <button className="sec mais" onClick={pendentes.maisUm}>Carregar mais</button>}
    </>
  );
}
