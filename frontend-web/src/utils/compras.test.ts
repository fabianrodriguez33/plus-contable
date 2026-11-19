import { describe, expect, it } from 'vitest'
import type { Compra, EstadoCompra } from '../services/compraService'
import { ETIQUETA_ESTADO_CLIENTE, ETIQUETA_ESTADO_CONTADOR, TIPOS_COMPRA, nombrePeriodo, ordenar, validarClasificacion } from './compras'

const compra = (id: number, estado: EstadoCompra): Compra => ({
  id, clienteId: 4, periodo: '2026-11-01', proveedorRuc: '20100070970', proveedorRazonSocial: null, comprobante: `F-${id}`,
  fechaComprobante: '2026-11-05', importe: 10, estado, tipo: estado === 'SIN_CLASIFICAR' ? null : 'ACTIVO_FIJO',
  descripcion: estado === 'SIN_CLASIFICAR' ? null : 'x', clasificadaEn: null, validadaPor: null, validadaEn: null, alertaCierre: false,
})

describe('compras (HU-25 · T215)', () => {
  it('el catálogo es el acordado con Beto', () => {
    expect(TIPOS_COMPRA).toEqual({ MERCADERIA_O_GASTO: 'Mercadería o gasto', ACTIVO_FIJO: 'Activo fijo' })
  })

  it('el cliente ve «pendiente de validación del contador» y el contador «propuesta por validar»', () => {
    expect(ETIQUETA_ESTADO_CLIENTE.PROPUESTA).toBe('Pendiente de validación del contador')
    expect(ETIQUETA_ESTADO_CONTADOR.PROPUESTA).toBe('Propuesta por validar')
  })

  it('al cliente le salen primero las que debe proponer; al contador, primero las que debe validar', () => {
    const lista = [compra(1, 'VALIDADA'), compra(2, 'PROPUESTA'), compra(3, 'SIN_CLASIFICAR'), compra(4, 'PROPUESTA'), compra(5, 'SIN_CLASIFICAR')]
    expect(ordenar(lista, 'cliente').map((c) => c.id)).toEqual([3, 5, 2, 4, 1])
    expect(ordenar(lista, 'contador').map((c) => c.id)).toEqual([2, 4, 3, 5, 1])
    expect(lista.map((c) => c.id)).toEqual([1, 2, 3, 4, 5])   // no modifica la lista original
  })

  it('el periodo se nombra con el mes y el año', () => {
    expect(nombrePeriodo('2026-11-01')).toBe('noviembre 2026')
    expect(nombrePeriodo('2027-01-01')).toBe('enero 2027')
  })

  it('para proponer hacen falta el tipo y la descripción (máximo 300)', () => {
    expect(validarClasificacion('ACTIVO_FIJO', 'Laptop')).toEqual({})
    expect(validarClasificacion('', 'Laptop').tipo).toBeTruthy()
    expect(validarClasificacion('OTRO', 'Laptop').tipo).toBeTruthy()
    expect(validarClasificacion('ACTIVO_FIJO', '   ').descripcion).toMatch(/obligatoria/)
    expect(validarClasificacion('ACTIVO_FIJO', 'x'.repeat(300))).toEqual({})
    expect(validarClasificacion('ACTIVO_FIJO', 'x'.repeat(301)).descripcion).toMatch(/300/)
  })
})
