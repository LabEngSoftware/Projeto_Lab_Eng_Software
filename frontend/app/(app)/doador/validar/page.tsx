"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import FotoLote from "@/components/FotoLote";
import Icone from "@/components/Icone";
import { api, type Reserva } from "@/lib/api";
import { dataHora, passou, preco, UNIDADES } from "@/lib/formato";
import Scanner from "./Scanner";

type Etapa =
  | { tipo: "codigo" }
  | { tipo: "conferida"; codigo: string; reserva: Reserva }
  | { tipo: "pronto"; reserva: Reserva; recusada: boolean };

/** UC06: o doador lê o QR Code (câmera) ou digita o PIN, confere o lote e registra a entrega. */
export default function Validar() {
  const [etapa, setEtapa] = useState<Etapa>({ tipo: "codigo" });
  const [modo, setModo] = useState<"camera" | "pin">("camera");
  const [leitura, setLeitura] = useState(0); // novo valor remonta o scanner depois de um erro
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [recusando, setRecusando] = useState(false);
  const [autorizaAtraso, setAutorizaAtraso] = useState(false);

  const conferir = useCallback(async (codigo: string) => {
    setErro("");
    try {
      const reserva = await api<Reserva>("/retiradas/conferencia", { method: "POST", body: { codigo: codigo.trim() } });
      setRecusando(false);
      setAutorizaAtraso(false);
      setEtapa({ tipo: "conferida", codigo: codigo.trim(), reserva });
    } catch (e) {
      setErro((e as Error).message);
      setLeitura((n) => n + 1);
    }
  }, []);

  async function confirmar(e: React.FormEvent<HTMLFormElement>, reserva: Reserva, codigo: string) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro("");
    try {
      const pronta = recusando
        ? await api<Reserva>("/retiradas/cancelamento", { method: "POST", body: { codigo, motivo: f.get("motivo") } })
        : await api<Reserva>("/retiradas", {
            method: "POST",
            body: { codigo, quantidadeEntregue: Number(f.get("quantidade")), autorizarAtraso: autorizaAtraso },
          });
      setEtapa({ tipo: "pronto", reserva: pronta, recusada: recusando });
      scrollTo(0, 0);
    } catch (err) {
      setErro((err as Error).message);
    }
    setEnviando(false);
  }

  function nova() {
    setEtapa({ tipo: "codigo" });
    setErro("");
    setLeitura((n) => n + 1);
  }

  if (etapa.tipo === "pronto") return <Comprovante reserva={etapa.reserva} recusada={etapa.recusada} nova={nova} />;

  if (etapa.tipo === "conferida") {
    const { reserva, codigo } = etapa;
    const l = reserva.lote;
    const atrasada = passou(reserva.prazoRetirada);
    return (
      <>
        <div>
          <h1>Confira a entrega</h1>
          <p className="sub">Veja se o lote e a instituição conferem antes de entregar.</p>
        </div>
        <form onSubmit={(e) => confirmar(e, reserva, codigo)}>
          <div className="cartao pilha">
            <div className="lote" style={{ padding: 0, boxShadow: "none" }}>
              <FotoLote lote={l} className="miniatura" />
              <div className="corpo">
                <h3>{l.nome}</h3>
                <p className="discreto">{l.quantidade} {UNIDADES[l.unidade]} · {preco(l.preco)}</p>
                <span><span className={`selo ${atrasada ? "ALTA" : "BAIXA"}`}><Icone nome="relogio" tamanho={12} /> {atrasada ? "Fora do prazo" : "No prazo"}</span></span>
              </div>
            </div>
            <div className="info">
              <div className="info-item">
                <span className="icone-bola"><Icone nome="coracao" tamanho={18} /></span>
                <div><strong>{reserva.instituicaoNome}</strong><span>Instituição que reservou</span></div>
              </div>
              <div className="info-item">
                <span className="icone-bola"><Icone nome="relogio" tamanho={18} /></span>
                <div><strong>Retirada até {dataHora(reserva.prazoRetirada)}</strong><span>Prazo da reserva</span></div>
              </div>
            </div>
          </div>

          {atrasada && !recusando && (
            <div className="aviso-atraso">
              <strong>O prazo desta reserva já passou.</strong>
              <label className="check">
                <input type="checkbox" checked={autorizaAtraso} onChange={(e) => setAutorizaAtraso(e.target.checked)} />
                <span>Autorizo a entrega fora do prazo</span>
              </label>
            </div>
          )}

          {recusando ? (
            <div className="cartao pilha">
              <label>Por que recusar a entrega?
                <textarea name="motivo" rows={3} maxLength={500} placeholder="Ex.: o lote não confere com o combinado" required />
              </label>
              <p className="discreto">O lote e a reserva serão cancelados{reserva.pagamento ? " e o valor pago, estornado" : ""}.</p>
            </div>
          ) : (
            <div className="cartao pilha">
              <label>Quantidade entregue ({UNIDADES[l.unidade]})
                <input name="quantidade" type="number" inputMode="decimal" min="0.01" max={l.quantidade} step="any" defaultValue={l.quantidade} required />
              </label>
              <p className="discreto">
                Entregou menos que {l.quantidade} {UNIDADES[l.unidade]}? Informe aqui{l.preco > 0 ? ": a instituição paga só o que recebeu" : ""}.
              </p>
            </div>
          )}

          {erro && <p className="erro centro">{erro}</p>}
          {recusando ? (
            <>
              <button className="perigo" disabled={enviando}>{enviando ? "Recusando…" : "Confirmar recusa"}</button>
              <button type="button" className="sec" onClick={() => setRecusando(false)}>Voltar</button>
            </>
          ) : (
            <>
              <button disabled={enviando || (atrasada && !autorizaAtraso)}>{enviando ? "Registrando…" : "Confirmar retirada"}</button>
              <button type="button" className="sec" onClick={() => setRecusando(true)}>Recusar entrega</button>
              <button type="button" className="fantasma" onClick={nova}>Ler outro código</button>
            </>
          )}
        </form>
      </>
    );
  }

  return (
    <>
      <div>
        <h1>Validar retirada</h1>
        <p className="sub">Leia o QR Code da instituição ou digite o PIN de 4 dígitos.</p>
      </div>

      <div className="segmento">
        <button className={modo === "camera" ? "ativo" : ""} onClick={() => { setModo("camera"); setErro(""); }}><Icone nome="camera" tamanho={18} /> Câmera</button>
        <button className={modo === "pin" ? "ativo" : ""} onClick={() => { setModo("pin"); setErro(""); }}><Icone nome="teclado" tamanho={18} /> PIN</button>
      </div>

      {modo === "camera" ? (
        <Scanner key={leitura} onCodigo={conferir} />
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); conferir(String(new FormData(e.currentTarget).get("pin"))); }}>
          <input name="pin" className="pin-input" inputMode="numeric" autoComplete="off" maxLength={4} placeholder="••••" pattern="\d{4}" required />
          <button>Conferir</button>
        </form>
      )}
      {erro && <p className="erro centro">{erro}</p>}
    </>
  );
}

