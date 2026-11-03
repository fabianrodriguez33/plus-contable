import type { TipoObligacion } from '../services/programacionService'

const ETIQUETAS: Record<TipoObligacion, string> = {
  IGV: 'IGV',
  RENTA: 'Renta',
  PLAME: 'PLAME',
  ESSALUD: 'EsSalud',
  RETENCION_QUINTA: 'Retención de quinta categoría',
  AFP: 'AFP',
}

export const etiquetaTipo = (tipo: TipoObligacion) => ETIQUETAS[tipo] ?? tipo

/** yyyy-MM-dd -> dd/MM/yyyy, sin pasar por Date para no mover el día por la zona horaria. */
export function fechaCorta(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  return m ? `${m[3]}/${m[2]}/${m[1]}` : iso
}

/** Mes tributario que vence ahora: el anterior al de la fecha dada, como "AAAA-MM". */
export function mesAnterior(hoy: Date): string {
  const d = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

export const periodoValido = (p: string) => /^\d{4}-(0[1-9]|1[0-2])$/.test(p)
