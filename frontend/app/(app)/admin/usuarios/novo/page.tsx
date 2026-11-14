"use client";

import Link from "next/link";
import { useState } from "react";
import Icone from "@/components/Icone";
import { api, ApiError, type Perfil, type Usuario } from "@/lib/api";
import { PERFIS } from "@/lib/formato";

const CAMPOS = [
  { name: "nome", label: "Nome", autoComplete: "off" },
  { name: "email", label: "E-mail", type: "email" },
  { name: "telefone", label: "Telefone", type: "tel", placeholder: "(11) 99999-9999" },
  { name: "endereco", label: "Endereço completo", placeholder: "Rua, número, cidade" },
] as const;

/** UC01 fluxo principal: o usuário nasce sem senha e define a sua pelo link de primeiro acesso. */
export default function NovoUsuario() {
  const [perfil, setPerfil] = useState<Perfil>("ESTABELECIMENTO");
  const [erro, setErro] = useState("");
  const [erros, setErros] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);
  const [criado, setCriado] = useState<{ usuario: Usuario; linkPrimeiroAcesso: string | null }>();
  const [copiado, setCopiado] = useState(false);

  async function criar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    setEnviando(true);
    setErro("");
    setErros({});
    try {
      setCriado(await api("/admin/usuarios", { method: "POST", body: { ...f, perfil, documento: f.documento || null } }));
      scrollTo(0, 0);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.erros).length) setErros(err.erros);
      else setErro((err as Error).message);
    }
    setEnviando(false);
  }

  if (criado) {
    const { usuario, linkPrimeiroAcesso: link } = criado;
    return (
      <>
        <div className="sucesso">
          <span className="icone-bola grande"><Icone nome="check" tamanho={36} /></span>
          <h1>Usuário criado!</h1>
          <p className="sub">{usuario.nome} ({PERFIS[usuario.perfil]}) já pode definir a senha.</p>
        </div>
        {link ? (
          <div className="link-copia">
            <strong>Envie este link de primeiro acesso</strong>
            <p className="discreto">O envio por e-mail não está ativo. O link vale por 48 horas.</p>
            <code>{link}</code>
            <button className="sec mini" onClick={() => navigator.clipboard.writeText(link).then(() => setCopiado(true))}>
              <Icone nome="copiar" tamanho={16} /> {copiado ? "Copiado!" : "Copiar link"}
            </button>
          </div>
        ) : (
          <p className="discreto centro">O link de primeiro acesso foi enviado por e-mail.</p>
        )}
        <Link href={`/admin/usuarios/${usuario.id}`} className="btn">Ver usuário</Link>
        <button className="sec" onClick={() => { setCriado(undefined); setCopiado(false); }}>Cadastrar outro</button>
      </>
    );
  }

  return (
    <>
      <div>
        <h1>Novo usuário</h1>
        <p className="sub">Ele recebe um link para criar a própria senha.</p>
      </div>

      <form onSubmit={criar}>
        <div>
          <p className="campo-titulo">Perfil</p>
          <div className="chips quebra">
            {(["ESTABELECIMENTO", "INSTITUICAO", "ADMIN"] as const).map((p) => (
              <button key={p} type="button" className={`chip ${perfil === p ? "ativo" : ""}`} onClick={() => setPerfil(p)}>{PERFIS[p]}</button>
            ))}
          </div>
        </div>
        {CAMPOS.map(({ name, label, ...attrs }) => (
          <label key={name}>{label}
            <input name={name} required {...attrs} />
            {erros[name] && <span className="erro">{erros[name]}</span>}
          </label>
        ))}
        <label>CNPJ{perfil === "ADMIN" && " (opcional)"}
          <input name="documento" inputMode="numeric" placeholder="00.000.000/0000-00" required={perfil !== "ADMIN"} />
          {erros.documento && <span className="erro">{erros.documento}</span>}
        </label>
        {erro && <p className="erro">{erro}</p>}
        <button disabled={enviando}>{enviando ? "Criando…" : "Criar usuário"}</button>
        <Link href="/admin/usuarios" className="btn sec">Cancelar</Link>
      </form>
    </>
  );
}
