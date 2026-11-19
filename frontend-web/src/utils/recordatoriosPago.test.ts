import { describe, expect, it } from 'vitest'
import { ETIQUETA_ESTADO, hoyEnLima, validarFecha } from './recordatoriosPago'

describe('recordatorios de pago (HU-15 · T35)', () => {
  it('«hoy» es el de Lima aunque el navegador esté en otra zona', () => {
    expect(hoyEnLima(new Date('2026-12-01T03:00:00Z'))).toBe('2026-11-30')
    expect(hoyEnLima(new Date('2026-12-01T06:00:00Z'))).toBe('2026-12-01')
  })

  it('la fecha es obligatoria, no puede ser pasada ni posterior al vencimiento; hoy y el día del vencimiento valen', () => {
    const hoy = '2026-11-18'
    const vence = '2026-11-30'
    expect(validarFecha('', hoy, vence)).toMatch(/Elija/)
    expect(validarFecha('2026-11-17', hoy, vence)).toMatch(/pasada/)
    expect(validarFecha('2026-12-01', hoy, vence)).toMatch(/posterior al vencimiento/)
    expect(validarFecha('2026-11-18', hoy, vence)).toBeNull()
    expect(validarFecha('2026-11-30', hoy, vence)).toBeNull()
  })

  it('los estados se muestran con su nombre', () => {
    expect(ETIQUETA_ESTADO.PROGRAMADO).toBe('Programado')
    expect(ETIQUETA_ESTADO.OMITIDO).toMatch(/Omitido/)
  })
})
