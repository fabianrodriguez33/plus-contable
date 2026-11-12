import { useEffect, useState } from 'react'
import { mensajeError } from '../../services/apiClient'
import { listarHistorial, type HistorialItem, type TipoIncidencia } from '../../services/incidenciaService'
import type { TipoSolicitud } from '../../services/solicitudService'
import { fechaCorta } from '../../utils/programacion'
import Table, { Td } from '../Table'
import { etiqueta as etiquetaIncidencia } from './IncidenciasTab'
import { TIPOS_SOLICITUD } from './SolicitudesTab'

interface Props {
  clienteId: number
  setError: (m: string) => void
}

const tipoDe = (h: HistorialItem) =>
  h.origen === 'INCIDENCIA' ? etiquetaIncidencia(h.tipo as TipoIncidencia) : TIPOS_SOLICITUD[h.tipo as TipoSolicitud] ?? h.tipo

function detalleDe(h: HistorialItem): string {
  if (h.origen === 'INCIDENCIA') return h.general ? 'General (toda la cartera)' : 'De este cliente'
  const estado = h.estado === 'ATENDIDA' ? 'Atendida' : 'Pendiente'
  return h.fechaEntrega ? `${estado} · entrega ${fechaCorta(h.fechaEntrega)}` : estado
}

/**
 * HU-27 · Tareas 190 a 192: el historial del cliente con sus incidencias y solicitudes, de lo más reciente a lo más antiguo
 * (criterio 1); cada registro con su tipo, fecha y descripción (criterio 2); si no hay registros, lo dice (criterio 3).
 * Solo el estudio lo ve (criterio 4): lo exige el servidor.
 */
export default function HistorialTab({ clienteId, setError }: Props) {
  const [historial, setHistorial] = useState<HistorialItem[] | null>(null)

  useEffect(() => {
    setHistorial(null)
    listarHistorial(clienteId).then(setHistorial).catch((e) => { setError(mensajeError(e)); setHistorial([]) })
  }, [clienteId, setError])

  if (historial === null) return <p className="text-sm text-slate-500">Cargando...</p>

  return (
    <Table columnas={['Fecha', 'Registro', 'Tipo', 'Descripción', 'Detalle']} hayFilas={historial.length > 0}
      vacio="Este cliente no tiene incidencias ni solicitudes registradas.">
      {historial.map((h) => (
        <tr key={`${h.origen}-${h.id}`}>
          <Td>{fechaCorta(h.fecha)}</Td>
          <Td>{h.origen === 'INCIDENCIA' ? 'Incidencia' : 'Solicitud'}</Td>
          <Td>{tipoDe(h)}</Td>
          <Td>{h.descripcion || '—'}</Td>
          <Td>{detalleDe(h)}</Td>
        </tr>
      ))}
    </Table>
  )
}
