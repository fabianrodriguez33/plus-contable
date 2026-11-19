/** @type {import('tailwindcss').Config} */
// Los colores salen de las variables de src/index.css (única fuente); aquí solo se nombran.
const color = (nombre) => `rgb(var(--${nombre}) / <alpha-value>)`

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        niebla: color('niebla'),
        tinta: { DEFAULT: color('tinta'), suave: color('tinta-suave') },
        acento: { DEFAULT: color('acento'), oscuro: color('acento-oscuro') },
        ambar: color('ambar'),
        exito: { DEFAULT: color('exito'), texto: color('exito-texto') },
        peligro: { DEFAULT: color('peligro'), texto: color('peligro-texto') },
        // La paleta de la marca pasa del azul al verde azulado; se conserva el nombre para no tocar las pantallas aún no rediseñadas
        brand: { 50: '#eef7f6', 100: '#d5ebea', 600: color('acento'), 700: color('acento-oscuro'), 900: color('tinta') },
        // Grises tintados hacia la tinta; 500 en adelante cumplen AA como texto sobre vidrio y niebla
        slate: {
          50: '#f3f7f7', 100: '#e8efef', 200: '#d6e1e2', 300: '#b6c6c9', 400: '#6b8087',
          500: color('tinta-suave'), 600: '#3c565d', 700: '#2b444c', 800: '#1e3841', 900: color('tinta'),
        },
        red: { 50: '#fff4ef', 100: '#ffe3d6', 200: '#fdc7ad', 400: '#e8793f', 500: '#d4561f', 600: color('peligro'), 700: color('peligro-texto') },
        green: { 50: '#f0f8f3', 100: '#d9eee2', 200: '#b5dcc4', 600: color('exito'), 700: color('exito-texto') },
      },
      borderRadius: { campo: 'var(--radio-campo)', panel: 'var(--radio-panel)' },
    },
  },
  plugins: [],
}
