"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

export type Perfil = "ADMIN" | "ESTABELECIMENTO" | "INSTITUICAO";

export type Usuario = {
  id: number;
  perfil: Perfil;
  nome: string;
  documento: string;
  email: string;
  telefone: string;
  endereco: string;
  coordenadas: Coordenadas | null;
  ativo: boolean;
  score: number;
  statusAnalise: "PENDENTE" | "APROVADA" | "REJEITADA" | null;
  criadoEm: string;
};

export type Coordenadas = { latitude: number; longitude: number };

export type Lote = {
  id: number;
  estabelecimentoNome: string;
  nome: string;
  categoria: string;
  quantidade: number;
  unidade: string;
  preco: number;
  validade: string;
  retiradaInicio: string;
  retiradaFim: string;
  observacoes: string | null;
  enderecoRetirada: string | null;
  coordenadas: Coordenadas | null;
  status: "DISPONIVEL" | "RESERVADO_PENDENTE" | "COLETADO" | "CANCELADO" | "EXPIRADO";
  urgencia: "ALTA" | "MEDIA" | "BAIXA";
  distanciaKm: number | null;
  fotoUrl: string | null;
};

export type Reserva = {
  id: number;
  instituicaoNome: string;
  atrasada: boolean;
  pin: string | null;
  qrCode: string | null;
  prazoRetirada: string;
  status: "PENDENTE" | "COLETADA" | "CANCELADA" | "EXPIRADA";
  criadoEm: string;
  validadoEm: string | null;
  quantidadeEntregue: number | null;
  motivoCancelamento: string | null;
  pagamento: { metodo: "PIX" | "CARTAO"; valor: number; valorEstornado: number; finalCartao: string | null; status: string } | null;
  lote: Lote;
};

/** Página do Spring Data (PagedModel). */
export type Pagina<T> = { content: T[]; page: { totalElements: number } };

export type Impacto = { retiradas: number; quantidades: Record<string, number> };

export type Notificacao = { id: number; titulo: string; mensagem: string; link: string | null; lida: boolean; criadoEm: string };

export type Auditoria = {
  id: number;
  autorId: number | null;
  acao: string;
  entidade: string;
  entidadeId: number;
  detalhes: string | null;
  criadoEm: string;
};

/** Erro no formato Problem Details do backend; `erros` traz a mensagem de cada campo inválido. */
export class ApiError extends Error {
  constructor(public status: number, message: string, public erros: Record<string, string> = {}) {
    super(message);
  }
}

const TOKEN = "token";
export const getToken = () => localStorage.getItem(TOKEN);
export const setToken = (t: string) => localStorage.setItem(TOKEN, t);
export const sair = () => localStorage.removeItem(TOKEN);

/** Chama o backend via /api (rewrite do next.config). Objetos viram JSON; FormData vai como multipart. */
export async function api<T = unknown>(path: string, init: Omit<RequestInit, "body"> & { body?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let body = init.body as BodyInit | undefined;
  if (init.body !== undefined && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.body);
  }

  const res = await fetch(`/api${path}`, { ...init, headers, body });
  if (res.status === 401 && token) {
    sair();
    // fora de componente não há router; recarregar também limpa o estado da sessão expirada
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    location.href = "/login";
  }
  if (!res.ok) {
    const p = await res.json().catch(() => ({}));
    throw new ApiError(res.status, p.detail ?? "Não foi possível completar a operação", p.erros);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

/** Tela inicial de cada perfil; instituição ainda não aprovada fica em "conta em análise". */
export function inicio(u: Usuario) {
  if (u.perfil === "ESTABELECIMENTO") return "/doador";
  if (u.perfil === "ADMIN") return "/admin";
  return u.statusAnalise === "APROVADA" ? "/receptor" : "/em-analise";
}

/** Usuário logado para páginas protegidas; sem token, manda para o login. */
export function useUsuario() {
  const router = useRouter();
  const [usuario, setUsuario] = useState<Usuario>();
  useEffect(() => {
    if (!getToken()) return router.replace("/login");
    api<Usuario>("/auth/me").then(setUsuario);
  }, [router]);
  return usuario;
}

/** Lista paginada do backend: acumula as páginas conforme `maisUm` é chamado; trocar o path recomeça da primeira. */
export function usePaginado<T>(path: string, size = 20) {
  const [estado, setEstado] = useState<{ path: string; itens: T[]; total: number; proxima: number }>();
  const [erro, setErro] = useState("");

  const buscar = useCallback((p: number) => api<Pagina<T>>(`${path}${path.includes("?") ? "&" : "?"}size=${size}&page=${p}`), [path, size]);
  const aplicar = useCallback((p: number, r: Pagina<T>) => {
    setErro("");
    setEstado((e) => ({
      path, total: r.page.totalElements, proxima: p + 1,
      itens: p === 0 || e?.path !== path ? r.content : [...e.itens, ...r.content],
    }));
  }, [path]);

  useEffect(() => {
    let vivo = true;
    buscar(0).then((r) => vivo && aplicar(0, r), (e) => vivo && setErro(e.message));
    return () => { vivo = false; };
  }, [buscar, aplicar]);

  const carregar = (p: number) => buscar(p).then((r) => aplicar(p, r), (e) => setErro(e.message));
  const atual = estado?.path === path ? estado : undefined;
  return {
    itens: atual?.itens,
    total: atual?.total ?? 0,
    erro,
    maisUm: atual && atual.itens.length < atual.total ? () => carregar(atual.proxima) : undefined,
    recarregar: () => carregar(0),
  };
}
