import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Info, TriangleAlert } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { Incidencia, listarIncidencias, registrarIncidencia, TipoIncidencia } from '../../services/incidenciaService'
import { aIso } from '../../utils/calendario'
import { fechaCorta } from '../../utils/programacion'
import Button from '../Button'
import { Input, Select } from '../Input'
import Table, { Td } from '../Table'

const TIPOS: { id: TipoIncidencia; etiqueta: string }[] = [
  { id: 'CLIENTE_NO_ENVIA_DOCUMENTOS', etiqueta: 'Cliente no envía documentos' },
  { id: 'RECHAZO_FRACCIONAMIENTO', etiqueta: 'Rechazo de fraccionamiento' },
  { id: 'CAIDA_PORTAL_SOL', etiqueta: 'Caída o indisponibilidad del portal SOL' },
  { id: 'PROBLEMA_SIRE', etiqueta: 'Problemas con SIRE' },
  { id: 'PROBLEMA_SERVICIOS_EXTERNOS', etiqueta: 'Problemas con SUNAT u otros servicios externos' },
  { id: 'FALLA_SISTEMA_INTERNO', etiqueta: 'Fallas del sistema interno' },
  { id: 'PROBLEMA_CONEXION_INTERNET', etiqueta: 'Problemas de conexión a internet' },
  { id: 'ERROR_PROCESO_CONTABLE', etiqueta: 'Errores en procesos contables o en la presentación de declaraciones' },
  { id: 'OTRO', etiqueta: 'Otro' },
]
const etiqueta = (t: TipoIncidencia) => TIPOS.find((x) => x.id === t)?.etiqueta ?? t
const MAX_DESCRIPCION = 1000
const MAX_OBSERVACIONES = 1000

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

/** HU-23 · Tarea 162: registrar una incidencia del servicio y ver el historial del cliente (propias y generales). */
export default function IncidenciasTab({ clienteId, setError, setOk }: Props) {
  const [incidencias, setIncidencias] = useState<Incidencia[]>([])
  const [tipo, setTipo] = useState<TipoIncidencia>('CLIENTE_NO_ENVIA_DOCUMENTOS')
  const [fecha, setFecha] = useState(() => aIso(new Date()))
  const [descripcion, setDescripcion] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [guardando, setGuardando] = useState(false)

  const cargar = useCallback(() => {
    listarIncidencias(clienteId).then(setIncidencias).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const esGeneral = tipo === 'CAIDA_PORTAL_SOL'

  const registrar = async (e: FormEvent) => {
    e.preventDefault()
    // La general se asocia a toda la cartera y aún no se puede borrar: se pide confirmar.
    if (esGeneral && !window.confirm('Esta incidencia se asociará a TODOS los clientes activos y no se puede borrar. ¿Registrarla?')) return
    setError('')
    setOk('')
    setGuardando(true)
    try {
      const notas = observaciones.trim()
      const r = await registrarIncidencia({
        tipo, clienteId: esGeneral ? undefined : clienteId, fecha, descripcion, ...(notas && { observaciones: notas }),
      })
      setOk(esGeneral
        ? `Incidencia general registrada: quedó asociada a ${r.clientesAsociados} clientes activos`
        : 'Incidencia registrada en el historial del cliente')
      setDescripcion('')
      setObservaciones('')
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="space-y-5">
      <form onSubmit={registrar} className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Select label="Tipo de incidencia" value={tipo} onChange={(e) => setTipo(e.target.value as TipoIncidencia)}>
            {TIPOS.map((t) => <option key={t.id} value={t.id}>{t.etiqueta}</option>)}
          </Select>
          <Input label="Fecha" type="date" value={fecha} max={aIso(new Date())} required
            onChange={(e) => setFecha(e.target.value)} />
        </div>
        <div>
          <label htmlFor="incidencia-descripcion" className="mb-1 block text-sm font-medium text-slate-700">Descripción</label>
          <textarea id="incidencia-descripcion" rows={3} required maxLength={MAX_DESCRIPCION} value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-100" />
          <p className="mt-1 text-xs text-slate-500">{descripcion.length}/{MAX_DESCRIPCION}</p>
        </div>
        <div>
          <label htmlFor="incidencia-observaciones" className="mb-1 block text-sm font-medium text-slate-700">Observaciones (opcional)</label>
          <textarea id="incidencia-observaciones" rows={2} maxLength={MAX_OBSERVACIONES} value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-100" />
          <p className="mt-1 text-xs text-slate-500">{observaciones.length}/{MAX_OBSERVACIONES}</p>
        </div>
        {esGeneral && (
          <p className="flex items-center gap-2 text-sm text-slate-500">
            <Info size={16} /> Es una incidencia general: no es de este cliente, se asocia a todos los clientes activos.
          </p>
        )}
        <Button type="submit" disabled={guardando}>
          <TriangleAlert size={16} /> {guardando ? 'Registrando…' : 'Registrar incidencia'}
        </Button>
      </form>

      <Table columnas={['Fecha', 'Tipo', 'Descripción', 'Observaciones', 'Alcance', 'Registrada por']} hayFilas={incidencias.length > 0}
        vacio="Este cliente no tiene incidencias registradas.">
        {incidencias.map((i) => (
          <tr key={i.id}>
            <Td>{fechaCorta(i.fecha)}</Td>
            <Td>{etiqueta(i.tipo)}</Td>
            <Td>{i.descripcion}</Td>
            <Td>{i.observaciones || '—'}</Td>
            <Td>{i.tipo === 'CAIDA_PORTAL_SOL' ? `General · ${i.clientesAsociados} clientes` : 'Este cliente'}</Td>
            <Td>{i.registradaPor}</Td>
          </tr>
        ))}
      </Table>
    </div>
  )
}
