import { tributarioApi } from './apiClient'
import type { ActivoFijo, Cliente, ResumenSocietario, Socio } from '../types'

const base = (id: number) => `/api/tributario/clientes/${id}`

export const listarClientes = async () => (await tributarioApi.get<Cliente[]>('/api/tributario/clientes')).data
export const obtenerCliente = async (id: number) => (await tributarioApi.get<Cliente>(base(id))).data
export const crearCliente = async (c: Cliente) => (await tributarioApi.post<Cliente>('/api/tributario/clientes', c)).data
export const actualizarCliente = async (id: number, c: Cliente) => (await tributarioApi.put<Cliente>(base(id), c)).data

export const obtenerResumenSocietario = async (id: number) =>
  (await tributarioApi.get<ResumenSocietario>(`${base(id)}/socios/resumen`)).data
export const guardarSocios = async (id: number, socios: Socio[]) =>
  (await tributarioApi.put<ResumenSocietario>(`${base(id)}/socios`, socios)).data
export const actualizarAporte = async (id: number, socioId: number, aporte: number) =>
  (await tributarioApi.patch<ResumenSocietario>(`${base(id)}/socios/${socioId}/aporte`, { aporte })).data

export const listarActivos = async (id: number) => (await tributarioApi.get<ActivoFijo[]>(`${base(id)}/activos`)).data
export const agregarActivo = async (id: number, a: ActivoFijo) =>
  (await tributarioApi.post<ActivoFijo>(`${base(id)}/activos`, a)).data
export const eliminarActivo = async (id: number, aid: number) => tributarioApi.delete(`${base(id)}/activos/${aid}`)
