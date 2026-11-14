"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { inicio, useUsuario } from "@/lib/api";

export default function Home() {
  const router = useRouter();
  const usuario = useUsuario();
  useEffect(() => {
    if (usuario) router.replace(inicio(usuario));
  }, [usuario, router]);
  return <p>Carregando…</p>;
}
