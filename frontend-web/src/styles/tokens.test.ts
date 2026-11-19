import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

/**
 * Contraste AA de los tokens del sistema de diseño «libro de plazos» (WCAG 2.1: 4.5:1 para texto normal, 3:1 para texto
 * grande y componentes de interfaz). Lee las variables de src/index.css, así que un cambio de color que rompa el contraste
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

// Fondos claros de los avisos y sellos (los de tailwind.config.js)
const VERDE_CLARO: Rgb = [0xee, 0xf6, 0xf1]
const ROJO_CLARO: Rgb = [0xfd, 0xf1, 0xee]
const AZUL_CLARO: Rgb = [0xee, 0xf0, 0xfa]
const AMBAR_CLARO: Rgb = [0xfb, 0xf5, 0xe6]
const AMBAR_TEXTO: Rgb = [0x7a, 0x4f, 0x00]

const TEXTO = 4.5

describe('tokens de color: contraste AA', () => {
  it.each([
    ['tinta sobre el escritorio', tinta, niebla],
    ['tinta sobre la hoja', tinta, BLANCO],
    ['tinta suave sobre el escritorio', suave, niebla],
    ['tinta suave sobre la hoja', suave, BLANCO],
    ['enlace (acento oscuro) sobre el escritorio', acentoOscuro, niebla],
    ['enlace (acento oscuro) sobre la hoja', acentoOscuro, BLANCO],
    ['enlace sobre su aviso (azul claro)', acentoOscuro, AZUL_CLARO],
    ['texto de éxito sobre la hoja', exitoTexto, BLANCO],
    ['texto de éxito sobre su aviso (verde claro)', exitoTexto, VERDE_CLARO],
    ['texto de peligro sobre la hoja', peligroTexto, BLANCO],
    ['texto de peligro sobre su aviso (rojo claro)', peligroTexto, ROJO_CLARO],
    ['texto ámbar (por vencer) sobre su aviso (ámbar claro)', AMBAR_TEXTO, AMBAR_CLARO],
    ['texto ámbar sobre la hoja', AMBAR_TEXTO, BLANCO],
    ['blanco sobre el botón de acción', BLANCO, acento],
    ['blanco sobre el botón de acción al pasar el cursor', BLANCO, acentoOscuro],
    ['blanco sobre el sello rojo de vencida', BLANCO, peligro],
    ['tinta sobre una insignia ámbar', tinta, ambar],
  ] as [string, Rgb, Rgb][])('%s ≥ 4.5', (_nombre, frente, fondo) => {
    expect(contraste(frente, fondo)).toBeGreaterThanOrEqual(TEXTO)
  })

  it('el rojo de sello sirve también como borde del sello de plazo: ≥ 3 sobre la hoja', () => {
    expect(contraste(peligro, BLANCO)).toBeGreaterThanOrEqual(3)
  })

  it('el éxito puro solo se usa en fondos con texto blanco o en íconos, no como texto', () => {
    expect(contraste(BLANCO, exito)).toBeGreaterThanOrEqual(TEXTO)
  })

  it('el ámbar decorativo no alcanza para texto sobre la hoja (por eso nunca se usa como texto)', () => {
    expect(contraste(ambar, BLANCO)).toBeLessThan(3)
  })

  it('respeta el menor movimiento que pida el usuario', () => {
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)/)
  })
})
