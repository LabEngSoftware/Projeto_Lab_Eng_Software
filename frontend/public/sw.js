// Service worker do PWA: sem cache (os lotes mudam a todo momento); só avisa quando a navegação falha offline.
self.addEventListener("fetch", (e) => {
  if (e.request.mode !== "navigate") return;
  e.respondWith(fetch(e.request).catch(() => new Response(
    "<meta name=viewport content='width=device-width'><p style='font-family:sans-serif;padding:24px'>Sem conexão. Tente de novo quando estiver online.</p>",
    { headers: { "Content-Type": "text/html; charset=utf-8" } },
  )));
});
