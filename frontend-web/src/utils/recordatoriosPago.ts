import type { EstadoRecordatorio } from '../services/recordatorioPagoService'

export const ETIQUETA_ESTADO: Record<EstadoRecordatorio, string> = {
  PROGRAMADO: 'Programado',
  ENVIADO: 'Enviado al cliente',
  CANCELADO: 'Cancelado',
  OMITIDO: 'Omitido: ya no correspondía',
}

/** Hoy en Lima como AAAA-MM-DD, aunque el navegador esté en otra zona. */
export function hoyEnLima(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(ahora)
}

/**
 * Las mismas reglas del servidor, para avisar antes de enviar: la fecha es obligatoria, no puede ser pasada ni posterior al
 * vencimiento de la obligación. (Que no tenga ya ese recordatorio programado lo verifica el servidor.)
 */
export function validarFecha(fecha: string, hoy: string, vence: string): string | null {
  if (!fecha) return 'Elija la fecha del recordatorio'
  if (fecha < hoy) return 'La fecha no puede ser pasada'
  if (fecha > vence) return 'La fecha no puede ser posterior al vencimiento de la obligación'
  return null
}
