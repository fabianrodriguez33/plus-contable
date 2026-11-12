import { tributarioApi } from './apiClient'

// ---- HU-24 ----
/** SIN_RESPUESTA no se guarda: es un PENDIENTE con la fecha límite vencida. */
export type EstadoDocumentoFaltante = 'PENDIENTE' | 'ENTREGADO' | 'SIN_RESPUESTA'

export interface DocumentoFaltante {
  id: number
  clienteId: number
  documento: string
  fechaSolicitud: string
  fechaLimite: string
  estado: EstadoDocumentoFaltante
  fechaEntrega: string | null
  /** La incidencia de HU-23 con la que se registró que no lo envió. */
  incidenciaId: number | null
}

export interface DocumentoFaltanteEntrada {
  documento: string
  fechaLimite: string
  /** Si no se envía, el servidor usa la fecha de hoy. */
  fechaSolicitud?: string
}

const ruta = (clienteId: number) => `/api/tributario/clientes/${clienteId}/documentos-faltantes`

export const registrarDocumentoFaltante = (clienteId: number, d: DocumentoFaltanteEntrada) =>
  tributarioApi.post<DocumentoFaltante>(ruta(clienteId), d).then((r) => r.data)

/** Los documentos del cliente con su estado, el de fecha límite más próxima primero. */
export const listarDocumentosFaltantes = (clienteId: number) =>
  tributarioApi.get<DocumentoFaltante[]>(ruta(clienteId)).then((r) => r.data)

export const registrarEntregaDocumento = (clienteId: number, documentoId: number) =>
  tributarioApi.post<DocumentoFaltante>(`${ruta(clienteId)}/${documentoId}/entrega`).then((r) => r.data)

export const registrarDocumentoComoIncidencia = (clienteId: number, documentoId: number) =>
  tributarioApi.post<DocumentoFaltante>(`${ruta(clienteId)}/${documentoId}/incidencia`).then((r) => r.data)
