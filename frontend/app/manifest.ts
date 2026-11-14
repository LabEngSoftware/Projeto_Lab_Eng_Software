import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "ResgatAI",
    short_name: "ResgatAI",
    description: "Resgate de alimentos perecíveis entre estabelecimentos e instituições",
    start_url: "/",
    display: "standalone",
    background_color: "#f5f4ef",
    theme_color: "#17694a",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" }],
  };
}
