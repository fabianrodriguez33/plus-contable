import { tributarioApi } from './apiClient'

export type Regimen = 'MYPE' | 'REMIPE' | 'GENERAL'
export type TipoDocumento = 'MINUTA' | 'COPIA_LITERAL' | 'VIGENCIA_PODERES' | 'FICHA_RUC'

export interface Cliente {
  id?: number
  ruc: string
  razonSocial: string
  regimenTributario: Regimen
  condicionHabido: 'HABIDO' | 'NO_HABIDO'
  direccion?: string
  usuarioSol?: string
  claveSol?: string
  tieneClaveSol?: boolean
}

export interface Documento {
  id: number
  tipo: TipoDocumento
  nombreArchivo: string
  tamanoBytes: number
  subidoEn: string
}

export interface Socio {
  id?: number
  nombreCompleto: string
  tipoDocumento: string
  numeroDocumento: string
  porcentaje: number
  cargo?: string
}

export interface Trabajador {
  id?: number
  nombreCompleto: string
  tipoDocumento: string
  numeroDocumento: string
  extranjero: boolean
  nacionalidad?: string
  regimenLaboral: Regimen
  sistemaPensionario: 'AFP' | 'ONP'
  nombreAfp?: string
  fechaIngreso?: string
  remuneracion?: number
  registroSunafil: boolean
  registroMtpe: boolean
}

const base = (id: number) => `/api/clientes/${id}`

export const listarClientes = async () => (await tributarioApi.get<Cliente[]>('/api/clientes')).data
export const obtenerCliente = async (id: number) => (await tributarioApi.get<Cliente>(base(id))).data
export const crearCliente = async (c: Cliente) => (await tributarioApi.post<Cliente>('/api/clientes', c)).data
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
  const r = await tributarioApi.get(`/api/documentos/${doc.id}/descargar`, { responseType: 'blob' })
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
