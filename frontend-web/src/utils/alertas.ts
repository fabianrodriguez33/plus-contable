import type { TipoNotificacion } from '../services/alertaPlazoService'

export const ETIQUETA_TIPO: Record<TipoNotificacion, string> = {
  ESQUELA: 'Esquela',
  CARTA_INDUCTIVA: 'Carta inductiva',
  COMUNICADO: 'Comunicado',
}

/** Los días que faltan para el plazo, como lo lee el contador. */
export function textoDiasRestantes(dias: number): string {
  if (dias === 0) return 'Vence hoy'
  if (dias === 1) return 'Falta 1 día'
  return `Faltan ${dias} días`
}

/** Cuanto más cerca el plazo, más urgente: hoy y mañana en rojo, hasta 3 días en ámbar, el resto neutro. */
export function estiloDias(dias: number): string {
  if (dias <= 1) return 'bg-red-50 text-red-700'
  if (dias <= 3) return 'bg-amber-50 text-amber-700'
  return 'bg-slate-100 text-slate-700'
}

/** «alerta de 5 días antes», «alerta del día del plazo». */
export const textoEscalon = (diasAntes: number) => (diasAntes === 0 ? 'Alerta del día del plazo' : `Alerta de ${diasAntes} ${diasAntes === 1 ? 'día' : 'días'} antes`)
