import NovoLote from "./NovoLote";

// ?de=<id> repete um lote anterior (UC02 fluxo 3a)
export default async function Page({ searchParams }: PageProps<"/doador/novo">) {
  const { de } = await searchParams;
  return <NovoLote de={typeof de === "string" ? de : undefined} />;
}
