import { tributarioApi } from './apiClient'

// ---- HU-26 ----
export type TipoSolicitud = 'CONSTANCIA' | 'CERTIFICACION' | 'TRAMITE'
export type EstadoSolicitud = 'PENDIENTE' | 'ATENDIDA'

export interface SolicitudEspecial {
  id: number
  clienteId: number
  tipo: TipoSolicitud
  descripcion: string | null
  fechaEntrega: string
  estado: EstadoSolicitud
  fechaAtencion: string | null
}

export interface SolicitudEntrada {
  tipo: TipoSolicitud
  fechaEntrega: string
  /** Opcional: si va vacía no se envía. */
  descripcion?: string
}

const ruta = (clienteId: number) => `/api/tributario/clientes/${clienteId}/solicitudes`

export const registrarSolicitud = (clienteId: number, s: SolicitudEntrada) =>
  tributarioApi.post<SolicitudEspecial>(ruta(clienteId), s).then((r) => r.data)

/** Los pedidos en seguimiento (PENDIENTE), el de entrega más próxima primero. */
export const listarSolicitudesPendientes = (clienteId: number) =>
  tributarioApi.get<SolicitudEspecial[]>(ruta(clienteId)).then((r) => r.data)

export const marcarSolicitudAtendida = (clienteId: number, solicitudId: number) =>
  tributarioApi.post<SolicitudEspecial>(`${ruta(clienteId)}/${solicitudId}/atender`).then((r) => r.data)
