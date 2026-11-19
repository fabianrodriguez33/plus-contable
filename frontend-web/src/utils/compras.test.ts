import { describe, expect, it } from 'vitest'
import type { Compra } from '../services/compraService'
import { TIPOS_COMPRA, nombrePeriodo, pendientesPrimero, validarClasificacion } from './compras'

const compra = (id: number, clasificada: boolean): Compra => ({
  id, clienteId: 4, periodo: '2026-11-01', proveedorRuc: '20100070970', proveedorRazonSocial: null, comprobante: `F-${id}`,
  fechaComprobante: '2026-11-05', importe: 10, clasificada, tipo: clasificada ? 'ACTIVO_FIJO' : null,
  descripcion: clasificada ? 'x' : null, clasificadaEn: null, alertaCierre: false,
})

describe('compras (HU-25 · T215)', () => {
  it('el catálogo es el acordado con Beto', () => {
    expect(TIPOS_COMPRA).toEqual({ MERCADERIA_O_GASTO: 'Mercadería o gasto', ACTIVO_FIJO: 'Activo fijo' })
  })

  it('las pendientes van primero y se respeta el orden dentro de cada grupo', () => {
    const lista = [compra(1, true), compra(2, false), compra(3, true), compra(4, false)]
    expect(pendientesPrimero(lista).map((c) => c.id)).toEqual([2, 4, 1, 3])
    expect(lista.map((c) => c.id)).toEqual([1, 2, 3, 4])   // no modifica la lista original
  })

  it('el periodo se nombra con el mes y el año', () => {
    expect(nombrePeriodo('2026-11-01')).toBe('noviembre 2026')
    expect(nombrePeriodo('2027-01-01')).toBe('enero 2027')
  })

  it('para clasificar hacen falta el tipo y la descripción (máximo 300)', () => {
    expect(validarClasificacion('ACTIVO_FIJO', 'Laptop')).toEqual({})
    expect(validarClasificacion('', 'Laptop').tipo).toBeTruthy()
    expect(validarClasificacion('OTRO', 'Laptop').tipo).toBeTruthy()
    expect(validarClasificacion('ACTIVO_FIJO', '   ').descripcion).toMatch(/obligatoria/)
    expect(validarClasificacion('ACTIVO_FIJO', 'x'.repeat(300))).toEqual({})
    expect(validarClasificacion('ACTIVO_FIJO', 'x'.repeat(301)).descripcion).toMatch(/300/)
  })
})
