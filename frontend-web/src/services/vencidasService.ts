import { tributarioApi } from './apiClient'
import type { EstadoObligacion } from './obligacionService'

/** HU-16: una obligación de la cartera vencida o sin declarar. montoPagado y saldo vienen del servidor (suma de los pagos de HU-13). */
export interface ObligacionVencida {
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
  montoPagado: number
  /** Nulo si la obligación todavía no tiene monto determinado. */
  saldo: number | null
}

/** Definición de Beto: «vencidas» = fecha pasada y saldo pendiente (declarada sin pago o con pago parcial); «sinDeclarar» va aparte. */
export interface ObligacionesVencidas {
  vencidas: ObligacionVencida[]
  sinDeclarar: ObligacionVencida[]
}

export const listarVencidas = (clienteId?: number) =>
  tributarioApi.get<ObligacionesVencidas>('/api/tributario/obligaciones-vencidas',
    { params: clienteId === undefined ? {} : { clienteId } }).then((r) => r.data)
