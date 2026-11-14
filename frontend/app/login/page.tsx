"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import AuthShell from "@/components/AuthShell";
import { api, inicio, sair, setToken, type Usuario } from "@/lib/api";

export default function Login() {
  const router = useRouter();
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function entrar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setEnviando(true);
    setErro("");
    sair();
    try {
      const { token } = await api<{ token: string }>("/auth/login", {
        method: "POST",
        body: { email: form.get("email"), senha: form.get("senha") },
      });
      setToken(token);
      router.replace(inicio(await api<Usuario>("/auth/me")));
    } catch (err) {
      setErro((err as Error).message);
      setEnviando(false);
    }
  }

  return (
    <AuthShell titulo="Bem-vindo de volta" subtitulo="Entre para doar ou resgatar alimentos perto de você.">
      <form onSubmit={entrar}>
        <label>E-mail<input name="email" type="email" autoComplete="email" placeholder="voce@exemplo.com" required /></label>
        <label>Senha<input name="senha" type="password" autoComplete="current-password" placeholder="••••••••" required /></label>
        {erro && <p className="erro">{erro}</p>}
        <button disabled={enviando}>{enviando ? "Entrando…" : "Entrar"}</button>
      </form>
      <p className="rodape-auth">Ainda não tem conta? <Link href="/cadastro">Criar conta</Link></p>
    </AuthShell>
  );
}
