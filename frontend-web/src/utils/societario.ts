import type { Socio } from '../types'

export interface EstadoEstructura {
  capitalSocialTotal: number
  /** Socios cuyo aporte es 0 o no es válido: sin aporte no se puede calcular su porcentaje. */
  sinAporte: number
  /** Socios agregados en pantalla que todavía no se guardaron: al guardar cambian los porcentajes de todos. */
  sinGuardar: number
  puedeGuardar: boolean
}

const aCentimos = (n: number) => Math.round((Number(n) || 0) * 100)

/**
 * Estado de la estructura societaria en pantalla. El porcentaje de cada socio no se calcula aquí: lo calcula el
 * servidor según el aporte (regla del mayor resto) y se muestra después de guardar.
 */
export function estadoEstructura(socios: Pick<Socio, 'id' | 'aporte'>[]): EstadoEstructura {
  const sinAporte = socios.filter((s) => aCentimos(s.aporte) <= 0).length
  return {
    capitalSocialTotal: socios.reduce((acc, s) => acc + aCentimos(s.aporte), 0) / 100,
    sinAporte,
    sinGuardar: socios.filter((s) => s.id === undefined).length,
    puedeGuardar: socios.length > 0 && sinAporte === 0,
  }
}

export const soles = (n: number) =>
  new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(Number(n) || 0)

export function mensajeDiferencia(diferencia: number): string {
  if (diferencia > 0) return `Faltan ${diferencia.toFixed(2)}% para llegar a 100.00%`
  if (diferencia < 0) return `Sobran ${Math.abs(diferencia).toFixed(2)}% sobre 100.00%`
  return 'La participación suma exactamente 100.00%'
}
