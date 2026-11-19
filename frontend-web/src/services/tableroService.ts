import { tributarioApi } from './apiClient'
import type { EstadoObligacion } from './obligacionService'
import type { TipoObligacion } from './programacionService'

/** Una obligación del tablero Declarado vs. Pagado (HU-14). El servidor calcula lo pagado, el saldo y si está vencida. */
export interface FilaTablero {
  obligacionId: number
  clienteId: number
  ruc: string
  razonSocial: string
  tipoObligacion: TipoObligacion | null
  /** Primer día del mes tributario (AAAA-MM-01). */
  periodo: string
  fechaVencimiento: string
  estado: EstadoObligacion
  montoDeterminado: number | null
  montoPagado: number
  saldo: number | null
  /** Fecha límite pasada y saldo pendiente (declarada sin pago o con pago parcial); nunca las pagadas ni las sin declarar. */
  vencida: boolean
  /** Sin declarar con la fecha límite pasada: pendiente de atención, aparte de las vencidas. */
  sinDeclarar: boolean
}

export interface FiltroTablero {
  clienteId?: number
  /** Primer día del mes (AAAA-MM-01). */
  periodo?: string
  soloDeclaradoSinPago?: boolean
}

export const consultarTablero = (filtro: FiltroTablero = {}) =>
  tributarioApi
    .get<FilaTablero[]>('/api/tributario/tablero', {
      params: { clienteId: filtro.clienteId, periodo: filtro.periodo, soloDeclaradoSinPago: filtro.soloDeclaradoSinPago ? true : undefined },
    })
    .then((r) => r.data)
