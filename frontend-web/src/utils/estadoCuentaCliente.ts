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

/** Hoy en Lima como AAAA-MM-DD (para comparar con las fechas límite, que también vienen así). */
export function hoyEnLima(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(ahora)
}

/**
 * «Vencida» (definición de Beto, la misma del tablero, del calendario y de la lista del estudio) = fecha límite pasada y saldo
 * pendiente: declarada sin pago o con pago parcial (con pago parcial sigue vencida). La pagada nunca, y la sin declarar con fecha
 * pasada no se marca como vencida: es un pendiente de atención del estudio. El día de la fecha límite todavía no está vencida.
 */
export function estaVencida(o: Pick<Obligacion, 'estado' | 'fechaVencimiento'>, hoy: string): boolean {
  return (o.estado === 'DECLARADO' || o.estado === 'PAGO_PARCIAL') && o.fechaVencimiento < hoy
}

/**
 * Lo pagado y el saldo para mostrar. El servidor los trae con el pago parcial (suma de los pagos de HU-13); en los demás estados se
 * derivan del estado: pagada = todo pagado y saldo cero; declarada sin pago = nada pagado y saldo igual al monto; sin declarar o
 * sin monto determinado = no hay cifra (nulo).
 */
export function montosDe(o: Obligacion): { pagado: number | null; saldo: number | null } {
  const monto = o.montoDeterminado ?? null
  if (o.estado === 'PAGO_PARCIAL') {
    return { pagado: o.montoAcumuladoPagado ?? null, saldo: o.saldo ?? (monto !== null && o.montoAcumuladoPagado != null ? monto - o.montoAcumuladoPagado : null) }
  }
  if (monto === null) return { pagado: null, saldo: null }
  if (o.estado === 'PAGADO') return { pagado: monto, saldo: 0 }
  if (o.estado === 'DECLARADO') return { pagado: 0, saldo: monto }
  return { pagado: null, saldo: null }
}
