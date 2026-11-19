import { tributarioApi } from './apiClient'

// ---- HU-15 ----
export type EstadoRecordatorio = 'PROGRAMADO' | 'ENVIADO' | 'CANCELADO' | 'OMITIDO'

export interface RecordatorioPago {
  id: number
  obligacionId: number
  fechaRecordatorio: string
  estado: EstadoRecordatorio
  programadoPor: string
  programadoEn: string
  resueltoEn: string | null
  /** Saldo pendiente de la obligación hoy: es lo que recibirá el cliente si se envía ahora. Nulo sin monto determinado. */
  saldo: number | null
}

const ruta = (obligacionId: number) => `/api/tributario/obligaciones/${obligacionId}/recordatorios-pago`

/** Solo el estudio (CONTADOR y ADMIN) programa, consulta y cancela. */
export const listarRecordatorios = (obligacionId: number) =>
  tributarioApi.get<RecordatorioPago[]>(ruta(obligacionId)).then((r) => r.data)

export const programarRecordatorio = (obligacionId: number, fechaRecordatorio: string) =>
  tributarioApi.post<RecordatorioPago>(ruta(obligacionId), { fechaRecordatorio }).then((r) => r.data)

export const cancelarRecordatorio = (obligacionId: number, recordatorioId: number) =>
  tributarioApi.patch<RecordatorioPago>(`${ruta(obligacionId)}/${recordatorioId}/cancelacion`).then((r) => r.data)
