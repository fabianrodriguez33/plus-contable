import { tributarioApi } from './apiClient'

// ---- HU-33 ----
export interface AvisoPago {
  id: number
  obligacionId: number
  tributo: string
  monto: number
  fechaVencimiento: string
  fechaAviso: string
}

/** Los avisos de pago del cliente, el más reciente primero. El cliente solo puede pedir los de su empresa. */
export const listarAvisosPago = (clienteId: number) =>
  tributarioApi.get<AvisoPago[]>(`/api/tributario/clientes/${clienteId}/avisos-pago`).then((r) => r.data)
