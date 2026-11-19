import { describe, expect, it } from 'vitest'
import type { Obligacion } from '../services/obligacionService'
import { ETIQUETA_ESTADO, delMes, estaVencida, hoyEnLima, mesDeHoyEnLima, mesesDisponibles, montosDe } from './estadoCuentaCliente'

const ob = (id: number, periodo: string, vence: string, tipo?: string): Obligacion =>
  ({ id, clienteId: 4, tipoObligacion: tipo, periodo, fechaVencimiento: vence, estado: 'PENDIENTE' })

describe('estado de cuenta del cliente (HU-32 · T200)', () => {
  it('los nombres de estado son los del calendario y el tablero', () => {
    expect(ETIQUETA_ESTADO).toEqual({ PENDIENTE: 'Pendiente', DECLARADO: 'Declarada', PAGO_PARCIAL: 'Pago parcial', PAGADO: 'Pagada' })
  })

  it('«mes» es el periodo tributario, no el mes de la fecha límite, ordenado por fecha límite, tributo e id', () => {
    const lista = [ob(3, '2026-10-01', '2026-11-20', 'RENTA'), ob(1, '2026-10-01', '2026-11-20', 'IGV'),
      ob(2, '2026-10-01', '2026-11-15', 'PLAME'), ob(9, '2026-11-01', '2026-12-18', 'IGV')]
    expect(delMes(lista, '2026-10').map((o) => o.id)).toEqual([2, 1, 3])
    expect(delMes(lista, '2026-11').map((o) => o.id)).toEqual([9])
    expect(delMes(lista, '2026-09')).toEqual([])
  })

  it('«hoy» es el de Lima aunque el navegador esté en otra zona', () => {
    // 01/12/2026 03:00 UTC = 30/11/2026 22:00 en Lima
    expect(mesDeHoyEnLima(new Date('2026-12-01T03:00:00Z'))).toBe('2026-11')
    expect(mesDeHoyEnLima(new Date('2026-12-01T06:00:00Z'))).toBe('2026-12')
  })

  it('los meses ofrecidos incluyen el actual aunque no tenga datos, sin repetir, del más reciente al más antiguo', () => {
    const lista = [ob(1, '2026-09-01', '2026-10-20'), ob(2, '2026-09-01', '2026-10-21'), ob(3, '2026-10-01', '2026-11-20')]
    expect(mesesDisponibles(lista, '2026-11')).toEqual(['2026-11', '2026-10', '2026-09'])
  })

  it('«hoy» de Lima como fecha completa', () => {
    expect(hoyEnLima(new Date('2026-12-01T03:00:00Z'))).toBe('2026-11-30')
    expect(hoyEnLima(new Date('2026-12-01T06:00:00Z'))).toBe('2026-12-01')
  })

  it('vencida (Beto) = fecha pasada y saldo pendiente: declarada o con pago parcial; la pagada y la sin declarar no', () => {
    const hoy = '2026-11-18'
    const o = (estado: Obligacion['estado'], vence: string) => ({ estado, fechaVencimiento: vence })
    expect(estaVencida(o('DECLARADO', '2026-11-17'), hoy)).toBe(true)
    expect(estaVencida(o('PAGO_PARCIAL', '2026-11-17'), hoy)).toBe(true)     // con pago parcial sigue vencida
    expect(estaVencida(o('PAGADO', '2026-11-17'), hoy)).toBe(false)
    expect(estaVencida(o('PENDIENTE', '2026-11-17'), hoy)).toBe(false)        // sin declarar: aparte, no vencida
    expect(estaVencida(o('DECLARADO', '2026-11-18'), hoy)).toBe(false)        // el día de la fecha límite todavía no
    expect(estaVencida(o('DECLARADO', '2026-11-19'), hoy)).toBe(false)
  })

  it('lo pagado y el saldo: del servidor con pago parcial; derivados del estado en los demás', () => {
    const base = { id: 1, clienteId: 4, periodo: '2026-10-01', fechaVencimiento: '2026-11-20' }
    expect(montosDe({ ...base, estado: 'PAGO_PARCIAL', montoDeterminado: 1000, montoAcumuladoPagado: 400, saldo: 600 })).toEqual({ pagado: 400, saldo: 600 })
    expect(montosDe({ ...base, estado: 'PAGO_PARCIAL', montoDeterminado: 1000, montoAcumuladoPagado: 400 })).toEqual({ pagado: 400, saldo: 600 })
    expect(montosDe({ ...base, estado: 'PAGADO', montoDeterminado: 700 })).toEqual({ pagado: 700, saldo: 0 })
    expect(montosDe({ ...base, estado: 'DECLARADO', montoDeterminado: 700 })).toEqual({ pagado: 0, saldo: 700 })
    expect(montosDe({ ...base, estado: 'PENDIENTE' })).toEqual({ pagado: null, saldo: null })
    expect(montosDe({ ...base, estado: 'DECLARADO' })).toEqual({ pagado: null, saldo: null })
  })
})
