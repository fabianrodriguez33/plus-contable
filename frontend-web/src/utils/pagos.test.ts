import { describe, expect, it } from 'vitest'
import { resumenDePagos, soles, validarPago } from './pagos'
import type { Obligacion } from '../services/obligacionService'
import type { Pago } from '../services/pagoService'

const HOY = '2026-11-09'
const ob = { id: 3, clienteId: 9, periodo: '2026-10-01', fechaVencimiento: '2026-11-20', estado: 'DECLARADO', montoDeterminado: 1000 } as Obligacion
const pago = (id: number, constancia: string, acumulado: number, saldo: number): Pago =>
  ({ id, fechaPago: '2026-11-01', montoPagado: 10, numeroConstancia: constancia, acumuladoDespues: acumulado, saldoDespues: saldo })
const datos = (over: Partial<{ fechaPago: string; montoPagado: string; numeroConstancia: string }> = {}) =>
  ({ fechaPago: HOY, montoPagado: '100', numeroConstancia: 'C-1', ...over })

describe('resumenDePagos', () => {
  it('sin pagos el acumulado es 0 y el saldo es el monto determinado', () => {
    expect(resumenDePagos(ob, [])).toEqual({ acumulado: 0, saldo: 1000 })
  })

  it('con pagos toma el acumulado y el saldo del último registrado (mayor id), aunque la lista venga en otro orden', () => {
    expect(resumenDePagos(ob, [pago(2, 'B', 500, 500), pago(1, 'A', 400, 600), pago(3, 'C', 1000, 0)])).toEqual({ acumulado: 1000, saldo: 0 })
    expect(resumenDePagos(ob, [pago(2, 'B', 500, 500), pago(1, 'A', 400, 600)])).toEqual({ acumulado: 500, saldo: 500 })
  })

  it('sin monto determinado el saldo es 0', () => {
    expect(resumenDePagos({ ...ob, montoDeterminado: undefined }, [])).toEqual({ acumulado: 0, saldo: 0 })
  })
})

describe('validarPago', () => {
  it('un pago correcto no tiene errores', () => {
    expect(validarPago(datos(), 1000, HOY, [])).toEqual({})
  })

  it('la fecha es obligatoria y no puede ser futura (la de hoy sí vale)', () => {
    expect(validarPago(datos({ fechaPago: '' }), 1000, HOY, []).fechaPago).toBe('Indique la fecha del pago')
    expect(validarPago(datos({ fechaPago: '2026-11-10' }), 1000, HOY, []).fechaPago).toBe('La fecha del pago no puede ser futura')
    expect(validarPago(datos({ fechaPago: HOY }), 1000, HOY, []).fechaPago).toBeUndefined()
  })

  it('el monto es obligatorio, mayor que cero, de hasta dos decimales y no supera el saldo (el saldo exacto vale)', () => {
    expect(validarPago(datos({ montoPagado: '' }), 1000, HOY, []).montoPagado).toBe('Indique el monto pagado')
    expect(validarPago(datos({ montoPagado: 'abc' }), 1000, HOY, []).montoPagado).toBe('Indique el monto pagado')
    expect(validarPago(datos({ montoPagado: '0' }), 1000, HOY, []).montoPagado).toBe('El monto debe ser mayor que cero')
    expect(validarPago(datos({ montoPagado: '-1' }), 1000, HOY, []).montoPagado).toBe('El monto debe ser mayor que cero')
    expect(validarPago(datos({ montoPagado: '10.123' }), 1000, HOY, []).montoPagado).toBe('Use como máximo dos decimales')
    expect(validarPago(datos({ montoPagado: '1000.01' }), 1000, HOY, []).montoPagado).toBe('El monto supera el saldo pendiente de S/ 1000.00')
    expect(validarPago(datos({ montoPagado: '1000' }), 1000, HOY, []).montoPagado).toBeUndefined()
    expect(validarPago(datos({ montoPagado: '0.01' }), 1000, HOY, []).montoPagado).toBeUndefined()
  })

  it('los centavos se comparan exactos: 99.90 con saldo 99.90 vale y 99.91 no', () => {
    expect(validarPago(datos({ montoPagado: '99.90' }), 99.9, HOY, []).montoPagado).toBeUndefined()
    expect(validarPago(datos({ montoPagado: '99.91' }), 99.9, HOY, []).montoPagado).toBe('El monto supera el saldo pendiente de S/ 99.90')
  })

  it('la constancia es obligatoria, de hasta 30 caracteres sin contar espacios de los lados y no se repite en la obligación', () => {
    expect(validarPago(datos({ numeroConstancia: '   ' }), 1000, HOY, []).numeroConstancia).toBe('Indique el número de constancia')
    expect(validarPago(datos({ numeroConstancia: 'X'.repeat(31) }), 1000, HOY, []).numeroConstancia).toBe('El número de constancia admite hasta 30 caracteres')
    expect(validarPago(datos({ numeroConstancia: ' ' + 'X'.repeat(30) + ' ' }), 1000, HOY, []).numeroConstancia).toBeUndefined()
    expect(validarPago(datos({ numeroConstancia: ' C-001 ' }), 1000, HOY, [pago(1, 'C-001', 10, 990)]).numeroConstancia)
      .toBe('Ese número de constancia ya está registrado en esta obligación')
    expect(validarPago(datos({ numeroConstancia: 'C-002' }), 1000, HOY, [pago(1, 'C-001', 10, 990)]).numeroConstancia).toBeUndefined()
  })

  it('reúne todos los errores a la vez', () => {
    expect(Object.keys(validarPago({ fechaPago: '', montoPagado: '', numeroConstancia: '' }, 1000, HOY, [])).sort())
      .toEqual(['fechaPago', 'montoPagado', 'numeroConstancia'])
  })
})

describe('soles', () => {
  it('da siempre dos decimales', () => {
    expect(soles(5)).toBe('S/ 5.00')
    expect(soles(0.5)).toBe('S/ 0.50')
  })
})
