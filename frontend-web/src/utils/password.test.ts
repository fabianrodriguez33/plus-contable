import { describe, expect, it } from 'vitest'
import { PASSWORD_MAXIMO, PASSWORD_MINIMO, validarPasswordNueva } from './password'

const CORREO = 'contacto@acme.pe'
const ok = (p: string, r = p) => validarPasswordNueva(p, r, CORREO)

describe('validarPasswordNueva (HU-37: de 8 a 72 caracteres)', () => {
  it('una contraseña válida que se repite igual no tiene problemas', () => {
    expect(ok('Valor-ficticio-1')).toBeNull()
  })

  it('vacía se pide', () => {
    expect(ok('')).toBe('Escriba la contraseña nueva')
  })

  it('con siete caracteres se rechaza y con ocho se acepta', () => {
    expect(ok('Abcdef1')).toBe('La contraseña nueva debe tener de 8 a 72 caracteres')
    expect(ok('Abcdefg1')).toBeNull()
    expect(PASSWORD_MINIMO).toBe(8)
  })

  it('con setenta y dos se acepta y con setenta y tres se rechaza', () => {
    expect(ok('a'.repeat(72))).toBeNull()
    expect(ok('a'.repeat(73))).toBe('La contraseña nueva debe tener de 8 a 72 caracteres')
    expect(PASSWORD_MAXIMO).toBe(72)
  })

  it('el máximo se mide en bytes: 36 letras con tilde caben (72 bytes) y 37 no (74)', () => {
    expect(ok('ñ'.repeat(36))).toBeNull()
    expect(ok('ñ'.repeat(37))).toBe('La contraseña nueva debe tener de 8 a 72 caracteres')
  })

  it('no puede ser igual al correo, escriba como lo escriba', () => {
    expect(ok(CORREO)).toBe('La contraseña nueva no puede ser igual al correo')
    expect(ok('CONTACTO@Acme.PE')).toBe('La contraseña nueva no puede ser igual al correo')
  })

  it('las dos contraseñas deben coincidir', () => {
    expect(validarPasswordNueva('Valor-ficticio-1', 'Valor-ficticio-2', CORREO)).toBe('Las dos contraseñas no coinciden')
    expect(validarPasswordNueva('Valor-ficticio-1', '', CORREO)).toBe('Las dos contraseñas no coinciden')
  })

  it('sin correo conocido no se compara con él', () => {
    expect(validarPasswordNueva('Valor-ficticio-1', 'Valor-ficticio-1', '')).toBeNull()
  })

  it('el orden de los motivos: largo, luego correo, luego coincidencia', () => {
    expect(validarPasswordNueva('corta', 'otra', CORREO)).toBe('La contraseña nueva debe tener de 8 a 72 caracteres')
    expect(validarPasswordNueva(CORREO, 'otra', CORREO)).toBe('La contraseña nueva no puede ser igual al correo')
  })
})
