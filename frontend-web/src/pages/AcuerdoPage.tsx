import axios from 'axios'
import { FormEvent, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { Acuerdo, guardarAcuerdo, obtenerAcuerdo } from '../services/expedienteService'

const input = 'w-full border rounded px-2 py-1 aria-[invalid=true]:border-red-600'
const hoy = () => new Date().toISOString().slice(0, 10)
const soles = (n: number) => `S/ ${Number(n).toFixed(2)}`

/** HU-05 · Tarea 45: servicios contratados, honorario mensual y fecha de inicio. */
export default function AcuerdoPage() {
  const clienteId = Number(useParams().clienteId)
  const [acuerdo, setAcuerdo] = useState<Acuerdo | null>(null)
  const [servicios, setServicios] = useState('')
  const [honorario, setHonorario] = useState('')
  const [fechaInicio, setFechaInicio] = useState('')
  const [honorarioDesde, setHonorarioDesde] = useState(hoy())
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [guardando, setGuardando] = useState(false)

  function cargar(a: Acuerdo) {
    setAcuerdo(a)
    setServicios(a.serviciosContratados ?? '')
    setHonorario(String(a.honorarioMensual))
    setFechaInicio(a.fechaInicio)
    setHonorarioDesde(hoy())
  }

  useEffect(() => {
    obtenerAcuerdo(clienteId).then(cargar).catch((e) => {
      // 404 = el cliente aún no tiene acuerdo: se muestra el formulario vacío
      if (!(axios.isAxiosError(e) && e.response?.status === 404)) setError(mensajeError(e))
    })
  }, [clienteId])

  const cambiaHonorario = acuerdo !== null && honorario !== '' && Number(honorario) !== Number(acuerdo.honorarioMensual)
  const invalido = (campo: string) => error.includes(campo)

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setGuardando(true)
    setError('')
    setOk('')
    try {
      cargar(await guardarAcuerdo(clienteId, {
        serviciosContratados: servicios,
        honorarioMensual: (honorario === '' ? null : Number(honorario)) as number,
        fechaInicio: (fechaInicio || null) as string,
        honorarioDesde: cambiaHonorario ? honorarioDesde : undefined,
      }))
      setOk('Acuerdo guardado')
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="space-y-6">
      <h1 className="text-xl font-semibold">Acuerdo de servicio</h1>

      {acuerdo && (
        <p className="border rounded p-3 bg-white">
          Vigente: <strong>{soles(acuerdo.honorarioMensual)}</strong> mensuales desde {acuerdo.honorarioDesde}
          {' · '}inicio del servicio {acuerdo.fechaInicio}
        </p>
      )}

      <form onSubmit={guardar} noValidate className="grid gap-3 sm:grid-cols-2 border rounded p-4">
        <label className="block text-sm sm:col-span-2">
          Servicios contratados
          <textarea className={input} rows={3} value={servicios} onChange={(e) => setServicios(e.target.value)}
            placeholder="Contabilidad mensual, planillas, declaraciones…" />
        </label>
        <label className="block text-sm">
          <span className={invalido('honorarioMensual') ? 'text-red-600 font-medium' : ''}>Honorario mensual (S/) *</span>
          <input type="number" min="0.01" step="0.01" className={input} aria-invalid={invalido('honorarioMensual')}
            value={honorario} onChange={(e) => setHonorario(e.target.value)} />
        </label>
        <label className="block text-sm">
          <span className={invalido('fechaInicio') ? 'text-red-600 font-medium' : ''}>Fecha de inicio *</span>
          <input type="date" className={input} aria-invalid={invalido('fechaInicio')}
            value={fechaInicio} onChange={(e) => setFechaInicio(e.target.value)} />
        </label>
        {cambiaHonorario && (
          <label className="block text-sm sm:col-span-2">
            <span className={invalido('honorarioDesde') ? 'text-red-600 font-medium' : ''}>
              El nuevo honorario rige desde * (el actual pasa al historial)
            </span>
            <input type="date" className={input} aria-invalid={invalido('honorarioDesde')}
              value={honorarioDesde} onChange={(e) => setHonorarioDesde(e.target.value)} />
          </label>
        )}
        {error && <p role="alert" className="sm:col-span-2 text-red-600">{error}</p>}
        {ok && <p role="status" className="sm:col-span-2 text-green-700">{ok}</p>}
        <div className="sm:col-span-2">
          <button type="submit" disabled={guardando} className="bg-blue-700 text-white rounded px-4 py-2 disabled:opacity-50">
            {guardando ? 'Guardando…' : acuerdo ? 'Actualizar acuerdo' : 'Registrar acuerdo'}
          </button>
        </div>
      </form>

      {acuerdo?.historial && acuerdo.historial.length > 0 && (
        <div className="overflow-x-auto">
          <h2 className="font-medium">Historial de honorarios</h2>
          <table className="w-full text-left text-sm">
            <thead><tr><th>Monto</th><th>Vigente desde</th><th>Vigente hasta</th></tr></thead>
            <tbody>
              {acuerdo.historial.map((h) => (
                <tr key={h.vigenteDesde}><td>{soles(h.monto)}</td><td>{h.vigenteDesde}</td><td>{h.vigenteHasta}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
