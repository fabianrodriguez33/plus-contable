/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: { 50: '#eff6ff', 100: '#dbeafe', 600: '#1d4ed8', 700: '#1e40af', 900: '#0f2a4a' },
      },
    },
  },
  plugins: [],
}
