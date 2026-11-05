import { tributarioApi } from './apiClient'
import type { Obligacion } from './obligacionService'

export interface CalendarioItem {
  obligacion: Obligacion
  diasRestantes: number
  /** Verdadero también si la obligación ya fue declarada: para mostrarla, ver `situacionVisual`. */
  vencimientoPasado: boolean
}

export interface FiltroCalendario {
  desde?: string
  hasta?: string
  estado?: 'PENDIENTE' | 'DECLARADO'
}

export const consultarCalendario = (clienteId: number, filtro: FiltroCalendario = {}) =>
  tributarioApi
    .get<CalendarioItem[]>('/api/tributario/calendario', { params: { clienteId, ...filtro } })
    .then((r) => r.data)
