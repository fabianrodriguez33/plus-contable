import { tributarioApi } from './apiClient'
import type { EstadoObligacion } from './obligacionService'

/** HU-16: una obligación vencida y sin ninguna constancia de pago. Solo el estudio la consulta. */
export interface ObligacionVencidaSinPago {
  obligacionId: number
  clienteId: number
  ruc: string
  razonSocial: string
  tipoObligacion: string | null
  periodo: string
  fechaVencimiento: string
  diasVencida: number
  estado: EstadoObligacion
  montoDeterminado: number | null
}

export const listarVencidasSinPago = (clienteId?: number) =>
  tributarioApi.get<ObligacionVencidaSinPago[]>('/api/tributario/obligaciones-vencidas-sin-pago',
    { params: clienteId === undefined ? {} : { clienteId } }).then((r) => r.data)
