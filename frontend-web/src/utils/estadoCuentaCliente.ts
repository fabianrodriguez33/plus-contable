import type { EstadoObligacion, Obligacion } from '../services/obligacionService'

/** Nombres de estado que ya usan el calendario y el tablero; aquí no se inventa ninguno nuevo. */
export const ETIQUETA_ESTADO: Record<EstadoObligacion, string> = {
  PENDIENTE: 'Pendiente',
  DECLARADO: 'Declarada',
  PAGO_PARCIAL: 'Pago parcial',
  PAGADO: 'Pagada',
}

export const COLOR_ESTADO: Record<EstadoObligacion, string> = {
  PENDIENTE: 'bg-amber-50 text-amber-700',
  DECLARADO: 'bg-sky-50 text-sky-700',
  PAGO_PARCIAL: 'bg-orange-50 text-orange-700',
  PAGADO: 'bg-emerald-50 text-emerald-700',
}

/** «Hoy» es el de Lima, aunque el navegador esté en otra zona. Devuelve el mes como AAAA-MM. */
export function mesDeHoyEnLima(ahora: Date = new Date()): string {
  const partes = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit' })
    .formatToParts(ahora)
  const valor = (tipo: string) => partes.find((p) => p.type === tipo)?.value ?? ''
  return `${valor('year')}-${valor('month')}`
}

/**
 * Los tributos de un mes. «Mes» = el periodo tributario de la obligación (supuesto pendiente de Beto, el mismo del tablero),
 * no el mes de su fecha límite. Orden: fecha límite, tributo e id.
 */
export function delMes(obligaciones: Obligacion[], mes: string): Obligacion[] {
  return obligaciones
    .filter((o) => o.periodo.slice(0, 7) === mes)
    .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento)
      || (a.tipoObligacion ?? '').localeCompare(b.tipoObligacion ?? '') || a.id - b.id)
}

/** Meses con al menos un tributo, del más reciente al más antiguo, más el mes pedido aunque no tenga datos. */
export function mesesDisponibles(obligaciones: Obligacion[], mesActual: string): string[] {
  return [...new Set([...obligaciones.map((o) => o.periodo.slice(0, 7)), mesActual])].sort().reverse()
}
