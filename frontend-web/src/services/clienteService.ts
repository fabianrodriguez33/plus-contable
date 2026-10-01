import { tributarioApi } from './apiClient'
import type { ActivoFijo, Cliente, Documento, Socio, TipoDocumento, Trabajador } from '../types'

const base = (id: number) => `/api/tributario/clientes/${id}`

export const listarClientes = async () => (await tributarioApi.get<Cliente[]>('/api/tributario/clientes')).data
export const obtenerCliente = async (id: number) => (await tributarioApi.get<Cliente>(base(id))).data
export const crearCliente = async (c: Cliente) => (await tributarioApi.post<Cliente>('/api/tributario/clientes', c)).data
export const actualizarCliente = async (id: number, c: Cliente) => (await tributarioApi.put<Cliente>(base(id), c)).data

export const listarDocumentos = async (id: number) =>
  (await tributarioApi.get<Documento[]>(`${base(id)}/documentos`)).data
export async function subirDocumento(id: number, tipo: TipoDocumento, archivo: File) {
  const form = new FormData()
  form.append('tipo', tipo)
  form.append('archivo', archivo)
  return (await tributarioApi.post<Documento>(`${base(id)}/documentos`, form)).data
}
export const eliminarDocumento = async (id: number, docId: number) =>
  tributarioApi.delete(`${base(id)}/documentos/${docId}`)
export async function descargarDocumento(doc: Documento) {
  const r = await tributarioApi.get(`/api/tributario/documentos/${doc.id}/descargar`, { responseType: 'blob' })
  const url = URL.createObjectURL(r.data)
  const a = document.createElement('a')
  a.href = url
  a.download = doc.nombreArchivo
  a.click()
  URL.revokeObjectURL(url)
}

export const listarSocios = async (id: number) => (await tributarioApi.get<Socio[]>(`${base(id)}/socios`)).data
export const guardarSocios = async (id: number, socios: Socio[]) =>
  (await tributarioApi.put<Socio[]>(`${base(id)}/socios`, socios)).data

export const listarTrabajadores = async (id: number) =>
  (await tributarioApi.get<Trabajador[]>(`${base(id)}/trabajadores`)).data
export const agregarTrabajador = async (id: number, t: Trabajador) =>
  (await tributarioApi.post<Trabajador>(`${base(id)}/trabajadores`, t)).data
export const eliminarTrabajador = async (id: number, tid: number) =>
  tributarioApi.delete(`${base(id)}/trabajadores/${tid}`)

export const listarActivos = async (id: number) => (await tributarioApi.get<ActivoFijo[]>(`${base(id)}/activos`)).data
export const agregarActivo = async (id: number, a: ActivoFijo) =>
  (await tributarioApi.post<ActivoFijo>(`${base(id)}/activos`, a)).data
export const eliminarActivo = async (id: number, aid: number) => tributarioApi.delete(`${base(id)}/activos/${aid}`)
