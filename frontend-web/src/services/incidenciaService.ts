import { tributarioApi } from './apiClient'

// ---- HU-23 ----
export type TipoIncidencia = 'CAIDA_PORTAL_SOL' | 'CLIENTE_NO_ENVIA_DOCUMENTOS' | 'RECHAZO_FRACCIONAMIENTO' | 'OTRO'

export interface Incidencia {
  id: number
  tipo: TipoIncidencia
  fecha: string
  descripcion: string
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
}

export const registrarIncidencia = (entrada: IncidenciaEntrada) =>
  tributarioApi.post<Incidencia>('/api/tributario/incidencias', entrada).then((r) => r.data)

/** Las incidencias del cliente y las generales, la más reciente primero. */
export const listarIncidencias = (clienteId: number) =>
  tributarioApi.get<Incidencia[]>(`/api/tributario/clientes/${clienteId}/incidencias`).then((r) => r.data)
