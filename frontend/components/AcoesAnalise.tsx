"use client";

import { useState } from "react";
import Icone from "@/components/Icone";
import { api } from "@/lib/api";

/** RF06: o admin aprova ou rejeita (com motivo) o cadastro de uma instituição. */
export default function AcoesAnalise({ id, rejeitada = false, depois }: { id: number; rejeitada?: boolean; depois: () => void }) {
  const [rejeitando, setRejeitando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");

  async function enviar(caminho: string, body?: unknown) {
    setEnviando(true);
    setErro("");
    try {
      await api(`/admin/usuarios/${id}/${caminho}`, { method: "POST", body });
      depois();
    } catch (e) {
      setErro((e as Error).message);
      setEnviando(false);
    }
  }

  if (rejeitando) {
    return (
      <form onSubmit={(e) => { e.preventDefault(); enviar("rejeitar", { motivo: new FormData(e.currentTarget).get("motivo") }); }}>
        <label>Motivo da rejeição
          <textarea name="motivo" rows={3} maxLength={500} placeholder="Ex.: CNPJ não corresponde a uma entidade beneficente" required />
        </label>
        {erro && <p className="erro">{erro}</p>}
        <button className="perigo mini" disabled={enviando}>Confirmar rejeição</button>
        <button type="button" className="fantasma" onClick={() => setRejeitando(false)}>Cancelar</button>
      </form>
    );
  }

  return (
    <div className="pilha" style={{ gap: 8 }}>
      <div className="duas">
        <button className="mini" disabled={enviando} onClick={() => enviar("aprovar")}><Icone nome="check" tamanho={16} /> Aprovar</button>
        {!rejeitada && <button className="perigo mini" disabled={enviando} onClick={() => setRejeitando(true)}><Icone nome="x" tamanho={16} /> Rejeitar</button>}
      </div>
      {erro && <p className="erro">{erro}</p>}
    </div>
  );
}
