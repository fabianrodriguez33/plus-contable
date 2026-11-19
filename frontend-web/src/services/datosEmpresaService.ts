import { tributarioApi } from './apiClient'

// ---- HU-35 ----
export type TipoDocumento = 'MINUTA' | 'VIGENCIA_PODERES' | 'FICHA_RUC'

export interface DatosEmpresa {
  clienteId: number
  ruc: string
  razonSocial: string
  regimenTributario: string
  direccion: string | null
  telefono: string | null
  email: string | null
  socios: { nombreCompleto: string; cargo: string | null; porcentaje: number }[]
  /** Un documento por tipo; sin subir, id y fechaEmision vienen null. */
  documentos: { tipo: TipoDocumento; id: number | null; version: number | null; fechaEmision: string | null }[]
}

/** Solo lectura. El cliente únicamente puede pedir los datos de su propia empresa. */
export const obtenerDatosEmpresa = (clienteId: number) =>
  tributarioApi.get<DatosEmpresa>(`/api/tributario/clientes/${clienteId}/datos-empresa`).then((r) => r.data)
