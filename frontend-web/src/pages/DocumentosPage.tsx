import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { DocumentoLegal, listarDocumentos } from '../services/expedienteService'

/** HU-04 · Tarea 39: adjuntar minuta, vigencia de poderes o ficha RUC con su fecha de emisión. */
export default function DocumentosPage() {
  const clienteId = Number(useParams().clienteId)
  const [documentos, setDocumentos] = useState<DocumentoLegal[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    listarDocumentos(clienteId).then(setDocumentos).catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold">Expediente digital</h1>
      {error && <p role="alert" className="text-red-600">{error}</p>}
      {/* TODO Tarea 39: formulario tipo + fecha de emision + input file accept="application/pdf" -> archivarDocumento */}
      {/* TODO Tarea 39: boton de descarga con urlDescarga (via tributarioApi, responseType blob, para enviar el JWT) */}
      <ul>
        {documentos.map((d) => (
          <li key={d.id}>{d.tipo} v{d.version} · emitido {d.fechaEmision} · {d.nombreArchivo}</li>
        ))}
      </ul>
    </section>
  )
}
