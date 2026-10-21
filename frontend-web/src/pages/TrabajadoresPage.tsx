import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { listarTrabajadores, Trabajador } from '../services/expedienteService'

/** HU-03 · Tarea 33: formulario de trabajador y lista con sistema pensionario. */
export default function TrabajadoresPage() {
  const clienteId = Number(useParams().clienteId)
  const [trabajadores, setTrabajadores] = useState<Trabajador[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    listarTrabajadores(clienteId).then(setTrabajadores).catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold">Expediente laboral</h1>
      {error && <p role="alert" className="text-red-600">{error}</p>}
      {/* TODO Tarea 33: formulario (nacional / extranjero con documento y contrato), mostrar campos faltantes */}
      <table className="w-full text-left">
        <thead>
          <tr><th>Nombre</th><th>Documento</th><th>Tipo</th><th>Sistema pensionario</th></tr>
        </thead>
        <tbody>
          {trabajadores.map((t) => (
            <tr key={t.id}>
              <td>{t.nombreCompleto}</td>
              <td>{t.tipoDocumento} {t.numeroDocumento}</td>
              <td>{t.extranjero ? 'Extranjero' : 'Nacional'}</td>
              <td>{t.sistemaPensionario}{t.nombreAfp ? ` · ${t.nombreAfp}` : ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
