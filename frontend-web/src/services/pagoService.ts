import { tributarioApi } from './apiClient'
import type { Obligacion } from './obligacionService'

// ---- HU-13: pagos de una obligación (API del servidor, #35)

/** Un pago registrado, con el acumulado y el saldo que quedaron después de él. */
export interface Pago {
  id: number
  fechaPago: string
  montoPagado: number
  numeroConstancia: string
  acumuladoDespues: number
  saldoDespues: number
}

/** El comprobante es el número de constancia: no se carga ningún archivo (alcance reducido de Beto, 10/11). */
export interface PagoEntrada {
  fechaPago: string
  montoPagado: number
  numeroConstancia: string
}

/** Lo que responde registrar un pago: la obligación con su estado nuevo, el acumulado, el saldo y la cantidad de pagos. */
export interface ObligacionPagada extends Obligacion {
  montoAcumuladoPagado: number
  saldo: number
  numeroPagos: number
}

const ruta = (obligacionId: number) => `/api/tributario/obligaciones/${obligacionId}/pagos`

export const listarPagos = (obligacionId: number) =>
  tributarioApi.get<Pago[]>(ruta(obligacionId)).then((r) => r.data)

/** El servidor recibe un formulario (multipart) con la fecha, el monto y la constancia; sin archivo. */
export function registrarPago(obligacionId: number, pago: PagoEntrada) {
  const cuerpo = new FormData()
  cuerpo.append('fechaPago', pago.fechaPago)
  cuerpo.append('montoPagado', String(pago.montoPagado))
  cuerpo.append('numeroConstancia', pago.numeroConstancia)
  return tributarioApi.post<ObligacionPagada>(ruta(obligacionId), cuerpo).then((r) => r.data)
}
