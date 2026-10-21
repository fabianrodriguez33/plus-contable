import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { Acuerdo, obtenerAcuerdo } from '../services/expedienteService'

/** HU-05 · Tarea 45: servicios contratados, honorario mensual y fecha de inicio. */
export default function AcuerdoPage() {
  const clienteId = Number(useParams().clienteId)
  const [acuerdo, setAcuerdo] = useState<Acuerdo | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    obtenerAcuerdo(clienteId).then(setAcuerdo).catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold">Acuerdo de servicio</h1>
      {error && <p role="alert" className="text-red-600">{error}</p>}
      {/* TODO Tarea 45: formulario editable -> guardarAcuerdo; mostrar historial de honorarios */}
      {acuerdo && (
        <p>S/ {acuerdo.honorarioMensual} mensual desde {acuerdo.fechaInicio}</p>
      )}
    </section>
  )
}
