"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import AuthShell from "@/components/AuthShell";
import Icone from "@/components/Icone";
import { inicio, sair, useUsuario } from "@/lib/api";

/** RF06: instituição só reserva lotes depois de aprovada pelo administrador. */
export default function EmAnalise() {
  const router = useRouter();
  const usuario = useUsuario();
  useEffect(() => {
    if (usuario?.statusAnalise === "APROVADA") router.replace(inicio(usuario));
  }, [usuario, router]);

  if (!usuario) return <p className="carregando">Carregando…</p>;
  const rejeitada = usuario.statusAnalise === "REJEITADA";

  return (
    <AuthShell titulo={rejeitada ? "Cadastro não aprovado" : "Conta em análise"}>
      <div className="pilha centro">
        <span className={`icone-bola grande ${rejeitada ? "vermelho" : "laranja"}`}>
          <Icone nome={rejeitada ? "alerta" : "ampulheta"} tamanho={34} />
        </span>
        <p className="sub">
          {rejeitada
            ? <>A análise do cadastro de <strong>{usuario.nome}</strong> não foi aprovada. Entre em contato com o administrador da plataforma.</>
            : <>Recebemos o cadastro de <strong>{usuario.nome}</strong>. Assim que um administrador aprovar a instituição, você poderá reservar lotes.</>}
        </p>
      </div>
      <div className="pilha">
        {!rejeitada && <button onClick={() => location.reload()}>Verificar novamente</button>}
        <button className="sec" onClick={() => { sair(); router.replace("/login"); }}><Icone nome="sair" /> Sair</button>
      </div>
    </AuthShell>
  );
}
