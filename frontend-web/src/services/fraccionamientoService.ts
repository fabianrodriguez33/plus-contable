import { tributarioApi } from './apiClient'

export type EstadoCuota = 'PENDIENTE' | 'PAGADA'
export type SituacionCuotaFiltro = 'PROXIMO' | 'VENCIDO'

export interface Cuota {
  id: number
  numero: number
  fechaVencimiento: string
  monto: number
  estado: EstadoCuota
  fechaPago?: string | null
  /** Cuota PENDIENTE con fecha anterior a hoy: riesgo de perder el fraccionamiento. */
  enRiesgo: boolean
}

/** Una cuota vista en el calendario: `vencida` es solo la fecha pasada, esté pagada o no. */
export interface CuotaCalendario {
  cuota: Cuota
  fraccionamientoId: number
  diasRestantes: number
  vencida: boolean
}

export interface FiltroCuotas {
  desde?: string
  hasta?: string
  estado?: EstadoCuota
  situacion?: SituacionCuotaFiltro
}

/** HU-17: cuotas de los fraccionamientos de un cliente, con los filtros que expone la API. */
export const consultarCuotas = (clienteId: number, filtro: FiltroCuotas = {}) =>
  tributarioApi
    .get<CuotaCalendario[]>(`/api/tributario/clientes/${clienteId}/fraccionamientos/calendario`, { params: filtro })
    .then((r) => r.data)
