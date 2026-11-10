import { tributarioApi } from './apiClient'

// ---- HU-18 ----
export type TipoNotificacion = 'ESQUELA' | 'CARTA_INDUCTIVA' | 'COMUNICADO'
export type EstadoNotificacion = 'PENDIENTE' | 'ATENDIDA'

export interface Notificacion {
  id: number
  clienteId: number
  tipo: TipoNotificacion
  fechaRecepcion: string
  plazoRespuesta: string
  estado: EstadoNotificacion
  fechaAtencion?: string | null
}

export interface NotificacionEntrada {
  tipo: TipoNotificacion
  fechaRecepcion: string
  plazoRespuesta: string
}

/** Registra una notificación del Buzón SOL del cliente; nace PENDIENTE. */
export const registrarNotificacion = (clienteId: number, entrada: NotificacionEntrada) =>
  tributarioApi.post<Notificacion>(`/api/tributario/clientes/${clienteId}/notificaciones`, entrada).then((r) => r.data)
