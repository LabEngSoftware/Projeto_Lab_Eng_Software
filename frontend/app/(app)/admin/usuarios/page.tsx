import Usuarios from "./Usuarios";

// os atalhos do painel chegam com ?perfil= ou ?situacao=
export default async function Page({ searchParams }: PageProps<"/admin/usuarios">) {
  const { perfil, situacao } = await searchParams;
  return <Usuarios perfilInicial={typeof perfil === "string" ? perfil : ""} situacaoInicial={typeof situacao === "string" ? situacao : ""} />;
}
