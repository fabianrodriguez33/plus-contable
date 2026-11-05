import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

/**
 * Contraste AA de los tokens del sistema de diseño (WCAG 2.1: 4.5:1 para texto normal, 3:1 para texto grande y
 * componentes de interfaz). Lee las variables de src/index.css, así que un cambio de color que rompa el contraste
 * hace fallar esta prueba.
 */
type Rgb = [number, number, number]

const css = readFileSync(new URL('../index.css', import.meta.url), 'utf-8')

const canal = (nombre: string): Rgb => {
  const m = css.match(new RegExp(String.raw`--${nombre}:\s*(\d+)\s+(\d+)\s+(\d+)`))
  if (!m) throw new Error(`Falta el token --${nombre}`)
  return [Number(m[1]), Number(m[2]), Number(m[3])]
}

const luminancia = ([r, g, b]: Rgb) => {
  const [R, G, B] = [r, g, b].map((v) => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * R + 0.7152 * G + 0.0722 * B
}

const contraste = (a: Rgb, b: Rgb) => {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x)
  return (claro + 0.05) / (oscuro + 0.05)
}

/** Color `frente` con opacidad `alfa` sobre `fondo`. */
const mezclar = (frente: Rgb, alfa: number, fondo: Rgb): Rgb =>
  frente.map((v, i) => Math.round(v * alfa + fondo[i] * (1 - alfa))) as Rgb

const BLANCO: Rgb = [255, 255, 255]
const niebla = canal('niebla')
const tinta = canal('tinta')
const suave = canal('tinta-suave')
const acento = canal('acento')
const acentoOscuro = canal('acento-oscuro')
const ambar = canal('ambar')
const exito = canal('exito')
const exitoTexto = canal('exito-texto')
const peligro = canal('peligro')
const peligroTexto = canal('peligro-texto')

// Fondo más oscuro de la página: el más oscuro entre cada brillo verde azulado sobre el degradado de su zona y el último tramo
const oscuridad = (c: Rgb) => luminancia(c)
const peorFondo = [
  mezclar(acento, 0.2, [0xea, 0xf2, 0xf1]),
  mezclar(acento, 0.12, [0xdc, 0xeb, 0xea]),
  [0xd2, 0xe7, 0xe8] as Rgb,
].sort((x, y) => oscuridad(x) - oscuridad(y))[0]
// Vidrio (blanco al 62 %) sobre ese peor fondo
const vidrio = mezclar(BLANCO, 0.62, peorFondo)

const TEXTO = 4.5

describe('tokens de color: contraste AA', () => {
  it.each([
    ['tinta sobre niebla', tinta, niebla],
    ['tinta sobre el peor fondo de la página', tinta, peorFondo],
    ['tinta sobre vidrio', tinta, vidrio],
    ['tinta suave sobre niebla', suave, niebla],
    ['tinta suave sobre el peor fondo de la página', suave, peorFondo],
    ['tinta suave sobre vidrio', suave, vidrio],
    ['tinta suave sobre blanco', suave, BLANCO],
    ['enlace (acento oscuro) sobre niebla', acentoOscuro, niebla],
    ['enlace (acento oscuro) sobre vidrio', acentoOscuro, vidrio],
    ['texto de éxito sobre vidrio', exitoTexto, vidrio],
    ['texto de éxito sobre su aviso (verde claro)', exitoTexto, [0xf0, 0xf8, 0xf3]],
    ['texto de peligro sobre vidrio', peligroTexto, vidrio],
    ['texto de peligro sobre su aviso (rojo claro)', peligroTexto, [0xff, 0xf4, 0xef]],
    ['blanco sobre el botón de acción', BLANCO, acento],
    ['blanco sobre el botón de acción al pasar el cursor', BLANCO, acentoOscuro],
    ['tinta sobre una insignia ámbar', tinta, ambar],
  ] as [string, Rgb, Rgb][])('%s ≥ 4.5', (_nombre, frente, fondo) => {
    expect(contraste(frente, fondo)).toBeGreaterThanOrEqual(TEXTO)
  })

  it('el peligro puro (#C2410C) sirve como borde y botón: blanco sobre peligro y peligro sobre vidrio', () => {
    expect(contraste(BLANCO, peligro)).toBeGreaterThanOrEqual(TEXTO)
    expect(contraste(peligro, vidrio)).toBeGreaterThanOrEqual(3)
  })

  it('el éxito puro (#2F855A) solo se usa en fondos con texto blanco o en íconos, no como texto', () => {
    expect(contraste(BLANCO, exito)).toBeGreaterThanOrEqual(TEXTO)
  })

  it('el ámbar es solo un acento: sobre vidrio no alcanza para texto (por eso nunca se usa como texto)', () => {
    expect(contraste(ambar, vidrio)).toBeLessThan(3)
  })

  it('el panel de vidrio traduce a un fondo casi opaco cuando se pide menos transparencia o no hay desenfoque', () => {
    expect(css).toMatch(/@media \(prefers-reduced-transparency: reduce\)/)
    expect(css).toMatch(/@supports not \(\(backdrop-filter: blur\(1px\)\) or \(-webkit-backdrop-filter: blur\(1px\)\)\)/)
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)/)
  })
})
