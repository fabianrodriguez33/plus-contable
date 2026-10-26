import type { Socio } from '../types'

const aCentimos = (n: number) => Math.round((Number(n) || 0) * 100)

export interface ResumenLocal {
  capitalSocialTotal: number
  porcentajeTotal: number
  /** 100 menos el porcentaje total: positivo = faltan puntos, negativo = sobran. */
  diferencia: number
  completo: boolean
}

/** Totales calculados en centésimas para evitar errores de coma flotante (p. ej. 33.33 + 33.33 + 33.34). */
export function resumenLocal(socios: Pick<Socio, 'aporte' | 'porcentaje'>[]): ResumenLocal {
  const aportes = socios.reduce((acc, s) => acc + aCentimos(s.aporte), 0)
  const porcentajes = socios.reduce((acc, s) => acc + aCentimos(s.porcentaje), 0)
  const diferencia = 10000 - porcentajes
  return {
    capitalSocialTotal: aportes / 100,
    porcentajeTotal: porcentajes / 100,
    diferencia: diferencia / 100,
    completo: socios.length > 0 && diferencia === 0,
  }
}

export const soles = (n: number) =>
  new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(Number(n) || 0)

export function mensajeDiferencia(diferencia: number): string {
  if (diferencia > 0) return `Faltan ${diferencia.toFixed(2)}% para llegar a 100.00%`
  if (diferencia < 0) return `Sobran ${Math.abs(diferencia).toFixed(2)}% sobre 100.00%`
  return 'La participación suma exactamente 100.00%'
}
