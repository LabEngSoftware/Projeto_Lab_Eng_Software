import type { NomeIcone } from "@/components/Icone";

export const CATEGORIAS: Record<string, { nome: string; emoji: string; fundo: string }> = {
  PADARIA: { nome: "Padaria", emoji: "🥖", fundo: "#fbeedd" },
  HORTIFRUTI: { nome: "Hortifrúti", emoji: "🥦", fundo: "#e3f3e3" },
  LATICINIOS: { nome: "Laticínios", emoji: "🧀", fundo: "#fff5cc" },
  CARNES: { nome: "Carnes", emoji: "🥩", fundo: "#fde4e1" },
  REFEICOES_PRONTAS: { nome: "Refeições", emoji: "🍱", fundo: "#fdeedc" },
  BEBIDAS: { nome: "Bebidas", emoji: "🧃", fundo: "#e1effc" },
  MERCEARIA: { nome: "Mercearia", emoji: "🥫", fundo: "#efe7f8" },
  OUTROS: { nome: "Outros", emoji: "📦", fundo: "#ecebe6" },
};

export const UNIDADES: Record<string, string> = { UNIDADE: "un.", KG: "kg", LITRO: "L", PACOTE: "pct.", CAIXA: "cx." };

export const STATUS_RESERVA: Record<string, string> = {
  PENDENTE: "Aguardando retirada",
  COLETADA: "Retirada",
  CANCELADA: "Cancelada",
  EXPIRADA: "Expirada",
};

export const STATUS_LOTE: Record<string, string> = {
  DISPONIVEL: "Disponível",
  RESERVADO_PENDENTE: "Reservado",
  COLETADO: "Retirado",
  CANCELADO: "Cancelado",
  EXPIRADO: "Expirado",
};

export const STATUS_ANALISE: Record<string, string> = { PENDENTE: "Em análise", APROVADA: "Aprovada", REJEITADA: "Rejeitada" };

export const PERFIS: Record<string, string> = { ADMIN: "Administrador", ESTABELECIMENTO: "Doador", INSTITUICAO: "Instituição" };

export const ACOES: Record<string, { texto: string; icone: NomeIcone }> = {
  USUARIO_CRIADO: { texto: "Usuário criado", icone: "usuarios" },
  USUARIO_ATUALIZADO: { texto: "Usuário atualizado", icone: "editar" },
  USUARIO_INATIVADO: { texto: "Usuário inativado", icone: "alerta" },
  INSTITUICAO_APROVADA: { texto: "Instituição aprovada", icone: "check" },
  INSTITUICAO_REJEITADA: { texto: "Instituição rejeitada", icone: "alerta" },
  LOTE_CRIADO: { texto: "Lote publicado", icone: "caixa" },
  LOTE_EXPIRADO: { texto: "Lote expirado", icone: "relogio" },
  RETIRADA_VALIDADA: { texto: "Retirada validada", icone: "check" },
  RETIRADA_CANCELADA: { texto: "Retirada cancelada", icone: "alerta" },
  RESERVA_EXPIRADA: { texto: "Reserva expirada", icone: "relogio" },
};

/** 00.000.000/0000-00 */
export const cnpj = (d: string | null) => d?.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5") ?? "";

export const hora = (iso: string) => new Date(iso).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });

const dia = (iso: string) => new Date(iso).toDateString();

/** "hoje, 18:30", "amanhã, 09:00" ou "14 de nov., 10:00". */
export function dataHora(iso: string) {
  const amanha = new Date(Date.now() + 86_400_000).toISOString();
  const d = dia(iso) === dia(new Date().toISOString()) ? "hoje"
    : dia(iso) === dia(amanha) ? "amanhã"
    : new Date(iso).toLocaleDateString("pt-BR", { day: "numeric", month: "short" });
  return `${d}, ${hora(iso)}`;
}

/** Intervalo sem repetir o dia: "hoje, 18:30 às 21:00". */
export const janela = (inicio: string, fim: string) =>
  `${dataHora(inicio)} às ${dia(inicio) === dia(fim) ? hora(fim) : dataHora(fim)}`;

export const preco = (valor: number) =>
  valor > 0 ? valor.toLocaleString("pt-BR", { style: "currency", currency: "BRL" }) : "Grátis";

/** ISO de daqui a `h` horas. */
export const emHoras = (h: number) => new Date(Date.now() + h * 3_600_000).toISOString();

export const passou = (iso: string) => Date.parse(iso) < Date.now();

/** "em 3 h", "em 45 min", "em 2 dias" ou "encerrado". */
export function restante(iso: string) {
  const min = Math.round((new Date(iso).getTime() - Date.now()) / 60_000);
  if (min <= 0) return "encerrado";
  if (min < 60) return `em ${min} min`;
  if (min < 48 * 60) return `em ${Math.round(min / 60)} h`;
  return `em ${Math.round(min / 1440)} dias`;
}
