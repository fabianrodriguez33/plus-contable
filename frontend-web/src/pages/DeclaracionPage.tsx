import { FormEvent, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { buscarObligacion, declarar, Obligacion } from '../services/obligacionService'

const input = 'w-full border rounded px-2 py-1 aria-[invalid=true]:border-red-600'
const hoy = () => new Date().toISOString().slice(0, 10)
const mesPeriodo = (p: string) => p.slice(0, 7)

/** HU-09 · Tarea 62: marcar como declarada con fecha, número de formulario y monto determinado. */
export default function DeclaracionPage() {
  const obligacionId = Number(useParams().obligacionId)
  const [obligacion, setObligacion] = useState<Obligacion | null>(null)
  const [fecha, setFecha] = useState(hoy())
  const [formulario, setFormulario] = useState('')
  const [monto, setMonto] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    buscarObligacion(obligacionId).then(setObligacion).catch((e) => setError(mensajeError(e)))
  }, [obligacionId])

  const invalido = (campo: string) => error.includes(campo)
  const etiqueta = (campo: string) => (invalido(campo) ? 'text-red-600 font-medium' : '')

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setGuardando(true)
    setError('')
    try {
      setObligacion(await declarar(obligacionId, {
        fechaDeclaracion: (fecha || null) as string,
        numeroFormulario: formulario,
        montoDeterminado: (monto === '' ? null : Number(monto)) as number,
      }))
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="p-4 space-y-4 max-w-2xl">
      <h1 className="text-xl font-semibold">Registrar declaración</h1>

      {obligacion && (
        <p className="border rounded p-3 bg-white">
          Periodo <strong>{mesPeriodo(obligacion.periodo)}</strong> · vence {obligacion.fechaVencimiento} ·{' '}
          <span className={obligacion.estado === 'DECLARADO' ? 'text-green-700 font-medium' : 'text-amber-700 font-medium'}>
            {obligacion.estado}
          </span>
        </p>
      )}

      {obligacion?.estado === 'DECLARADO' ? (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 border rounded p-4">
          <dt>Fecha de declaración</dt><dd>{obligacion.fechaDeclaracion}</dd>
          <dt>N° de formulario</dt><dd>{obligacion.numeroFormulario}</dd>
          <dt>Monto determinado</dt><dd>S/ {Number(obligacion.montoDeterminado).toFixed(2)}</dd>
        </dl>
      ) : obligacion && (
        <form onSubmit={guardar} noValidate className="grid gap-3 sm:grid-cols-3 border rounded p-4">
          <label className="block text-sm">
            <span className={etiqueta('fechaDeclaracion')}>Fecha de declaración *</span>
            <input type="date" className={input} aria-invalid={invalido('fechaDeclaracion')}
              min={obligacion.periodo} max={hoy()} value={fecha} onChange={(e) => setFecha(e.target.value)} />
          </label>
          <label className="block text-sm">
            <span className={etiqueta('numeroFormulario')}>N° de formulario *</span>
            <input className={input} aria-invalid={invalido('numeroFormulario')} maxLength={30}
              value={formulario} onChange={(e) => setFormulario(e.target.value)} placeholder="0621" />
          </label>
          <label className="block text-sm">
            <span className={etiqueta('montoDeterminado')}>Monto determinado (S/) *</span>
            <input type="number" min="0" step="0.01" className={input} aria-invalid={invalido('montoDeterminado')}
              value={monto} onChange={(e) => setMonto(e.target.value)} />
          </label>
          {error && <p role="alert" className="sm:col-span-3 text-red-600">{error}</p>}
          <div className="sm:col-span-3">
            <button type="submit" disabled={guardando} className="bg-blue-700 text-white rounded px-4 py-2 disabled:opacity-50">
              {guardando ? 'Guardando…' : 'Marcar como declarada'}
            </button>
          </div>
        </form>
      )}

      {!obligacion && error && <p role="alert" className="text-red-600">{error}</p>}
    </section>
  )
}
