import type { Compra, TipoCompra } from '../services/compraService'

export const TIPOS_COMPRA: Record<TipoCompra, string> = {
  MERCADERIA_O_GASTO: 'Mercadería o gasto',
  ACTIVO_FIJO: 'Activo fijo',
}

export const DESCRIPCION_MAXIMO = 300

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre']

/** «2026-11-01» → «noviembre 2026». */
export const nombrePeriodo = (periodo: string) => `${MESES[Number(periodo.slice(5, 7)) - 1]} ${periodo.slice(0, 4)}`

/** Las pendientes primero (lo que falta hacer), después las clasificadas; dentro de cada grupo se respeta el orden del servidor. */
export function pendientesPrimero(compras: Compra[]): Compra[] {
  return [...compras.filter((c) => !c.clasificada), ...compras.filter((c) => c.clasificada)]
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
