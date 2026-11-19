import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  // Despliegue: sin estas variables la web llamaria a localhost. Solo avisa; no corta la compilacion.
  if (mode === 'production') {
    const env = { ...loadEnv(mode, process.cwd(), 'VITE_'), ...process.env }
    for (const v of ['VITE_AUTH_URL', 'VITE_TRIBUTARIO_URL']) {
      if (!env[v]) console.warn(`
[aviso] ${v} no esta definida: la web publicada llamaria a localhost (ver docs/DESPLIEGUE.md)
`)
    }
  }
  return {
    plugins: [react()],
    // HU-34: cada compilación estrena su caché del service worker (ver main.tsx y public/sw.js)
    define: { __BUILD_ID__: JSON.stringify(Date.now().toString(36)) },
    server: { port: 5173 },
  }
})
