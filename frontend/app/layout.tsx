import type { Metadata, Viewport } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import Script from "next/script";
import "./globals.css";

const fonte = Plus_Jakarta_Sans({ subsets: ["latin"], variable: "--fonte" });

export const metadata: Metadata = {
  title: "ResgatAI",
  description: "Resgate de alimentos perecíveis entre estabelecimentos e instituições",
  appleWebApp: { capable: true, title: "ResgatAI" },
};

export const viewport: Viewport = { themeColor: "#17694a" };

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="pt-BR" className={fonte.variable}>
      <body>
        <main>{children}</main>
        <Script id="sw">{`if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js")`}</Script>
      </body>
    </html>
  );
}
