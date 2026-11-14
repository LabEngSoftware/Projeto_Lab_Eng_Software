"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import AuthShell from "@/components/AuthShell";
import Icone from "@/components/Icone";
import { api, ApiError, inicio, sair, setToken, type Perfil, type Usuario } from "@/lib/api";

const PERFIS = {
  ESTABELECIMENTO: {
    titulo: "Quero doar",
    texto: "Mercado, padaria ou hortifrúti com alimentos perto do vencimento.",
    icone: "loja",
  },
  INSTITUICAO: {
    titulo: "Quero receber",
    texto: "ONG, igreja ou abrigo. A conta passa por análise antes de reservar.",
    icone: "coracao",
  },
} as const;

const CAMPOS = [
  { name: "nome", label: "Nome da organização", autoComplete: "organization" },
  { name: "documento", label: "CNPJ", inputMode: "numeric", placeholder: "00.000.000/0000-00" },
  { name: "email", label: "E-mail", type: "email", autoComplete: "email" },
  { name: "senha", label: "Senha", type: "password", minLength: 8, autoComplete: "new-password", placeholder: "Mínimo 8 caracteres" },
  { name: "telefone", label: "Telefone", type: "tel", autoComplete: "tel", placeholder: "(11) 99999-9999" },
  { name: "endereco", label: "Endereço completo", autoComplete: "street-address", placeholder: "Rua, número, cidade" },
] as const;

export default function Cadastro() {
  const router = useRouter();
  const [perfil, setPerfil] = useState<Exclude<Perfil, "ADMIN">>();
  const [erro, setErro] = useState("");
  const [erros, setErros] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);

  if (!perfil) {
    return (
      <AuthShell titulo="Criar conta" subtitulo="Como você vai usar o ResgatAI?">
        <div className="pilha">
          {(Object.keys(PERFIS) as (keyof typeof PERFIS)[]).map((p) => (
            <button key={p} className="perfil" onClick={() => setPerfil(p)}>
              <span className={`icone-bola ${p === "INSTITUICAO" ? "laranja" : ""}`}><Icone nome={PERFIS[p].icone} tamanho={24} /></span>
              <span>
                <strong>{PERFIS[p].titulo}</strong>
                <small>{PERFIS[p].texto}</small>
              </span>
            </button>
          ))}
        </div>
        <p className="rodape-auth">Já tem conta? <Link href="/login">Entrar</Link></p>
      </AuthShell>
    );
  }

  async function cadastrar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const dados = Object.fromEntries(new FormData(e.currentTarget));
    setEnviando(true);
    setErro("");
    setErros({});
    sair();
    try {
      await api("/auth/registro", { method: "POST", body: { ...dados, perfil, aceiteLgpd: dados.aceiteLgpd === "on" } });
      const { token } = await api<{ token: string }>("/auth/login", {
        method: "POST",
        body: { email: dados.email, senha: dados.senha },
      });
      setToken(token);
      router.replace(inicio(await api<Usuario>("/auth/me")));
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.erros).length) setErros(err.erros);
      else setErro((err as Error).message);
      setEnviando(false);
    }
  }

  return (
    <AuthShell titulo={PERFIS[perfil].titulo} subtitulo={PERFIS[perfil].texto}>
      <form onSubmit={cadastrar}>
        {CAMPOS.map(({ name, label, ...attrs }) => (
          <label key={name}>
            {label}
            <input name={name} required {...attrs} />
            {erros[name] && <span className="erro">{erros[name]}</span>}
          </label>
        ))}
        <label className="check">
          <input name="aceiteLgpd" type="checkbox" required />
          <span>Li e aceito os termos de uso e a política de privacidade (LGPD)</span>
        </label>
        {erro && <p className="erro">{erro}</p>}
        <button disabled={enviando}>{enviando ? "Criando conta…" : "Criar conta"}</button>
        <button type="button" className="sec" onClick={() => setPerfil(undefined)}>Voltar</button>
      </form>
    </AuthShell>
  );
}
