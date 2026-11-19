import type { Compra, EstadoCompra, TipoCompra } from '../services/compraService'

export const TIPOS_COMPRA: Record<TipoCompra, string> = {
  MERCADERIA_O_GASTO: 'Mercadería o gasto',
  ACTIVO_FIJO: 'Activo fijo',
}

export const DESCRIPCION_MAXIMO = 300

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre']

/** «2026-11-01» → «noviembre 2026». */
export const nombrePeriodo = (periodo: string) => `${MESES[Number(periodo.slice(5, 7)) - 1]} ${periodo.slice(0, 4)}`

export const ETIQUETA_ESTADO_CLIENTE: Record<EstadoCompra, string> = {
  SIN_CLASIFICAR: 'Sin clasificar',
  PROPUESTA: 'Pendiente de validación del contador',
  VALIDADA: 'Validada',
}

/** Lo que el contador ve en su pestaña: la propuesta del cliente espera su validación. */
export const ETIQUETA_ESTADO_CONTADOR: Record<EstadoCompra, string> = {
  SIN_CLASIFICAR: 'Sin clasificar',
  PROPUESTA: 'Propuesta por validar',
  VALIDADA: 'Validada',
}

const ORDEN_CLIENTE: Record<EstadoCompra, number> = { SIN_CLASIFICAR: 0, PROPUESTA: 1, VALIDADA: 2 }
const ORDEN_CONTADOR: Record<EstadoCompra, number> = { PROPUESTA: 0, SIN_CLASIFICAR: 1, VALIDADA: 2 }

/** Lo que falta hacer primero: al cliente, lo que debe proponer; al contador, lo que debe validar. Dentro de cada grupo se respeta el orden del servidor. */
export function ordenar(compras: Compra[], para: 'cliente' | 'contador'): Compra[] {
  const orden = para === 'cliente' ? ORDEN_CLIENTE : ORDEN_CONTADOR
  return [...compras].sort((a, b) => orden[a.estado] - orden[b.estado])   // sort es estable: se conserva el orden del servidor
}

export interface ErroresClasificacion { tipo?: string; descripcion?: string }

/** Las mismas reglas del servidor, para avisar antes de enviar. */
export function validarClasificacion(tipo: string, descripcion: string): ErroresClasificacion {
  const e: ErroresClasificacion = {}
  if (tipo !== 'MERCADERIA_O_GASTO' && tipo !== 'ACTIVO_FIJO') e.tipo = 'Elija si es mercadería o gasto, o activo fijo'
  if (!descripcion.trim()) e.descripcion = 'La descripción es obligatoria'
  else if (descripcion.trim().length > DESCRIPCION_MAXIMO) e.descripcion = `La descripción admite hasta ${DESCRIPCION_MAXIMO} caracteres`
  return e
}
