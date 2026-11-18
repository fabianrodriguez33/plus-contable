import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // HU-34: cada compilación estrena su caché del service worker (ver main.tsx y public/sw.js)
  define: { __BUILD_ID__: JSON.stringify(Date.now().toString(36)) },
  server: { port: 5173 },
})
