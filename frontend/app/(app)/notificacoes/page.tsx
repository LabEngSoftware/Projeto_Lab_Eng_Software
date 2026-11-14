"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Icone from "@/components/Icone";
import { api, useUsuario, usePaginado, type Notificacao, type Perfil } from "@/lib/api";
import { dataHora } from "@/lib/formato";

/** O backend guarda o caminho do assunto sem o prefixo do perfil (ex.: /lotes/12, /reservas). */
function destino(link: string | null, perfil: Perfil) {
  if (!link || perfil === "ADMIN") return null;
  if (perfil === "ESTABELECIMENTO") return "/doador";
  return link.startsWith("/lotes/") ? `/receptor${link}` : "/receptor/reservas";
}

/** RF07: caixa de entrada com novos lotes próximos, reservas, retiradas e expirações. */
export default function Notificacoes() {
  const router = useRouter();
  const usuario = useUsuario();
  const { itens, erro, maisUm, recarregar } = usePaginado<Notificacao>("/notificacoes");
  const [lidas, setLidas] = useState<Set<number>>(new Set());

  const lida = (n: Notificacao) => n.lida || lidas.has(n.id);
  const naoLidas = itens?.filter((n) => !lida(n)) ?? [];

  async function marcar(ns: Notificacao[]) {
    await Promise.all(ns.map((n) => api(`/notificacoes/${n.id}/lida`, { method: "PUT" }))).catch(() => {});
    setLidas((s) => new Set([...s, ...ns.map((n) => n.id)]));
    dispatchEvent(new Event("notificacoes")); // atualiza o contador do sino
  }

  async function abrir(n: Notificacao) {
    if (!lida(n)) await marcar([n]);
    const rota = usuario && destino(n.link, usuario.perfil);
    if (rota) router.push(rota);
  }

  return (
    <>
      <div className="cabecalho">
        <div>
          <h1>Notificações</h1>
          <p className="sub">{naoLidas.length ? `${naoLidas.length} não lida${naoLidas.length > 1 ? "s" : ""}` : "Tudo em dia"}</p>
        </div>
        {naoLidas.length > 0 && <button className="sec mini" onClick={() => marcar(naoLidas).then(recarregar)}><Icone nome="check" tamanho={16} /> Marcar todas</button>}
      </div>

      {erro && <p className="erro">{erro}</p>}
      {!itens ? (
        <p className="carregando">Carregando…</p>
      ) : itens.length === 0 ? (
        <div className="vazio">
          <span className="emoji">🔔</span>
          <strong>Nenhuma notificação ainda</strong>
          <p>Avisos de lotes próximos, reservas e retiradas aparecem aqui.</p>
        </div>
      ) : (
        <ul className="lista">
          {itens.map((n) => (
            <li key={n.id}>
              <button className={`item ${lida(n) ? "" : "nao-lida"}`} style={{ width: "100%", textAlign: "left" }} onClick={() => abrir(n)}>
                <div className="corpo">
                  <div className="linha">
                    <strong>{n.titulo}</strong>
                    {!lida(n) && <span className="ponto" />}
                  </div>
                  <p className="sub" style={{ fontSize: 14 }}>{n.mensagem}</p>
                  <p className="discreto">{dataHora(n.criadoEm)}</p>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
      {maisUm && <button className="sec mais" onClick={maisUm}>Carregar mais</button>}
    </>
  );
}
