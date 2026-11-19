import { tributarioApi } from './apiClient'

// ---- HU-25 ----
/** Catálogo acordado con Beto (18/11): «Mercadería o gasto» y «Activo fijo». */
export type TipoCompra = 'MERCADERIA_O_GASTO' | 'ACTIVO_FIJO'
export type EstadoCompra = 'SIN_CLASIFICAR' | 'PROPUESTA' | 'VALIDADA'

export interface Compra {
  id: number
  clienteId: number
  /** Primer día del mes tributario (AAAA-MM-01). */
  periodo: string
  proveedorRuc: string
  proveedorRazonSocial: string | null
  comprobante: string
  fechaComprobante: string
  importe: number
  /** SIN_CLASIFICAR · PROPUESTA (el cliente propuso; pendiente de validación del contador) · VALIDADA. */
  estado: EstadoCompra
  tipo: TipoCompra | null
  descripcion: string | null
  clasificadaEn: string | null
  validadaPor: string | null
  validadaEn: string | null
  /** Solo para el contador: la compra sigue sin validar y falta poco para el último día del mes. Al cliente siempre le llega falso. */
  alertaCierre: boolean
}

export interface CompraEntrada {
  periodo: string
  proveedorRuc: string
  proveedorRazonSocial?: string
  comprobante: string
  fechaComprobante: string
  importe: number
}

const ruta = (clienteId: number) => `/api/tributario/clientes/${clienteId}/compras`

/** El cliente solo puede pedir las de su empresa; el contador, las de cualquiera. */
export const listarCompras = (clienteId: number) =>
  tributarioApi.get<Compra[]>(ruta(clienteId)).then((r) => r.data)

/** Solo el contador carga compras (con el SIRE como referencia). */
export const registrarCompra = (clienteId: number, c: CompraEntrada) =>
  tributarioApi.post<Compra>(ruta(clienteId), c).then((r) => r.data)

/** Solo el cliente propone la clasificación, y solo en su empresa. Queda pendiente de validación del contador; se puede cambiar hasta que la valide. */
export const proponerClasificacion = (clienteId: number, compraId: number, tipo: TipoCompra, descripcion: string) =>
  tributarioApi.patch<Compra>(`${ruta(clienteId)}/${compraId}/clasificacion`, { tipo, descripcion }).then((r) => r.data)

/** Solo el contador valida la propuesta del cliente tal como está. */
export const validarCompra = (clienteId: number, compraId: number) =>
  tributarioApi.post<Compra>(`${ruta(clienteId)}/${compraId}/validacion`).then((r) => r.data)
