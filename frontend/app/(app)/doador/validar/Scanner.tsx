"use client";

import jsQR from "jsqr";
import { useEffect, useRef, useState } from "react";

/** Lê o QR Code da instituição pela câmera traseira; chama onCodigo uma vez com o conteúdo lido. */
export default function Scanner({ onCodigo }: { onCodigo: (codigo: string) => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    let parar = false;
    let quadro = 0;
    let stream: MediaStream | undefined;
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d", { willReadFrequently: true })!;

    function ler() {
      const v = video.current;
      if (parar || !v) return;
      if (v.readyState === v.HAVE_ENOUGH_DATA && v.videoWidth) {
        // reduz o quadro para a leitura ficar leve no celular
        const escala = Math.min(1, 480 / v.videoWidth);
        canvas.width = v.videoWidth * escala;
        canvas.height = v.videoHeight * escala;
        ctx.drawImage(v, 0, 0, canvas.width, canvas.height);
        const qr = jsQR(ctx.getImageData(0, 0, canvas.width, canvas.height).data, canvas.width, canvas.height);
        if (qr?.data) return onCodigo(qr.data);
      }
      quadro = requestAnimationFrame(ler);
    }

    // getUserMedia só existe em HTTPS ou localhost
    (navigator.mediaDevices?.getUserMedia({ video: { facingMode: "environment" } }) ?? Promise.reject()).then(
      (s) => {
        if (parar) return s.getTracks().forEach((t) => t.stop());
        stream = s;
        video.current!.srcObject = s;
        video.current!.play();
        quadro = requestAnimationFrame(ler);
      },
      () => setErro("Não foi possível abrir a câmera. Permita o acesso no navegador ou digite o PIN."),
    );

    return () => {
      parar = true;
      cancelAnimationFrame(quadro);
      stream?.getTracks().forEach((t) => t.stop());
    };
  }, [onCodigo]);

  return (
    <div className="camera">
      <video ref={video} muted playsInline />
      {erro ? <p className="aviso">{erro}</p> : <div className="mira" />}
    </div>
  );
}
