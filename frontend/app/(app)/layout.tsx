"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Icone, { type NomeIcone } from "@/components/Icone";
import { api, inicio, sair, useUsuario, type Notificacao, type Pagina } from "@/lib/api";

const ABAS: Record<string, { href: string; icone: NomeIcone; texto: string }[]> = {
  "/receptor": [
    { href: "/receptor", icone: "busca", texto: "Lotes" },
    { href: "/receptor/reservas", icone: "caixa", texto: "Minhas reservas" },
  ],
  "/doador": [
    { href: "/doador", icone: "caixa", texto: "Meus lotes" },
    { href: "/doador/novo", icone: "mais", texto: "Novo lote" },
    { href: "/doador/validar", icone: "qr", texto: "Validar" },
  ],
  "/admin": [
    { href: "/admin", icone: "grafico", texto: "Painel" },
    { href: "/admin/usuarios", icone: "usuarios", texto: "Usuários" },
    { href: "/admin/auditoria", icone: "escudo", texto: "Auditoria" },
  ],
};

/** Moldura dos três perfis: cada um só entra na própria área (e na caixa de notificações). */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const rota = usePathname();
  const usuario = useUsuario();
  const [naoLidas, setNaoLidas] = useState(0);

  const casa = usuario && inicio(usuario);
  const permitido = casa && (rota.startsWith(casa) || (rota === "/notificacoes" && casa in ABAS));
  useEffect(() => {
    if (casa && !permitido) router.replace(casa);
  }, [casa, permitido, router]);

  // RF07: o PWA consulta a caixa de entrada periodicamente; a página de notificações avisa quando marca como lida
  useEffect(() => {
    if (!permitido) return;
    const contar = () =>
      api<Pagina<Notificacao>>("/notificacoes?size=50").then((p) => setNaoLidas(p.content.filter((n) => !n.lida).length), () => {});
    contar();
    const timer = setInterval(contar, 30_000);
    addEventListener("notificacoes", contar);
    return () => { clearInterval(timer); removeEventListener("notificacoes", contar); };
  }, [permitido]);

  if (!permitido) return <p className="carregando">Carregando…</p>;
  const abas = ABAS[casa];
  const ativa = (href: string) => (href === casa ? !abas.slice(1).some((a) => rota.startsWith(a.href)) && rota !== "/notificacoes" : rota.startsWith(href));

  return (
    <>
      <header className="topo">
        <div>
          <div className="marca"><Icone nome="folha" tamanho={22} /> ResgatAI</div>
          <p className="discreto">{usuario.nome}</p>
        </div>
        <div className="acoes-topo">
          <Link href="/notificacoes" className="sino" aria-label="Notificações">
            <Icone nome="sino" />
            {naoLidas > 0 && <span className="badge">{naoLidas > 9 ? "9+" : naoLidas}</span>}
          </Link>
          <button className="fantasma" aria-label="Sair" onClick={() => { sair(); router.replace("/login"); }}>
            <Icone nome="sair" />
          </button>
        </div>
      </header>
      <div className="conteudo">{children}</div>
      <nav className="abas-inferiores">
        {abas.map((a) => (
          <Link key={a.href} href={a.href} className={ativa(a.href) ? "ativa" : ""}><Icone nome={a.icone} /> {a.texto}</Link>
        ))}
      </nav>
    </>
  );
}
