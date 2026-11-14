"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import FotoLote from "@/components/FotoLote";
import Icone, { type NomeIcone } from "@/components/Icone";
import { api, ApiError, type Lote, type Reserva } from "@/lib/api";
import { CATEGORIAS, dataHora, hora, janela, preco, restante, UNIDADES } from "@/lib/formato";
import CodigoRetirada from "../../CodigoRetirada";

/** Detalhe do lote → revisão/pagamento (RF04) → reserva confirmada com QR Code e PIN (UC04). */
export default function DetalheLote() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [lote, setLote] = useState<Lote>();
  const [etapa, setEtapa] = useState<"detalhe" | "revisao">("detalhe");
  const [metodo, setMetodo] = useState<"PIX" | "CARTAO">("PIX");
  const [reserva, setReserva] = useState<Reserva>();
  const [erro, setErro] = useState("");
  const [erros, setErros] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    api<Lote>(`/lotes/${id}`).then(setLote, (e) => setErro(e.message));
  }, [id]);

  async function reservar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    setEnviando(true);
    setErro("");
    setErros({});
    try {
      setReserva(await api<Reserva>(`/lotes/${id}/reserva`, {
        method: "POST",
        body: !(lote!.preco > 0) ? undefined : metodo === "PIX" ? { metodo } : {
          metodo,
          cartao: { numero: String(f.numero).replace(/\D/g, ""), titular: f.titular, validade: f.validade, cvv: f.cvv },
        },
      }));
      scrollTo(0, 0);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.erros).length) setErros(err.erros);
      else setErro((err as Error).message);
      setEnviando(false);
    }
  }

  if (reserva) {
    return (
      <>
        <div className="sucesso">
          <span className="icone-bola grande"><Icone nome="check" tamanho={36} /></span>
          <h1>Reserva confirmada!</h1>
          <p className="sub">
            {reserva.lote.nome} é seu. {reserva.pagamento &&
              `Pagamento de ${preco(reserva.pagamento.valor)} aprovado via ${reserva.pagamento.metodo === "PIX" ? "PIX" : `cartão final ${reserva.pagamento.finalCartao}`}.`}
          </p>
        </div>
        <CodigoRetirada reserva={reserva} />
        <Link href="/receptor/reservas" className="btn">Ver minhas reservas</Link>
        <Link href="/receptor" className="btn sec">Voltar aos lotes</Link>
      </>
    );
  }

  if (!lote) return erro ? <p className="erro">{erro}</p> : <p className="carregando">Carregando…</p>;
  const c = CATEGORIAS[lote.categoria];
  const pago = lote.preco > 0;

  return (
    <>
      <div className="capa-wrap">
        <FotoLote lote={lote} className="capa" />
        <button className="voltar" aria-label="Voltar" onClick={() => (etapa === "revisao" ? setEtapa("detalhe") : router.back())}>
          <Icone nome="voltar" />
        </button>
      </div>

      <div className="folha-detalhe">
        <div className="pilha" style={{ gap: 6 }}>
          <div className="linha">
            <span className={`selo ${lote.urgencia}`}><Icone nome="relogio" tamanho={12} /> Vence {restante(lote.validade)}</span>
            <span className="discreto">{c.emoji} {c.nome}</span>
          </div>
          <div className="linha" style={{ alignItems: "flex-start" }}>
            <h1>{lote.nome}</h1>
            <h1 className={`preco ${pago ? "pago" : ""}`}>{preco(lote.preco)}</h1>
          </div>
          <p className="sub">{lote.estabelecimentoNome}</p>
        </div>

        {etapa === "detalhe" ? (
          <>
            <div className="cartao info">
              <Info icone="caixa" titulo={`${lote.quantidade} ${UNIDADES[lote.unidade]}`} texto={`Validade: ${dataHora(lote.validade)}`} />
              <Info icone="relogio" titulo="Janela de retirada" texto={janela(lote.retiradaInicio, lote.retiradaFim)} />
              <Info icone="pino" titulo={lote.distanciaKm != null ? `${lote.distanciaKm} km de você` : "Endereço"} texto={lote.enderecoRetirada ?? ""} />
            </div>
            {lote.observacoes && (
              <div className="cartao">
                <h2 style={{ fontSize: 15, marginBottom: 6 }}>Observações do doador</h2>
                <p className="sub">{lote.observacoes}</p>
              </div>
            )}
            {lote.status === "DISPONIVEL" ? (
              <div className="barra-acao"><button onClick={() => setEtapa("revisao")}>Reservar lote</button></div>
            ) : (
              <p className="erro centro">Este lote não está mais disponível.</p>
            )}
          </>
        ) : (
          <form onSubmit={reservar}>
            <div className="cartao pilha">
              <h2>Revisão</h2>
              <Info icone="caixa" titulo={`${lote.quantidade} ${UNIDADES[lote.unidade]} de ${lote.nome}`} texto={lote.estabelecimentoNome} />
              <Info icone="pino" titulo="Retirada" texto={`${lote.enderecoRetirada} · até ${hora(lote.retiradaFim)}`} />
              <p className="discreto">
                O lote fica reservado só para a sua instituição. Se não for retirado no prazo, a reserva expira e a
                instituição perde pontos de reputação.
              </p>
              <div className="total"><span className="sub">Total</span><strong>{preco(lote.preco)}</strong></div>
            </div>

            {pago && (
              <div className="cartao pilha">
                <h2>Pagamento</h2>
                <Opcao ativa={metodo === "PIX"} icone="pix" titulo="PIX" texto="Aprovado na hora" onClick={() => setMetodo("PIX")} />
                <Opcao ativa={metodo === "CARTAO"} icone="cartao" titulo="Cartão de crédito" texto="Visa, Master, Elo" onClick={() => setMetodo("CARTAO")} />
                {metodo === "CARTAO" && (
                  <>
                    <label>Número do cartão
                      <input name="numero" inputMode="numeric" autoComplete="cc-number" placeholder="0000 0000 0000 0000" required />
                      {erros["cartao.numero"] && <span className="erro">{erros["cartao.numero"]}</span>}
                    </label>
                    <label>Nome impresso<input name="titular" autoComplete="cc-name" required /></label>
                    <div className="duas">
                      <label>Validade
                        <input name="validade" placeholder="MM/AA" autoComplete="cc-exp" required />
                        {erros["cartao.validade"] && <span className="erro">{erros["cartao.validade"]}</span>}
                      </label>
                      <label>CVV
                        <input name="cvv" inputMode="numeric" autoComplete="cc-csc" placeholder="123" required />
                        {erros["cartao.cvv"] && <span className="erro">{erros["cartao.cvv"]}</span>}
                      </label>
                    </div>
                  </>
                )}
                <p className="discreto">Pagamento simulado para fins acadêmicos.</p>
              </div>
            )}

            {erro && <p className="erro centro">{erro}</p>}
            <div className="barra-acao">
              <button disabled={enviando}>
                {enviando ? "Reservando…" : pago ? `Pagar ${preco(lote.preco)} e reservar` : "Confirmar reserva"}
              </button>
            </div>
          </form>
        )}
      </div>
    </>
  );
}

function Info({ icone, titulo, texto }: { icone: NomeIcone; titulo: string; texto: string }) {
  return (
    <div className="info-item">
      <span className="icone-bola"><Icone nome={icone} tamanho={18} /></span>
      <div><strong>{titulo}</strong><span>{texto}</span></div>
    </div>
  );
}

function Opcao({ ativa, icone, titulo, texto, onClick }: { ativa: boolean; icone: NomeIcone; titulo: string; texto: string; onClick: () => void }) {
  return (
    <button type="button" className={`opcao ${ativa ? "ativa" : ""}`} onClick={onClick}>
      <span className="icone-bola" style={{ width: 40, height: 40 }}><Icone nome={icone} /></span>
      <span>{titulo}<small>{texto}</small></span>
      <span className="radio" />
    </button>
  );
}
