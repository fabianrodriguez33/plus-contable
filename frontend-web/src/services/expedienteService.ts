import { tributarioApi } from './apiClient'

const base = (clienteId: number) => `/api/tributario/clientes/${clienteId}`

// ---- HU-03 ----
export type SistemaPensionario = 'ONP' | 'AFP'
export type TipoDocumento = 'DNI' | 'CE' | 'PASAPORTE' | 'PTP'

export interface Trabajador {
  id?: number
  nombreCompleto: string
  tipoDocumento: TipoDocumento
  numeroDocumento: string
  extranjero: boolean
  nacionalidad?: string
  sistemaPensionario: SistemaPensionario
  nombreAfp?: string
  fechaIngreso: string
  remuneracion?: number
  tipoContrato?: string
  fechaInicioContrato?: string
  fechaFinContrato?: string
}

export const listarTrabajadores = (clienteId: number) =>
  tributarioApi.get<Trabajador[]>(`${base(clienteId)}/trabajadores`).then((r) => r.data)

export const registrarTrabajador = (clienteId: number, t: Trabajador) =>
  tributarioApi.post<Trabajador>(`${base(clienteId)}/trabajadores`, t).then((r) => r.data)

// ---- HU-04 ----
export type TipoDocumentoLegal = 'MINUTA' | 'VIGENCIA_PODERES' | 'FICHA_RUC'

export interface DocumentoLegal {
  id: number
  tipo: TipoDocumentoLegal
  version: number
  fechaEmision: string
  nombreArchivo: string
  tamanoBytes: number
  subidoEn: string
}

export const listarDocumentos = (clienteId: number) =>
  tributarioApi.get<DocumentoLegal[]>(`${base(clienteId)}/documentos`).then((r) => r.data)

export function archivarDocumento(clienteId: number, tipo: TipoDocumentoLegal, fechaEmision: string, archivo: File) {
  const form = new FormData()
  form.append('tipo', tipo)
  form.append('fechaEmision', fechaEmision)
  form.append('archivo', archivo)
  return tributarioApi.post<DocumentoLegal>(`${base(clienteId)}/documentos`, form).then((r) => r.data)
}

export const urlDescarga = (clienteId: number, documentoId: number) =>
  `${base(clienteId)}/documentos/${documentoId}/archivo`

// ---- HU-05 ----
export interface Acuerdo {
  id?: number
  serviciosContratados: string
  honorarioMensual: number
  fechaInicio: string
  historial?: { monto: number; vigenteDesde: string; vigenteHasta: string }[]
}

export const obtenerAcuerdo = (clienteId: number) =>
  tributarioApi.get<Acuerdo>(`${base(clienteId)}/acuerdo`).then((r) => r.data)

export const guardarAcuerdo = (clienteId: number, a: Acuerdo) =>
  tributarioApi.put<Acuerdo>(`${base(clienteId)}/acuerdo`, a).then((r) => r.data)
