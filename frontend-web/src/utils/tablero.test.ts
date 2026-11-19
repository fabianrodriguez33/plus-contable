import { describe, expect, it } from 'vitest'
import type { FilaTablero } from '../services/tableroService'
import { ENLACES } from '../components/Sidebar'
import { ETIQUETA_ESTADO, hoyLima, mesActualLima, mesCorto, periodoDeMes, resumenTablero } from './tablero'

const fila = (cambios: Partial<FilaTablero>): FilaTablero => ({
  obligacionId: 1, clienteId: 1, ruc: '20100070970', razonSocial: 'Acme SAC', tipoObligacion: 'IGV', periodo: '2026-11-01',
  fechaVencimiento: '2026-11-20', estado: 'PENDIENTE', montoDeterminado: null, montoPagado: 0, saldo: null, vencida: false, sinDeclarar: false, ...cambios,
})

describe('tablero (HU-14)', () => {
  it('«hoy» es el de Lima aunque el navegador o el servidor estén en UTC', () => {
    // 12/11 02:00 UTC = 11/11 21:00 en Lima
    expect(hoyLima(new Date('2026-11-12T02:00:00Z'))).toBe('2026-11-11')
    expect(hoyLima(new Date('2026-11-12T05:00:00Z'))).toBe('2026-11-12')
    expect(mesActualLima(new Date('2026-12-01T03:00:00Z'))).toBe('2026-11') // 30/11 22:00 en Lima
    expect(mesActualLima(new Date('2026-12-01T05:00:00Z'))).toBe('2026-12')
  })

  it('el campo de mes se convierte en el primer día del mes y vacío significa todos los meses', () => {
    expect(periodoDeMes('2026-11')).toBe('2026-11-01')
    expect(periodoDeMes('')).toBeUndefined()
    expect(periodoDeMes('2026-13')).toBeUndefined()
    expect(periodoDeMes('2026-1')).toBeUndefined()
  })

  it('el mes se muestra como MM/AAAA', () => {
    expect(mesCorto('2026-11-01')).toBe('11/2026')
    expect(mesCorto('raro')).toBe('raro')
  })

  it('cada estado tiene su etiqueta', () => {
    expect(ETIQUETA_ESTADO).toEqual({ PENDIENTE: 'Pendiente', DECLARADO: 'Declarada', PAGO_PARCIAL: 'Pago parcial', PAGADO: 'Pagada' })
  })

  it('el resumen cuenta por estado y las vencidas aparte', () => {
    const r = resumenTablero([
      fila({ obligacionId: 1, estado: 'PENDIENTE', sinDeclarar: true }),
      fila({ obligacionId: 2, estado: 'DECLARADO', vencida: true }),
      fila({ obligacionId: 3, estado: 'DECLARADO' }),
      fila({ obligacionId: 4, estado: 'PAGO_PARCIAL', vencida: true }),
      fila({ obligacionId: 5, estado: 'PAGADO' }),
    ])

    expect(r).toEqual({ pendientes: 1, declaradas: 2, pagoParcial: 1, pagadas: 1, vencidas: 2, sinDeclarar: 1 })
  })

  it('el menú tiene el enlace al tablero', () => {
    expect(ENLACES).toContainEqual(expect.objectContaining({ to: '/tablero', etiqueta: 'Tablero' }))
  })
})
