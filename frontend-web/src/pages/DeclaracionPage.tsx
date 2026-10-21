import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { buscarObligacion, Obligacion } from '../services/obligacionService'

/** HU-09 · Tarea 62: marcar como declarada con fecha, número de formulario y monto determinado. */
export default function DeclaracionPage() {
  const obligacionId = Number(useParams().obligacionId)
  const [obligacion, setObligacion] = useState<Obligacion | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    buscarObligacion(obligacionId).then(setObligacion).catch((e) => setError(mensajeError(e)))
  }, [obligacionId])

  return (
    <section className="p-4 space-y-4">
      <h1 className="text-xl font-semibold">Registrar declaración</h1>
      {error && <p role="alert" className="text-red-600">{error}</p>}
      {obligacion && <p>Periodo {obligacion.periodo} · {obligacion.estado}</p>}
      {/* TODO Tarea 62: formulario fecha + numero de formulario (obligatorio) + monto -> declarar */}
    </section>
  )
}
