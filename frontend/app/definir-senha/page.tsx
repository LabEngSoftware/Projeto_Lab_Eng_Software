"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import AuthShell from "@/components/AuthShell";
import { api, ApiError } from "@/lib/api";

/** Primeiro acesso de usuário cadastrado pelo administrador: o link do e-mail traz ?token=. */
export default function DefinirSenha() {
  const router = useRouter();
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function definir(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setEnviando(true);
    setErro("");
    try {
      await api("/auth/definir-senha", {
        method: "POST",
        body: {
          token: new URLSearchParams(location.search).get("token"),
          senha: form.get("senha"),
          aceiteLgpd: form.get("aceiteLgpd") === "on",
        },
      });
      router.replace("/login");
    } catch (err) {
      setErro(err instanceof ApiError && err.erros.senha ? err.erros.senha : (err as Error).message);
      setEnviando(false);
    }
  }

  return (
    <AuthShell titulo="Definir senha" subtitulo="Crie a senha do seu primeiro acesso.">
      <form onSubmit={definir}>
        <label>Nova senha<input name="senha" type="password" minLength={8} autoComplete="new-password" placeholder="Mínimo 8 caracteres" required /></label>
        <label className="check">
          <input name="aceiteLgpd" type="checkbox" required />
          <span>Li e aceito os termos de uso e a política de privacidade (LGPD)</span>
        </label>
        {erro && <p className="erro">{erro}</p>}
        <button disabled={enviando}>Salvar e ir para o login</button>
      </form>
    </AuthShell>
  );
}
