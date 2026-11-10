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

/** Las notificaciones del cliente, la de plazo más próximo primero; con `estado` solo las de ese estado. */
export const listarNotificaciones = (clienteId: number, estado?: EstadoNotificacion) =>
  tributarioApi
    .get<Notificacion[]>(`/api/tributario/clientes/${clienteId}/notificaciones`, { params: estado ? { estado } : {} })
    .then((r) => r.data)

/** Pasa una notificación pendiente a ATENDIDA con la fecha de hoy (el servidor responde 400 si ya lo estaba). */
export const marcarAtendida = (id: number) =>
  tributarioApi.patch<Notificacion>(`/api/tributario/notificaciones/${id}/atencion`).then((r) => r.data)

/** Registra una notificación del Buzón SOL del cliente; nace PENDIENTE. */
export const registrarNotificacion = (clienteId: number, entrada: NotificacionEntrada) =>
  tributarioApi.post<Notificacion>(`/api/tributario/clientes/${clienteId}/notificaciones`, entrada).then((r) => r.data)
