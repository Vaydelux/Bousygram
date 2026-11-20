const scope = self.registration.scope;
const cachePrefix = `bousygram-preview:${new URL(scope).pathname}:`;
const cacheName = `${cachePrefix}v2`;
const shell = ["./", "./index.html", "./styles.css", "./theme.mjs", "./player.js", "./icon.png", "./qr.svg", "./manifest.webmanifest"]
  .map(path => new URL(path, scope).href);

self.addEventListener("install", event => {
  event.waitUntil(caches.open(cacheName).then(cache => cache.addAll(shell)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", event => {
  event.waitUntil(Promise.all([
    self.clients.claim(),
    caches.keys().then(keys => Promise.all(keys
      .filter(key => key.startsWith(cachePrefix) && key !== cacheName)
      .map(key => caches.delete(key)))),
  ]));
});

self.addEventListener("fetch", event => {
  const { request } = event;
  if (request.method !== "GET" || !shell.includes(request.url)) return;
  event.respondWith(fetch(request).catch(async () => {
    const cache = await caches.open(cacheName);
    return (await cache.match(request)) ?? Response.error();
  }));
});
