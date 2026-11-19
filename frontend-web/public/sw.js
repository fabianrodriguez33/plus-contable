// HU-34: guarda la cáscara de la app (páginas y archivos propios) para poder abrirla sin internet.
// Nunca guarda /api: los datos del cliente viven en su copia local y se borran al cerrar sesión.
// El nombre sale de la versión con que se registró (?v=), así una versión nueva no sirve archivos de la anterior
const CACHE = `pc-shell-${new URL(self.location.href).searchParams.get('v') ?? '0'}`

self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((claves) => Promise.all(claves.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()))
})

self.addEventListener('fetch', (e) => {
  const req = e.request
  const url = new URL(req.url)
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return
  // Toda ruta de la app es la misma página: se guarda y se sirve como «/»
  const clave = req.mode === 'navigate' ? '/' : req
  e.respondWith(fetch(req).then((res) => {
    if (res.ok) {
      const copia = res.clone()
      caches.open(CACHE).then((c) => c.put(clave, copia))
    }
    return res
  }).catch(() => caches.match(clave).then((r) => r || Response.error())))
})
