"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Icone from "@/components/Icone";
import { api, ApiError, useUsuario, type Lote } from "@/lib/api";
import { CATEGORIAS, dataHora, emHoras } from "@/lib/formato";

const HORAS = [2, 4, 8, 12, 24, 48];
const UNIDADES_NOME = { UNIDADE: "Unidades", KG: "Quilos (kg)", LITRO: "Litros", PACOTE: "Pacotes", CAIXA: "Caixas" };

/** Repetir um lote (?de=) pré-preenche o formulário com o lote anterior. */
export default function NovoLote({ de }: { de?: string }) {
  const usuario = useUsuario();
  const [base, setBase] = useState<Lote | null>();
  useEffect(() => {
    if (de) api<Lote>(`/lotes/${de}`).then(setBase, () => setBase(null));
  }, [de]);

  const pronto = usuario && (!de || base === null || base?.id === Number(de));
  if (!pronto) return <p className="carregando">Carregando…</p>;
  return <Formulario key={de ?? "novo"} base={de ? base : null} enderecoCadastro={usuario.endereco} />;
}

function Formulario({ base, enderecoCadastro }: { base: Lote | null | undefined; enderecoCadastro: string }) {
  const [categoria, setCategoria] = useState(base?.categoria ?? "");
  const [validade, setValidade] = useState<number | "outra" | null>(() => {
    const h = base && Math.round((Date.parse(base.validade) - Date.parse(base.retiradaInicio)) / 3_600_000);
    return h && HORAS.includes(h) ? h : null;
  });
  const [erro, setErro] = useState("");
  const [erros, setErros] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);
  const [publicado, setPublicado] = useState<{ lote: Lote; avisoFoto: string }>();

  const enderecoAlternativo = base && base.enderecoRetirada !== enderecoCadastro ? base.enderecoRetirada : "";

  async function publicar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const fim = validade === "outra" ? new Date(String(f.get("validadeOutra"))) : validade ? new Date(emHoras(validade)) : null;
    const faltando: Record<string, string> = {};
    if (!categoria) faltando.categoria = "Escolha a categoria";
    if (!fim || isNaN(+fim)) faltando.validade = "Escolha quando o lote vence";
    setErros(faltando);
    if (Object.keys(faltando).length) return;

    setEnviando(true);
    setErro("");
    try {
      const lote = await api<Lote>("/lotes", {
        method: "POST",
        body: {
          nome: f.get("nome"),
          categoria,
          quantidade: Number(f.get("quantidade")),
          unidade: f.get("unidade"),
          preco: Number(f.get("preco") || 0),
          validade: fim!.toISOString(),
          retiradaInicio: new Date().toISOString(),
          retiradaFim: fim!.toISOString(), // a retirada vale até o vencimento
          observacoes: f.get("observacoes") || null,
          enderecoRetirada: f.get("enderecoRetirada") || null,
        },
      });
      // a foto vai depois: se falhar, o lote já está publicado e o doador é avisado
      let avisoFoto = "";
      const foto = f.get("foto") as File;
      if (foto?.size) {
        const fd = new FormData();
        fd.set("arquivo", foto);
        await api(`/lotes/${lote.id}/foto`, { method: "PUT", body: fd }).catch((err: Error) => { avisoFoto = err.message; });
      }
      setPublicado({ lote, avisoFoto });
      scrollTo(0, 0);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.erros).length) setErros(err.erros);
      else setErro((err as Error).message);
      setEnviando(false);
    }
  }

  if (publicado) {
    return (
      <>
        <div className="sucesso">
          <span className="icone-bola grande"><Icone nome="check" tamanho={36} /></span>
          <h1>Lote publicado!</h1>
          <p className="sub">
            {publicado.lote.nome} já aparece para as instituições. As mais próximas são avisadas agora.
          </p>
        </div>
        {publicado.avisoFoto && <p className="erro centro">O lote foi publicado, mas a foto não foi enviada: {publicado.avisoFoto}</p>}
        <Link href="/doador" className="btn">Ver meus lotes</Link>
        <button className="sec" onClick={() => { setPublicado(undefined); setCategoria(""); setValidade(null); setEnviando(false); }}>
          Cadastrar outro lote
        </button>
      </>
    );
  }

  return (
    <>
      <div>
        <h1>{base ? "Repetir lote" : "Novo lote"}</h1>
        <p className="sub">Escolha a categoria, quando vence e publique.</p>
      </div>

      <form onSubmit={publicar}>
        <label>O que você está doando?
          <input name="nome" defaultValue={base?.nome} maxLength={150} placeholder="Ex.: Pão francês" required />
          {erros.nome && <span className="erro">{erros.nome}</span>}
        </label>

        <div>
          <p className="campo-titulo">Categoria</p>
          <div className="chips quebra">
            {Object.entries(CATEGORIAS).map(([v, c]) => (
              <button key={v} type="button" className={`chip ${categoria === v ? "ativo" : ""}`} onClick={() => setCategoria(v)}>
                {c.emoji} {c.nome}
              </button>
            ))}
          </div>
          {erros.categoria && <p className="erro" style={{ marginTop: 6 }}>{erros.categoria}</p>}
        </div>

        <div className="duas">
          <label>Quantidade
            <input name="quantidade" type="number" inputMode="decimal" min="0.01" step="any" defaultValue={base?.quantidade ?? 1} required />
            {erros.quantidade && <span className="erro">{erros.quantidade}</span>}
          </label>
          <label>Unidade
            <select name="unidade" defaultValue={base?.unidade ?? "UNIDADE"}>
              {Object.entries(UNIDADES_NOME).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
            </select>
          </label>
        </div>

        <div>
          <p className="campo-titulo">Vence em</p>
          <div className="chips quebra">
            {HORAS.map((h) => (
              <button key={h} type="button" className={`chip ${validade === h ? "ativo" : ""}`} onClick={() => setValidade(h)}>{h} h</button>
            ))}
            <button type="button" className={`chip ${validade === "outra" ? "ativo" : ""}`} onClick={() => setValidade("outra")}>Outra data</button>
          </div>
          {validade === "outra" && (
            <input name="validadeOutra" type="datetime-local" required style={{ marginTop: 10 }} />
          )}
          {typeof validade === "number" && (
            <p className="resumo-janela" style={{ marginTop: 10 }}>
              <Icone nome="relogio" tamanho={16} /> Retirada liberada agora, até {dataHora(emHoras(validade))}
            </p>
          )}
          {(erros.validade || erros.retiradaFim) && <p className="erro" style={{ marginTop: 6 }}>{erros.validade ?? erros.retiradaFim}</p>}
        </div>

        <details className="cartao avancado" open={!!(base && (base.preco > 0 || base.observacoes || enderecoAlternativo)) || undefined}>
          <summary>Mais opções <span className="discreto">preço, foto e observações</span></summary>
          <div className="pilha">
            <label>Preço (R$) — deixe 0 para doar
              <input name="preco" type="number" inputMode="decimal" min="0" step="0.01" defaultValue={base?.preco ?? 0} />
              {erros.preco && <span className="erro">{erros.preco}</span>}
            </label>
            <label>Observações
              <textarea name="observacoes" rows={3} maxLength={500} defaultValue={base?.observacoes ?? ""} placeholder="Ex.: trazer sacolas, manter refrigerado" />
            </label>
            <label>Retirar em outro endereço?
              <input name="enderecoRetirada" defaultValue={enderecoAlternativo ?? ""} placeholder={enderecoCadastro} />
              {erros.enderecoRetirada && <span className="erro">{erros.enderecoRetirada}</span>}
            </label>
            <label>Foto do lote
              <input name="foto" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" className="foto-input" />
            </label>
          </div>
        </details>

        {erro && <p className="erro centro">{erro}</p>}
        <div className="barra-acao"><button disabled={enviando}>{enviando ? "Publicando…" : "Publicar lote"}</button></div>
      </form>
    </>
  );
}