function Comprovante({ reserva, recusada, nova }: { reserva: Reserva; recusada: boolean; nova: () => void }) {
  const l = reserva.lote;
  const p = reserva.pagamento;
  return (
    <>
      <div className="sucesso">
        <span className={`icone-bola grande ${recusada ? "vermelho" : ""}`}><Icone nome={recusada ? "alerta" : "check"} tamanho={36} /></span>
        <h1>{recusada ? "Entrega recusada" : "Retirada registrada!"}</h1>
        <p className="sub">{recusada ? "O lote e a reserva foram cancelados." : "Obrigado por doar. A instituição já foi avisada."}</p>
      </div>

      <div className="cartao comprovante">
        <p className="discreto centro">Comprovante #{reserva.id}</p>
        <div className="corte" />
        <dl>
          <div><dt>Instituição</dt><dd>{reserva.instituicaoNome}</dd></div>
          <div><dt>Lote</dt><dd>{l.nome}</dd></div>
          {recusada ? (
            <div><dt>Motivo</dt><dd>{reserva.motivoCancelamento}</dd></div>
          ) : (
            <>
              <div><dt>Entregue</dt><dd>{reserva.quantidadeEntregue} de {l.quantidade} {UNIDADES[l.unidade]}</dd></div>
              <div><dt>Prazo</dt><dd>{reserva.atrasada ? "Fora do prazo (autorizado)" : "Dentro do prazo"}</dd></div>
            </>
          )}
          {reserva.validadoEm && <div><dt>Registrado</dt><dd>{dataHora(reserva.validadoEm)}</dd></div>}
          {p && (
            <>
              <div><dt>Valor pago</dt><dd>{preco(p.valor - p.valorEstornado)}</dd></div>
              {p.valorEstornado > 0 && <div><dt>Estornado</dt><dd>{preco(p.valorEstornado)}</dd></div>}
            </>
          )}
        </dl>
      </div>

      <button onClick={nova}><Icone nome="qr" /> Validar outra retirada</button>
      <Link href="/doador" className="btn sec">Voltar aos meus lotes</Link>
    </>
  );
}
