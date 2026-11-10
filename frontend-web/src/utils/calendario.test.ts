import { describe, expect, it } from 'vitest'
import type { CalendarioItem } from '../services/calendarioService'
import type { CuotaCalendario } from '../services/fraccionamientoService'
import {
  agruparEntradasPorDia, agruparPorDia, aIso, deIso, entradaDeCuota, entradaDeObligacion, etiquetaSituacion,
  mapConLimite, rangoDeLaSemana, rangoDelMes, situacionVisual,
} from './calendario'

function item(id: number, clienteId: number, fecha: string, over: Partial<CalendarioItem> = {},
              estado: 'PENDIENTE' | 'DECLARADO' = 'PENDIENTE', tipo = 'IGV'): CalendarioItem {
  return {
    obligacion: { id, clienteId, tipoObligacion: tipo, periodo: '2026-10-01', fechaVencimiento: fecha, estado },
    diasRestantes: 3, vencimientoPasado: false, ...over,
  }
}

describe('rangoDelMes', () => {
  it('febrero tiene 28 días en un año común y 29 en bisiesto', () => {
    expect(rangoDelMes(2026, 2)).toEqual({ desde: '2026-02-01', hasta: '2026-02-28' })
    expect(rangoDelMes(2028, 2)).toEqual({ desde: '2028-02-01', hasta: '2028-02-29' })
  })
  it('diciembre termina el 31 y los meses de un dígito llevan cero', () => {
    expect(rangoDelMes(2026, 12)).toEqual({ desde: '2026-12-01', hasta: '2026-12-31' })
    expect(rangoDelMes(2026, 4)).toEqual({ desde: '2026-04-01', hasta: '2026-04-30' })
  })
})

describe('rangoDeLaSemana', () => {
  it('va de lunes a domingo', () => {
    expect(rangoDeLaSemana(deIso('2026-11-04'))).toEqual({ desde: '2026-11-02', hasta: '2026-11-08' })
  })
  it('un domingo pertenece a la semana que termina ese día', () => {
    expect(rangoDeLaSemana(deIso('2026-11-08'))).toEqual({ desde: '2026-11-02', hasta: '2026-11-08' })
  })
  it('un lunes abre su propia semana', () => {
    expect(rangoDeLaSemana(deIso('2026-11-02'))).toEqual({ desde: '2026-11-02', hasta: '2026-11-08' })
  })
  it('cruza el cambio de mes y de año', () => {
    expect(rangoDeLaSemana(deIso('2026-12-31'))).toEqual({ desde: '2026-12-28', hasta: '2027-01-03' })
  })
})

describe('fechas ISO', () => {
  it('ida y vuelta sin correr el día', () => {
    expect(aIso(deIso('2026-03-01'))).toBe('2026-03-01')
  })
})

describe('agruparPorDia', () => {
  const nombres: Record<number, string> = { 1: 'Zeta SAC', 2: 'Alfa SAC' }
  const nombre = (id: number) => nombres[id]

  it('ordena los días cronológicamente y dentro del día por cliente y tributo', () => {
    const grupos = agruparPorDia([
      item(1, 1, '2026-11-10'), item(2, 2, '2026-11-05', {}, 'PENDIENTE', 'RENTA'),
      item(3, 2, '2026-11-05'), item(4, 1, '2026-11-05'),
    ], nombre)
    expect(grupos.map((g) => g.fecha)).toEqual(['2026-11-05', '2026-11-10'])
    expect(grupos[0].items.map((i) => i.obligacion.id)).toEqual([3, 2, 4])
  })
  it('sin vencimientos no hay grupos', () => {
    expect(agruparPorDia([], nombre)).toEqual([])
  })
})

describe('agruparEntradasPorDia', () => {
  const nombres: Record<number, string> = { 1: 'Zeta SAC', 2: 'Alfa SAC' }
  const nombre = (id: number) => nombres[id]
  const q = (id: number, numero: number, fecha: string): CuotaCalendario => ({
    cuota: { id, numero, fechaVencimiento: fecha, monto: 100, estado: 'PENDIENTE', fechaPago: null, enRiesgo: false },
    fraccionamientoId: 1, diasRestantes: 3, vencida: false,
  })

  it('mezcla cuotas y obligaciones por día: por cliente, obligaciones antes que cuotas y cuotas por número', () => {
    const grupos = agruparEntradasPorDia([
      entradaDeCuota(1, q(12, 2, '2026-11-10')), entradaDeCuota(1, q(11, 1, '2026-11-10')),
      entradaDeObligacion(item(5, 1, '2026-11-10')), entradaDeCuota(2, q(20, 1, '2026-11-03')),
      entradaDeObligacion(item(6, 2, '2026-11-10')),
    ], nombre)
    expect(grupos.map((g) => g.fecha)).toEqual(['2026-11-03', '2026-11-10'])
    expect(grupos[1].entradas.map((e) => `${e.tipo}${e.clienteId}`))
      .toEqual(['OBLIGACION2', 'OBLIGACION1', 'CUOTA1', 'CUOTA1'])
    expect(grupos[1].entradas.slice(2).map((e) => (e.tipo === 'CUOTA' ? e.item.cuota.numero : 0))).toEqual([1, 2])
  })
})

describe('situación visual', () => {
  it('una obligación declarada se muestra como declarada aunque su fecha haya pasado', () => {
    const it = item(1, 1, '2026-10-20', { diasRestantes: -13, vencimientoPasado: true }, 'DECLARADO')
    expect(situacionVisual(it)).toBe('DECLARADA')
    expect(etiquetaSituacion(it)).toBe('Declarada')
  })
  it('una pendiente con fecha pasada está vencida', () => {
    expect(etiquetaSituacion(item(1, 1, '2026-11-01', { diasRestantes: -1, vencimientoPasado: true })))
      .toBe('Vencida hace 1 día')
    expect(etiquetaSituacion(item(1, 1, '2026-10-30', { diasRestantes: -3, vencimientoPasado: true })))
      .toBe('Vencida hace 3 días')
  })
  it('hoy, mañana y días restantes', () => {
    expect(etiquetaSituacion(item(1, 1, '2026-11-04', { diasRestantes: 0 }))).toBe('Vence hoy')
    expect(etiquetaSituacion(item(1, 1, '2026-11-05', { diasRestantes: 1 }))).toBe('Vence mañana')
    expect(etiquetaSituacion(item(1, 1, '2026-11-09', { diasRestantes: 5 }))).toBe('Vence en 5 días')
  })
})

describe('mapConLimite', () => {
  it('conserva el orden y nunca supera el límite de tareas simultáneas', async () => {
    let activas = 0
    let maximo = 0
    const r = await mapConLimite([1, 2, 3, 4, 5, 6, 7], 3, async (n) => {
      activas++
      maximo = Math.max(maximo, activas)
      await new Promise((ok) => setTimeout(ok, 5))
      activas--
      return n * 2
    })
    expect(r).toEqual([2, 4, 6, 8, 10, 12, 14])
    expect(maximo).toBeLessThanOrEqual(3)
  })
  it('con lista vacía devuelve vacío', async () => {
    expect(await mapConLimite([], 4, async (n: number) => n)).toEqual([])
  })
})
