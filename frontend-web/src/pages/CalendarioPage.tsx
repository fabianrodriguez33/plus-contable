import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { TriangleAlert } from 'lucide-react'
import axios from 'axios'
import { listarClientes } from '../services/clienteService'
import { consultarCalendario } from '../services/calendarioService'
import {
  consultarCuotas, type EstadoCuota, type FiltroCuotas, type SituacionCuotaFiltro,
} from '../services/fraccionamientoService'
import { mensajeError } from '../services/apiClient'
import type { Cliente } from '../types'
import Card from '../components/Card'
import Button from '../components/Button'
import { Input, Select } from '../components/Input'
import {
  agruparEntradasPorDia, aIso, deIso, entradaDeCuota, entradaDeObligacion, etiquetaCuota, etiquetaSituacion,
  mapConLimite, rangoDeLaSemana, rangoDelMes, situacionCuota, situacionVisual,
  type Entrada, type Rango, type SituacionCuota, type SituacionVisual,
} from '../utils/calendario'

type Vista = 'HOY' | 'SEMANA' | 'MES'
/** Qué se lista: obligaciones y cuotas de fraccionamiento, o solo una de las dos. */
type Mostrar = 'TODO' | 'OBLIGACIONES' | 'CUOTAS'

interface Parte {
  entradas: Entrada[]
  error: string | null
}

const CONSULTAS_EN_PARALELO = 4

const ESTILO: Record<SituacionVisual, string> = {
  DECLARADA: 'bg-emerald-50 text-emerald-700',
  PAGADA: 'bg-emerald-50 text-emerald-700',
  VENCIDA: 'bg-red-50 text-red-700',
  HOY: 'bg-amber-50 text-amber-700',
  PROXIMA: 'bg-slate-100 text-slate-700',
}

const ESTILO_CUOTA: Record<SituacionCuota, string> = {
  PAGADA: 'bg-emerald-50 text-emerald-700',
  RIESGO: 'bg-red-100 text-red-800 font-semibold',
  HOY: 'bg-amber-50 text-amber-700',
  PROXIMA: 'bg-slate-100 text-slate-700',
}

function rangoDe(vista: Vista, hoy: Date, mes: string): Rango {
  if (vista === 'HOY') return { desde: aIso(hoy), hasta: aIso(hoy) }
  if (vista === 'SEMANA') return rangoDeLaSemana(hoy)
  const [anio, m] = mes.split('-').map(Number)
  return rangoDelMes(anio, m)
}

function mensajeCalendario(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 403) {
    return 'No tienes permiso para ver el calendario de vencimientos.'
  }
  return mensajeError(error)
}

const titulo = (iso: string) =>
  deIso(iso).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

