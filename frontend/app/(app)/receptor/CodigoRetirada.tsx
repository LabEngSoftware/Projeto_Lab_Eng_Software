"use client";

import QRCode from "qrcode";
import { useEffect, useState } from "react";
import Icone from "@/components/Icone";
import type { Reserva } from "@/lib/api";
import { dataHora, restante } from "@/lib/formato";

/** QR Code e PIN que a instituição apresenta ao doador na retirada (UC06), em forma de ticket. */
export default function CodigoRetirada({ reserva, solto = false }: { reserva: Reserva; solto?: boolean }) {
  const [qr, setQr] = useState<string>();
  useEffect(() => {
    if (reserva.qrCode) QRCode.toDataURL(reserva.qrCode, { width: 480, margin: 1, color: { dark: "#0e3b2c" } }).then(setQr);
  }, [reserva.qrCode]);

  return (
    <div className={solto ? "ticket" : "cartao ticket"}>
      <p className="discreto centro" style={{ marginBottom: 12 }}>Mostre o QR Code ao doador na retirada</p>
      {/* eslint-disable-next-line @next/next/no-img-element -- data URL gerada no navegador */}
      {qr ? <img className="qr" src={qr} alt="QR Code da retirada" /> : <div className="qr" />}
      <div className="corte" />
      <p className="discreto centro">ou informe o PIN</p>
      <div className="pin">{reserva.pin?.split("").map((d, i) => <span key={i}>{d}</span>)}</div>
      <div className="pilha" style={{ marginTop: 16, gap: 10 }}>
        <div className="prazo"><Icone nome="relogio" tamanho={16} /> Retire até {dataHora(reserva.prazoRetirada)} ({restante(reserva.prazoRetirada)})</div>
        <p className="discreto centro"><Icone nome="pino" tamanho={13} /> {reserva.lote.enderecoRetirada}</p>
      </div>
    </div>
  );
}
