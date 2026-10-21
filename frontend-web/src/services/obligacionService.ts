import { tributarioApi } from './apiClient'

// PROVISIONAL: la obligacion es de HU-07 (Ricardo); aqui solo lo que usan HU-09 y HU-10
export interface Obligacion {
  id: number
  clienteId: number
  periodo: string
  fechaVencimiento: string
  estado: 'PENDIENTE' | 'DECLARADO'
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

export const buscarObligacion = (id: number) =>
  tributarioApi.get<Obligacion>(`${base}/${id}`).then((r) => r.data)

export const declarar = (id: number, d: Declaracion) =>
  tributarioApi.patch<Obligacion>(`${base}/${id}/declaracion`, d).then((r) => r.data)

export const cambiarVencimiento = (obligacionIds: number[], nuevaFecha: string) =>
  tributarioApi.patch<Obligacion[]>(`${base}/vencimiento`, { obligacionIds, nuevaFecha }).then((r) => r.data)
