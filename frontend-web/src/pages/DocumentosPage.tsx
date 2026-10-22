import { FormEvent, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import {
  archivarDocumento,
  descargarDocumento,
  DocumentoLegal,
  listarDocumentos,
  TipoDocumentoLegal,
} from '../services/expedienteService'

const TIPOS: Record<TipoDocumentoLegal, string> = {
  MINUTA: 'Minuta de constitución',
  VIGENCIA_PODERES: 'Vigencia de poderes',
  FICHA_RUC: 'Ficha RUC',
}
const MAX_BYTES = 10 * 1024 * 1024 // igual que ExpedienteService.TAMANO_MAXIMO
const input = 'w-full border rounded px-2 py-1'

const kb = (bytes: number) => `${Math.ceil(bytes / 1024)} KB`

/** HU-04 · Tarea 39: adjuntar minuta, vigencia de poderes o ficha RUC con su fecha de emisión. */
export default function DocumentosPage() {
  const clienteId = Number(useParams().clienteId)
  const [documentos, setDocumentos] = useState<DocumentoLegal[]>([])
  const [tipo, setTipo] = useState<TipoDocumentoLegal>('MINUTA')
  const [fechaEmision, setFechaEmision] = useState('')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const archivoRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    listarDocumentos(clienteId).then(setDocumentos).catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  async function subir(e: FormEvent) {
    e.preventDefault()
    // aviso inmediato; el backend vuelve a validar todo
    const motivos = [
      !fechaEmision && 'la fecha de emisión es obligatoria',
      !archivo && 'seleccione un archivo',
      archivo && archivo.type !== 'application/pdf' && 'el archivo no es un PDF',
      archivo && archivo.size > MAX_BYTES && 'el archivo supera el máximo de 10 MB',
    ].filter(Boolean)
    if (motivos.length) {
      setError(`Documento rechazado: ${motivos.join('; ')}`)
      return
    }
    setGuardando(true)
    setError('')
    try {
      const nuevo = await archivarDocumento(clienteId, tipo, fechaEmision, archivo!)
      setDocumentos((ds) => [nuevo, ...ds])
      setFechaEmision('')
      setArchivo(null)
      if (archivoRef.current) archivoRef.current.value = ''
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  async function descargar(d: DocumentoLegal) {
    try {
      await descargarDocumento(clienteId, d)
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <section className="space-y-6">
      <h1 className="text-xl font-semibold">Expediente digital</h1>

      <form onSubmit={subir} noValidate className="grid gap-3 sm:grid-cols-3 border rounded p-4">
        <label className="block text-sm">
          Documento *
          <select className={input} value={tipo} onChange={(e) => setTipo(e.target.value as TipoDocumentoLegal)}>
            {Object.entries(TIPOS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </label>
        <label className="block text-sm">
          Fecha de emisión *
          <input type="date" className={input} value={fechaEmision}
            max={new Date().toISOString().slice(0, 10)} onChange={(e) => setFechaEmision(e.target.value)} />
        </label>
        <label className="block text-sm">
          Archivo PDF (máx. 10 MB) *
          <input ref={archivoRef} type="file" accept="application/pdf,.pdf" className={input}
            onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
        </label>
        {error && <p role="alert" className="sm:col-span-3 text-red-600">{error}</p>}
        <div className="sm:col-span-3">
          <button type="submit" disabled={guardando} className="bg-blue-700 text-white rounded px-4 py-2 disabled:opacity-50">
            {guardando ? 'Subiendo…' : 'Adjuntar documento'}
          </button>
        </div>
      </form>

      {(Object.keys(TIPOS) as TipoDocumentoLegal[]).map((t) => {
        const versiones = documentos.filter((d) => d.tipo === t).sort((a, b) => b.version - a.version)
        return (
          <div key={t}>
            <h2 className="font-medium">{TIPOS[t]}</h2>
            {versiones.length === 0 ? (
              <p className="text-sm text-gray-500">Sin documentos</p>
            ) : (
              <ul className="text-sm">
                {versiones.map((d, i) => (
                  <li key={d.id} className="flex flex-wrap items-center gap-2 py-1">
                    <span>v{d.version}{i === 0 && ' · vigente'}</span>
                    <span>emitido {d.fechaEmision}</span>
                    <span>{d.nombreArchivo} ({kb(d.tamanoBytes)})</span>
                    <button type="button" className="text-blue-700 underline" onClick={() => descargar(d)}>Descargar</button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )
      })}
    </section>
  )
}
