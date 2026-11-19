/** @type {import('tailwindcss').Config} */
// Los colores salen de las variables de src/index.css (única fuente); aquí solo se nombran.
const color = (nombre) => `rgb(var(--${nombre}) / <alpha-value>)`

// Las pantallas usan las clases de siempre (slate, red, amber...); aquí cada una apunta a la paleta «libro de plazos».
const verde = { 50: '#eef6f1', 100: '#d8ebdf', 200: '#b2d6bf', 600: color('exito'), 700: color('exito-texto'), 800: '#17402a' }
const azul = { 50: '#eef0fa', 100: '#dde1f5', 200: '#bcc4ec', 600: color('acento'), 700: color('acento-oscuro'), 800: '#1a2362' }

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Public Sans Variable"', 'system-ui', 'sans-serif'],
        serif: ['"Source Serif 4 Variable"', 'Georgia', 'serif'],
      },
      colors: {
        niebla: color('niebla'),
        tinta: { DEFAULT: color('tinta'), suave: color('tinta-suave') },
        acento: { DEFAULT: color('acento'), oscuro: color('acento-oscuro') },
        ambar: color('ambar'),
        exito: { DEFAULT: color('exito'), texto: color('exito-texto') },
        peligro: { DEFAULT: color('peligro'), texto: color('peligro-texto') },
        brand: { ...azul, 900: color('tinta') },
        sky: azul,
        blue: azul,
        // Grises tintados hacia la tinta azul noche; 500 en adelante cumplen AA como texto sobre hoja y escritorio
        slate: {
          50: '#f5f7fa', 100: '#eaeef3', 200: '#d6dce5', 300: '#b3bccb', 400: '#7c879b',
          500: color('tinta-suave'), 600: '#3f4a60', 700: '#2c364b', 800: '#1f293c', 900: color('tinta'),
        },
        red: { 50: '#fdf1ee', 100: '#fadcd5', 200: '#f2bcae', 400: '#d9644d', 500: '#c8452e', 600: color('peligro'), 700: color('peligro-texto'), 800: '#6e1b0f' },
        amber: { 50: '#fbf5e6', 100: '#f5e8c2', 200: '#ebd08a', 300: '#dfb84f', 400: '#d4a02a', 700: '#7a4f00', 800: '#5e3c00', 900: '#452c00' },
        green: verde,
        emerald: verde,
      },
      borderRadius: { campo: 'var(--radio-campo)', panel: 'var(--radio-panel)' },
    },
  },
  plugins: [],
}
