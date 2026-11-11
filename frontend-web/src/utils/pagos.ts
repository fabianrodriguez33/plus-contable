import type { Obligacion } from '../services/obligacionService'
import type { Pago } from '../services/pagoService'

export const CONSTANCIA_MAXIMO = 30

export const soles = (n: number) => `S/ ${n.toFixed(2)}`

const centimos = (n: number) => Math.round(n * 100)

/** Acumulado y saldo tras el último pago registrado (el de mayor id). Sin pagos: 0 y el monto determinado. */
export function resumenDePagos(o: Obligacion, pagos: Pago[]): { acumulado: number; saldo: number } {
  const ultimo = pagos.reduce<Pago | null>((a, p) => (!a || p.id > a.id ? p : a), null)
  return { acumulado: ultimo?.acumuladoDespues ?? 0, saldo: ultimo?.saldoDespues ?? o.montoDeterminado ?? 0 }
}

export interface DatosPago {
  fechaPago: string
  montoPagado: string
  numeroConstancia: string
}

export interface ErroresPago {
  fechaPago?: string
  montoPagado?: string
  numeroConstancia?: string
}

/** Las mismas reglas del servidor, para avisar antes de enviar. `hoy` es AAAA-MM-DD. */
export function validarPago(d: DatosPago, saldo: number, hoy: string, existentes: Pago[]): ErroresPago {
  const e: ErroresPago = {}

  if (!d.fechaPago) e.fechaPago = 'Indique la fecha del pago'
  else if (d.fechaPago > hoy) e.fechaPago = 'La fecha del pago no puede ser futura'

  const monto = Number(d.montoPagado)
  if (d.montoPagado.trim() === '' || Number.isNaN(monto)) e.montoPagado = 'Indique el monto pagado'
  else if (monto <= 0) e.montoPagado = 'El monto debe ser mayor que cero'
  else if (Math.abs(centimos(monto) - monto * 100) > 1e-6) e.montoPagado = 'Use como máximo dos decimales'
  else if (centimos(monto) > centimos(saldo)) e.montoPagado = `El monto supera el saldo pendiente de ${soles(saldo)}`

  const constancia = d.numeroConstancia.trim()
  if (!constancia) e.numeroConstancia = 'Indique el número de constancia'
  else if (constancia.length > CONSTANCIA_MAXIMO) e.numeroConstancia = `El número de constancia admite hasta ${CONSTANCIA_MAXIMO} caracteres`
  else if (existentes.some((p) => p.numeroConstancia === constancia)) e.numeroConstancia = 'Ese número de constancia ya está registrado en esta obligación'

  return e
}