/** HU-08 y HU-17: vencimientos y cuotas de fraccionamiento de todos los clientes (o de uno) por día, para el día, la semana o el mes. */
export default function CalendarioPage({ hoy }: { hoy?: Date }) {
  const [fechaHoy] = useState(() => hoy ?? new Date())
  const [vista, setVista] = useState<Vista>('MES')
  const [mes, setMes] = useState(() => aIso(fechaHoy).slice(0, 7))
  const [clienteId, setClienteId] = useState('')
  const [clientes, setClientes] = useState<Cliente[] | null>(null)
  const [mostrar, setMostrar] = useState<Mostrar>('TODO')
  const [estadoFiltro, setEstadoFiltro] = useState<'' | EstadoCuota>('')
  const [situacionFiltro, setSituacionFiltro] = useState<'' | SituacionCuotaFiltro>('')
  const [entradas, setEntradas] = useState<Entrada[]>([])
  const [errores, setErrores] = useState<string[]>([])
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    listarClientes()
      .then(setClientes)
      .catch((e) => { setErrores([mensajeCalendario(e)]); setClientes([]) })
  }, [])

  useEffect(() => {
    if (clientes === null) return
    let vigente = true
    const objetivo = clienteId ? clientes.filter((c) => String(c.id) === clienteId) : clientes
    const rango = rangoDe(vista, fechaHoy, mes)
    // Los filtros de estado y situación son los de la API de cuotas; no se mandan los que están vacíos.
    const filtroCuotas: FiltroCuotas = {
      ...rango, ...(estadoFiltro && { estado: estadoFiltro }), ...(situacionFiltro && { situacion: situacionFiltro }),
    }
    const vacia: Parte = { entradas: [], error: null }
    setCargando(true)
    mapConLimite(objetivo, CONSULTAS_EN_PARALELO, async (c): Promise<Parte[]> => {
      const id = c.id as number
      return Promise.all([
        mostrar === 'CUOTAS' ? vacia : consultarCalendario(id, rango).then(
          (lista): Parte => ({ entradas: lista.map(entradaDeObligacion), error: null }),
          (e): Parte => ({ entradas: [], error: mensajeCalendario(e) }),
        ),
        mostrar === 'OBLIGACIONES' ? vacia : consultarCuotas(id, filtroCuotas).then(
          (lista): Parte => ({ entradas: lista.map((q) => entradaDeCuota(id, q)), error: null }),
          (e): Parte => ({ entradas: [], error: mensajeCalendario(e) }),
        ),
      ])
    }).then((resultados) => {
      if (!vigente) return
      const partes = resultados.flat()
      setEntradas(partes.flatMap((r) => r.entradas))
      setErrores([...new Set(partes.flatMap((r) => (r.error ? [r.error] : [])))])
      setCargando(false)
    })
    return () => { vigente = false }
  }, [clientes, clienteId, vista, mes, fechaHoy, mostrar, estadoFiltro, situacionFiltro])

  const nombre = useMemo(() => {
    const porId = new Map((clientes ?? []).map((c) => [c.id, c.razonSocial]))
    return (id: number) => porId.get(id) ?? `Cliente ${id}`
  }, [clientes])
  const grupos = useMemo(() => agruparEntradasPorDia(entradas, nombre), [entradas, nombre])

  return (
    <Card title="Calendario de vencimientos">
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="flex gap-2" role="group" aria-label="Vista">
          {(['HOY', 'SEMANA', 'MES'] as Vista[]).map((v) => (
            <Button key={v} variant={vista === v ? 'primary' : 'secondary'} aria-pressed={vista === v}
              onClick={() => setVista(v)}>
              {v === 'HOY' ? 'Hoy' : v === 'SEMANA' ? 'Semana' : 'Mes'}
            </Button>
          ))}
        </div>
        {vista === 'MES' && (
          <Input label="Mes" type="month" value={mes} onChange={(e) => e.target.value && setMes(e.target.value)} />
        )}
        <Select label="Cliente" value={clienteId} onChange={(e) => setClienteId(e.target.value)}>
          <option value="">Todos los clientes</option>
          {(clientes ?? []).map((c) => <option key={c.id} value={c.id}>{c.razonSocial}</option>)}
        </Select>
        <Select label="Mostrar" value={mostrar} onChange={(e) => setMostrar(e.target.value as Mostrar)}>
          <option value="TODO">Obligaciones y cuotas</option>
          <option value="OBLIGACIONES">Solo obligaciones</option>
          <option value="CUOTAS">Solo cuotas de fraccionamiento</option>
        </Select>
        {mostrar !== 'OBLIGACIONES' && (
          <>
            <Select label="Estado de la cuota" value={estadoFiltro}
              onChange={(e) => setEstadoFiltro(e.target.value as '' | EstadoCuota)}>
              <option value="">Todas</option>
              <option value="PENDIENTE">Pendiente</option>
              <option value="PAGADA">Pagada</option>
            </Select>
            <Select label="Situación de la cuota" value={situacionFiltro}
              onChange={(e) => setSituacionFiltro(e.target.value as '' | SituacionCuotaFiltro)}>
              <option value="">Todas</option>
              <option value="PROXIMO">Próxima</option>
              <option value="VENCIDO">Vencida</option>
            </Select>
          </>
        )}
      </div>

      {errores.map((m) => (
        <div key={m} role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{m}</div>
      ))}

      {cargando ? (
        <p className="text-sm text-slate-500">Cargando...</p>
      ) : grupos.length === 0 ? (
        <p className="text-sm text-slate-500">No hay vencimientos en este periodo.</p>
      ) : (
        <div className="space-y-5">
          {grupos.map((g) => (
            <section key={g.fecha} aria-label={g.fecha}>
              <h3 className="mb-2 text-sm font-semibold capitalize text-slate-700">{titulo(g.fecha)}</h3>
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                {g.entradas.map((e) => e.tipo === 'OBLIGACION' ? (
                  <li key={`o${e.item.obligacion.id}`} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm">
                    <span>
                      <Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${e.clienteId}`}>
                        {nombre(e.clienteId)}
                      </Link>
                      {' · '}{e.item.obligacion.tipoObligacion ?? 'Obligación'}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${ESTILO[situacionVisual(e.item)]}`}>
                      {etiquetaSituacion(e.item)}
                    </span>
                  </li>
                ) : (
                  <li key={`c${e.item.cuota.id}`} data-riesgo={situacionCuota(e.item) === 'RIESGO'}
                    className={`flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm ${
                      situacionCuota(e.item) === 'RIESGO' ? 'border-l-4 border-red-600 bg-red-50' : ''}`}>
                    <span>
                      <Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${e.clienteId}`}>
                        {nombre(e.clienteId)}
                      </Link>
                      {' · '}Cuota {e.item.cuota.numero} del fraccionamiento · S/ {e.item.cuota.monto.toFixed(2)}
                    </span>
                    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${ESTILO_CUOTA[situacionCuota(e.item)]}`}>
                      {situacionCuota(e.item) === 'RIESGO' && <TriangleAlert size={14} aria-hidden />}
                      {etiquetaCuota(e.item)}
                    </span>
                    {situacionCuota(e.item) === 'RIESGO' && (
                      <span className="w-full text-xs text-red-700">
                        Riesgo de perder el fraccionamiento: la cuota venció sin pago.
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </Card>
  )
}
