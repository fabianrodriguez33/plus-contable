import type { CalendarioItem } from '../services/calendarioService'

export interface Rango {
  desde: string
  hasta: string
}

const dos = (n: number) => String(n).padStart(2, '0')

/** Fecha local como AAAA-MM-DD (sin pasar por UTC, para no correr el día). */
export function aIso(fecha: Date): string {
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}`
}

/** Convierte AAAA-MM-DD en fecha local (mediodía, para evitar saltos por horario de verano). */
export function deIso(iso: string): Date {
  const [a, m, d] = iso.split('-').map(Number)
  return new Date(a, m - 1, d, 12)
}

/** Primer y último día del mes; mes de 1 a 12. */
export function rangoDelMes(anio: number, mes: number): Rango {
  const ultimo = new Date(anio, mes, 0).getDate()
  return { desde: `${anio}-${dos(mes)}-01`, hasta: `${anio}-${dos(mes)}-${dos(ultimo)}` }
}

/** Semana de lunes a domingo que contiene la fecha. */
export function rangoDeLaSemana(fecha: Date): Rango {
  const base = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate(), 12)
  const retroceso = (base.getDay() + 6) % 7
  const lunes = new Date(base)
  lunes.setDate(base.getDate() - retroceso)
  const domingo = new Date(lunes)
  domingo.setDate(lunes.getDate() + 6)
  return { desde: aIso(lunes), hasta: aIso(domingo) }
}

export interface GrupoDia {
  fecha: string
  items: CalendarioItem[]
}

/** Agrupa por día de vencimiento, en orden cronológico; dentro del día, por cliente y tributo. */
export function agruparPorDia(items: CalendarioItem[], nombreCliente: (clienteId: number) => string): GrupoDia[] {
  const porDia = new Map<string, CalendarioItem[]>()
  for (const item of items) {
    const dia = item.obligacion.fechaVencimiento
    porDia.set(dia, [...(porDia.get(dia) ?? []), item])
  }
  return [...porDia.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([fecha, lista]) => ({
      fecha,
      items: [...lista].sort((x, y) =>
        nombreCliente(x.obligacion.clienteId).localeCompare(nombreCliente(y.obligacion.clienteId))
        || (x.obligacion.tipoObligacion ?? '').localeCompare(y.obligacion.tipoObligacion ?? '')
        || x.obligacion.id - y.obligacion.id),
    }))
}

export type SituacionVisual = 'DECLARADA' | 'VENCIDA' | 'HOY' | 'PROXIMA'

/** Una obligación declarada nunca se muestra como vencida, aunque su fecha ya haya pasado. */
export function situacionVisual(item: CalendarioItem): SituacionVisual {
  if (item.obligacion.estado === 'DECLARADO') return 'DECLARADA'
  if (item.vencimientoPasado) return 'VENCIDA'
  if (item.diasRestantes === 0) return 'HOY'
  return 'PROXIMA'
}

export function etiquetaSituacion(item: CalendarioItem): string {
  const dias = item.diasRestantes
  switch (situacionVisual(item)) {
    case 'DECLARADA': return 'Declarada'
    case 'VENCIDA': return `Vencida hace ${-dias} ${-dias === 1 ? 'día' : 'días'}`
    case 'HOY': return 'Vence hoy'
    default: return dias === 1 ? 'Vence mañana' : `Vence en ${dias} días`
  }
}

/** Ejecuta `tarea` sobre cada elemento con a lo sumo `limite` en curso a la vez; conserva el orden. */
export async function mapConLimite<T, R>(elementos: T[], limite: number, tarea: (e: T) => Promise<R>): Promise<R[]> {
  const resultados: R[] = new Array(elementos.length)
  let siguiente = 0
  const trabajador = async () => {
    while (siguiente < elementos.length) {
      const i = siguiente++
      resultados[i] = await tarea(elementos[i])
    }
  }
  await Promise.all(Array.from({ length: Math.min(limite, elementos.length) }, trabajador))
  return resultados
}
