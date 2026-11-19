import type { EstadoObligacion } from '../services/obligacionService'
import type { FilaTablero } from '../services/tableroService'

export const ETIQUETA_ESTADO: Record<EstadoObligacion, string> = {
  PENDIENTE: 'Pendiente',
  DECLARADO: 'Declarada',
  PAGO_PARCIAL: 'Pago parcial',
  PAGADO: 'Pagada',
}

export const ESTILO_ESTADO: Record<EstadoObligacion, string> = {
  PENDIENTE: 'bg-slate-100 text-slate-700',
  DECLARADO: 'bg-sky-50 text-sky-700',
  PAGO_PARCIAL: 'bg-amber-50 text-amber-700',
  PAGADO: 'bg-emerald-50 text-emerald-700',
}

/** La fecha de hoy en Lima como AAAA-MM-DD, sin depender de la zona horaria del navegador. */
export function hoyLima(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(ahora)
}

/** El mes de hoy en Lima como AAAA-MM (el formato del campo de mes). */
export const mesActualLima = (ahora: Date = new Date()) => hoyLima(ahora).slice(0, 7)

/** AAAA-MM del campo de mes -> primer día del mes (AAAA-MM-01) que espera el servidor; vacío = todos los meses. */
export const periodoDeMes = (mes: string): string | undefined => (/^\d{4}-(0[1-9]|1[0-2])$/.test(mes) ? `${mes}-01` : undefined)

/** AAAA-MM-01 -> MM/AAAA. */
export function mesCorto(periodo: string): string {
  const m = /^(\d{4})-(\d{2})-\d{2}$/.exec(periodo)
  return m ? `${m[2]}/${m[1]}` : periodo
}

export interface ResumenTablero {
  pendientes: number
  declaradas: number
  pagoParcial: number
  pagadas: number
  vencidas: number
  sinDeclarar: number
}

export function resumenTablero(filas: FilaTablero[]): ResumenTablero {
  const cuenta = (estado: EstadoObligacion) => filas.filter((f) => f.estado === estado).length
  return {
    pendientes: cuenta('PENDIENTE'),
    declaradas: cuenta('DECLARADO'),
    pagoParcial: cuenta('PAGO_PARCIAL'),
    pagadas: cuenta('PAGADO'),
    vencidas: filas.filter((f) => f.vencida).length,
    sinDeclarar: filas.filter((f) => f.sinDeclarar).length,
  }
}
