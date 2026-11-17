import { tributarioApi } from './apiClient'

// ---- HU-23 ----
export type TipoIncidencia =
  | 'CAIDA_PORTAL_SOL' | 'CLIENTE_NO_ENVIA_DOCUMENTOS' | 'RECHAZO_FRACCIONAMIENTO'
  | 'PROBLEMA_SIRE' | 'PROBLEMA_SERVICIOS_EXTERNOS' | 'FALLA_SISTEMA_INTERNO' | 'PROBLEMA_CONEXION_INTERNET'
  | 'ERROR_PROCESO_CONTABLE' | 'OTRO'

export interface Incidencia {
  id: number
  tipo: TipoIncidencia
  fecha: string
  descripcion: string
  /** Notas adicionales; opcional. */
  observaciones?: string | null
  registradaPor: string
  registradaEn: string
  clientesAsociados: number
}

export interface IncidenciaEntrada {
  tipo: TipoIncidencia
  /** Se omite en la incidencia general (caída del portal SOL). */
  clienteId?: number
  fecha: string
  descripcion: string
  /** Opcional: si va vacía no se envía. */
  observaciones?: string
}

export const registrarIncidencia = (entrada: IncidenciaEntrada) =>
  tributarioApi.post<Incidencia>('/api/tributario/incidencias', entrada).then((r) => r.data)

/** Las incidencias del cliente y las generales, la más reciente primero. */
export const listarIncidencias = (clienteId: number) =>
  tributarioApi.get<Incidencia[]>(`/api/tributario/clientes/${clienteId}/incidencias`).then((r) => r.data)

// ---- HU-27 ----
/** Un registro del historial: una incidencia (HU-23) o una solicitud especial (HU-26). */
export interface HistorialItem {
  origen: 'INCIDENCIA' | 'SOLICITUD'
  id: number
  /** TipoIncidencia o TipoSolicitud, según el origen. */
  tipo: string
  /** La fecha de la incidencia o el día en que se pidió la solicitud. */
  fecha: string
  descripcion: string | null
  /** Solo incidencias: la general (caída del portal SOL) es de toda la cartera. */
  general: boolean
  /** Solo solicitudes. */
  estado: 'PENDIENTE' | 'ATENDIDA' | null
  fechaEntrega: string | null
  registradaEn: string
}

/** El historial del cliente, de lo más reciente a lo más antiguo. */
export const listarHistorial = (clienteId: number) =>
  tributarioApi.get<HistorialItem[]>(`/api/tributario/clientes/${clienteId}/historial`).then((r) => r.data)
