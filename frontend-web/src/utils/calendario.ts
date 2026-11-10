import type { CalendarioItem } from '../services/calendarioService'
import type { CuotaCalendario } from '../services/fraccionamientoService'

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

/** Lo que se lista en un día del calendario: una obligación o una cuota de fraccionamiento (HU-17). */
export type Entrada =
  | { tipo: 'OBLIGACION'; clienteId: number; fecha: string; item: CalendarioItem }
  | { tipo: 'CUOTA'; clienteId: number; fecha: string; item: CuotaCalendario }

export const entradaDeObligacion = (item: CalendarioItem): Entrada =>
  ({ tipo: 'OBLIGACION', clienteId: item.obligacion.clienteId, fecha: item.obligacion.fechaVencimiento, item })

export const entradaDeCuota = (clienteId: number, item: CuotaCalendario): Entrada =>
  ({ tipo: 'CUOTA', clienteId, fecha: item.cuota.fechaVencimiento, item })

export interface GrupoEntradas {
  fecha: string
  entradas: Entrada[]
}

const idDe = (e: Entrada) => (e.tipo === 'OBLIGACION' ? e.item.obligacion.id : e.item.cuota.id)
const claveDe = (e: Entrada) =>
  e.tipo === 'OBLIGACION' ? `0 ${e.item.obligacion.tipoObligacion ?? ''}` : `1 ${String(e.item.cuota.numero).padStart(4, '0')}`

/** Agrupa por día de vencimiento; dentro del día, por cliente, obligaciones antes que cuotas, y por tributo o número de cuota. */
export function agruparEntradasPorDia(entradas: Entrada[], nombreCliente: (clienteId: number) => string): GrupoEntradas[] {
  const porDia = new Map<string, Entrada[]>()
  for (const e of entradas) porDia.set(e.fecha, [...(porDia.get(e.fecha) ?? []), e])
  return [...porDia.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([fecha, lista]) => ({
      fecha,
      entradas: [...lista].sort((x, y) =>
        nombreCliente(x.clienteId).localeCompare(nombreCliente(y.clienteId))
        || claveDe(x).localeCompare(claveDe(y))
        || idDe(x) - idDe(y)),
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
