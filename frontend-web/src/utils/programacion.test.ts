import { describe, expect, it } from 'vitest'
import { etiquetaTipo, fechaCorta, mesAnterior, periodoValido } from './programacion'

describe('utils/programacion', () => {
  it('el mes anterior cruza el año en enero', () => {
    expect(mesAnterior(new Date(2026, 0, 15))).toBe('2025-12')
    expect(mesAnterior(new Date(2026, 10, 4))).toBe('2026-10')
    expect(mesAnterior(new Date(2026, 2, 31))).toBe('2026-02')
  })

  it('valida el periodo AAAA-MM', () => {
    expect(periodoValido('2026-10')).toBe(true)
    expect(periodoValido('2026-13')).toBe(false)
    expect(periodoValido('2026-00')).toBe(false)
    expect(periodoValido('')).toBe(false)
    expect(periodoValido('octubre')).toBe(false)
  })

  it('da formato dd/MM/yyyy sin mover el día', () => {
    expect(fechaCorta('2026-11-20')).toBe('20/11/2026')
    expect(fechaCorta('2026-12-01')).toBe('01/12/2026')
    expect(fechaCorta('no es fecha')).toBe('no es fecha')
  })

  it('traduce los tipos a su etiqueta', () => {
    expect(etiquetaTipo('RETENCION_QUINTA')).toBe('Retención de quinta categoría')
    expect(etiquetaTipo('ESSALUD')).toBe('EsSalud')
    expect(etiquetaTipo('IGV')).toBe('IGV')
  })
})
