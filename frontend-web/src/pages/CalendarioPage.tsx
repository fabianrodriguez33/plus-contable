import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import { listarClientes } from '../services/clienteService'
import { consultarCalendario, type CalendarioItem } from '../services/calendarioService'
import { mensajeError } from '../services/apiClient'
import type { Cliente } from '../types'
import Card from '../components/Card'
import Button from '../components/Button'
import { Input, Select } from '../components/Input'
import {
  agruparPorDia, aIso, deIso, etiquetaSituacion, mapConLimite, rangoDeLaSemana, rangoDelMes, situacionVisual,
  type Rango, type SituacionVisual,
} from '../utils/calendario'

type Vista = 'HOY' | 'SEMANA' | 'MES'

const CONSULTAS_EN_PARALELO = 4

const ESTILO: Record<SituacionVisual, string> = {
  DECLARADA: 'bg-emerald-50 text-emerald-700',
  VENCIDA: 'bg-red-50 text-red-700',
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

/** HU-08: vencimientos de todos los clientes (o de uno) por día, para el día, la semana o el mes. */
export default function CalendarioPage({ hoy }: { hoy?: Date }) {
  const [fechaHoy] = useState(() => hoy ?? new Date())
  const [vista, setVista] = useState<Vista>('MES')
  const [mes, setMes] = useState(() => aIso(fechaHoy).slice(0, 7))
  const [clienteId, setClienteId] = useState('')
  const [clientes, setClientes] = useState<Cliente[] | null>(null)
  const [items, setItems] = useState<CalendarioItem[]>([])
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
    setCargando(true)
    mapConLimite(objetivo, CONSULTAS_EN_PARALELO, (c) =>
      consultarCalendario(c.id as number, rango).then(
        (lista) => ({ lista, error: null as string | null }),
        (e) => ({ lista: [] as CalendarioItem[], error: mensajeCalendario(e) }),
      ),
    ).then((resultados) => {
      if (!vigente) return
      setItems(resultados.flatMap((r) => r.lista))
      setErrores([...new Set(resultados.flatMap((r) => (r.error ? [r.error] : [])))])
      setCargando(false)
    })
    return () => { vigente = false }
  }, [clientes, clienteId, vista, mes, fechaHoy])

  const nombre = useMemo(() => {
    const porId = new Map((clientes ?? []).map((c) => [c.id, c.razonSocial]))
    return (id: number) => porId.get(id) ?? `Cliente ${id}`
  }, [clientes])
  const grupos = useMemo(() => agruparPorDia(items, nombre), [items, nombre])

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
                {g.items.map((it) => (
                  <li key={it.obligacion.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm">
                    <span>
                      <Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${it.obligacion.clienteId}`}>
                        {nombre(it.obligacion.clienteId)}
                      </Link>
                      {' · '}{it.obligacion.tipoObligacion ?? 'Obligación'}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${ESTILO[situacionVisual(it)]}`}>
                      {etiquetaSituacion(it)}
                    </span>
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
