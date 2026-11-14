import Icone from "./Icone";

/** Moldura das telas de acesso: faixa verde com a marca e o conteúdo num cartão por cima. */
export default function AuthShell({ titulo, subtitulo, children }: { titulo: string; subtitulo?: string; children: React.ReactNode }) {
  return (
    <div className="auth">
      <header className="auth-topo">
        <div className="marca marca-clara"><Icone nome="folha" tamanho={26} /> ResgatAI</div>
        <p>Menos desperdício, mais comida no prato de quem precisa.</p>
      </header>
      <section className="auth-cartao">
        <h1>{titulo}</h1>
        {subtitulo && <p className="sub">{subtitulo}</p>}
        {children}
      </section>
    </div>
  );
}
