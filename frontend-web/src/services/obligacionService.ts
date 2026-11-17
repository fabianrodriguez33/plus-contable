import { tributarioApi } from './apiClient'

/** PAGO_PARCIAL y PAGADO los fija HU-13 según la suma de pagos (igual que el servidor). */
export type EstadoObligacion = 'PENDIENTE' | 'DECLARADO' | 'PAGO_PARCIAL' | 'PAGADO'

// PROVISIONAL: la obligacion es de HU-07 (Ricardo); aqui solo lo que usan HU-09 y HU-10
export interface Obligacion {
  id: number
  clienteId: number
  tipoObligacion?: string
  periodo: string
  fechaVencimiento: string
  estado: EstadoObligacion
  fechaDeclaracion?: string
  numeroFormulario?: string
  montoDeterminado?: number
}

export interface Declaracion {
  fechaDeclaracion: string
  numeroFormulario: string
  montoDeterminado: number
}

const base = '/api/tributario/obligaciones'

// PROVISIONAL hasta HU-07
export const listarObligaciones = (clienteId: number) =>
  tributarioApi.get<Obligacion[]>(base, { params: { clienteId } }).then((r) => r.data)

export const buscarObligacion = (id: number) =>
  tributarioApi.get<Obligacion>(`${base}/${id}`).then((r) => r.data)

export const declarar = (id: number, d: Declaracion) =>
  tributarioApi.patch<Obligacion>(`${base}/${id}/declaracion`, d).then((r) => r.data)

export const cambiarVencimiento = (obligacionIds: number[], nuevaFecha: string) =>
  tributarioApi.patch<Obligacion[]>(`${base}/vencimiento`, { obligacionIds, nuevaFecha }).then((r) => r.data)
