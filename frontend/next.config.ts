import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  devIndicators: false,
  output: "standalone", // imagem Docker só com o necessário para rodar (frontend/Dockerfile)
  // O navegador chama /api/* na mesma origem e o Next repassa ao backend: sem CORS.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${process.env.API_URL ?? "http://localhost:8080"}/:path*` }];
  },
};

export default nextConfig;
