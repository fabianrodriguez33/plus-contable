import { tributarioApi } from './apiClient'

// ---- HU-25 ----
/** Catálogo acordado con Beto (18/11): «Mercadería o gasto» y «Activo fijo». */
export type TipoCompra = 'MERCADERIA_O_GASTO' | 'ACTIVO_FIJO'

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
  clasificada: boolean
  tipo: TipoCompra | null
  descripcion: string | null
  clasificadaEn: string | null
  /** El servidor la marca cuando la compra sigue sin clasificar y el cierre del mes está cerca (días configurables). */
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

/** Solo el cliente clasifica, y solo en su empresa. Una compra clasificada no se cambia. */
export const clasificarCompra = (clienteId: number, compraId: number, tipo: TipoCompra, descripcion: string) =>
  tributarioApi.patch<Compra>(`${ruta(clienteId)}/${compraId}/clasificacion`, { tipo, descripcion }).then((r) => r.data)
