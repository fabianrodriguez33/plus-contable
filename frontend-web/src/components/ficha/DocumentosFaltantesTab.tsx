import { FormEvent, useCallback, useEffect, useState } from 'react'
import { FileWarning, PackageCheck, TriangleAlert } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import {
  listarDocumentosFaltantes, registrarDocumentoComoIncidencia, registrarDocumentoFaltante, registrarEntregaDocumento,
  type DocumentoFaltante, type EstadoDocumentoFaltante,
} from '../../services/documentoFaltanteService'
import { aIso } from '../../utils/calendario'
import { fechaCorta } from '../../utils/programacion'
import Button from '../Button'
import { Input } from '../Input'
import Table, { Td } from '../Table'

const ESTADOS: Record<EstadoDocumentoFaltante, { texto: string; estilo: string }> = {
  PENDIENTE: { texto: 'Pendiente', estilo: 'bg-amber-50 text-amber-700' },
  SIN_RESPUESTA: { texto: 'Sin respuesta', estilo: 'bg-red-50 text-red-700' },
  ENTREGADO: { texto: 'Entregado', estilo: 'bg-emerald-50 text-emerald-700' },
}
const MAX_DOCUMENTO = 150

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

/**
 * HU-24 · Tarea 170: qué documentos faltan del cliente. Se registra el documento con su fecha límite y la fecha en que se pidió
 * (hoy si no se indica); al recibirlo se registra la entrega; si vence sin entrega queda «Sin respuesta» y se puede registrar como
 * incidencia (HU-23) una sola vez.
 */
export default function DocumentosFaltantesTab({ clienteId, setError, setOk }: Props) {
  const [documentos, setDocumentos] = useState<DocumentoFaltante[]>([])
  const [documento, setDocumento] = useState('')
  const [fechaSolicitud, setFechaSolicitud] = useState(() => aIso(new Date()))
  const [fechaLimite, setFechaLimite] = useState('')
  const [guardando, setGuardando] = useState(false)

  const cargar = useCallback(() => {
    listarDocumentosFaltantes(clienteId).then(setDocumentos).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const registrar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    if (!documento.trim()) return setError('Indique qué documento falta')
    if (!fechaLimite) return setError('Indique la fecha límite de entrega')
    setGuardando(true)
    try {
      await registrarDocumentoFaltante(clienteId, { documento: documento.trim(), fechaLimite, ...(fechaSolicitud && { fechaSolicitud }) })
      setOk(`Documento registrado como pendiente hasta el ${fechaCorta(fechaLimite)}`)
      setDocumento('')
      setFechaLimite('')
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  const accion = async (hacer: () => Promise<unknown>, mensaje: string) => {
    setError('')
    setOk('')
    try {
      await hacer()
      setOk(mensaje)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const faltan = documentos.filter((d) => d.estado !== 'ENTREGADO').length

  return (
    <div className="space-y-5">
      <form onSubmit={registrar} noValidate className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Input label="Documento que falta" maxLength={MAX_DOCUMENTO} value={documento} onChange={(e) => setDocumento(e.target.value)} />
          <Input label="Solicitado el" type="date" max={aIso(new Date())} value={fechaSolicitud} onChange={(e) => setFechaSolicitud(e.target.value)} />
          <Input label="Fecha límite" type="date" min={fechaSolicitud || undefined} value={fechaLimite} onChange={(e) => setFechaLimite(e.target.value)} />
        </div>
        <Button type="submit" disabled={guardando}>
          <FileWarning size={16} /> {guardando ? 'Registrando…' : 'Registrar documento faltante'}
        </Button>
      </form>

      <section aria-label="Documentos del cliente">
        <h3 className="mb-2 font-medium">{faltan === 0 ? 'No falta ningún documento' : faltan === 1 ? 'Falta 1 documento' : `Faltan ${faltan} documentos`}</h3>
        <Table columnas={['Documento', 'Solicitado', 'Fecha límite', 'Estado', '']} hayFilas={documentos.length > 0}
          vacio="El cliente no tiene documentos pendientes de entregar.">
          {documentos.map((d) => (
            <tr key={d.id}>
              <Td>{d.documento}</Td>
              <Td>{fechaCorta(d.fechaSolicitud)}</Td>
              <Td>{fechaCorta(d.fechaLimite)}</Td>
              <Td>
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${ESTADOS[d.estado].estilo}`}>{ESTADOS[d.estado].texto}</span>
                {d.fechaEntrega && <span className="ml-2 text-xs text-slate-500">el {fechaCorta(d.fechaEntrega)}</span>}
              </Td>
              <Td>
                <div className="flex flex-wrap gap-2">
                  {d.estado !== 'ENTREGADO' && (
                    <Button variant="secondary" onClick={() => accion(() => registrarEntregaDocumento(clienteId, d.id), `Entrega de «${d.documento}» registrada`)}>
                      <PackageCheck size={16} /> Registrar entrega
                    </Button>
                  )}
                  {d.estado === 'SIN_RESPUESTA' && d.incidenciaId === null && (
                    <Button variant="secondary" onClick={() => accion(() => registrarDocumentoComoIncidencia(clienteId, d.id),
                      `«${d.documento}» quedó registrado como incidencia del cliente`)}>
                      <TriangleAlert size={16} /> Registrar como incidencia
                    </Button>
                  )}
                  {d.incidenciaId !== null && <span className="text-xs text-slate-500">Incidencia registrada</span>}
                </div>
              </Td>
            </tr>
          ))}
        </Table>
      </section>
    </div>
  )
}
