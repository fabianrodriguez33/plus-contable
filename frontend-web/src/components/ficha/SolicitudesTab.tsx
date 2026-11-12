import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Check, ClipboardList } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import {
  listarSolicitudesPendientes, marcarSolicitudAtendida, registrarSolicitud, type SolicitudEspecial, type TipoSolicitud,
} from '../../services/solicitudService'
import { aIso } from '../../utils/calendario'
import { fechaCorta } from '../../utils/programacion'
import Button from '../Button'
import { Input, Select } from '../Input'
import Table, { Td } from '../Table'

export const TIPOS_SOLICITUD: Record<TipoSolicitud, string> = {
  CONSTANCIA: 'Constancia',
  CERTIFICACION: 'Certificación',
  TRAMITE: 'Trámite',
}
const MAX_DESCRIPCION = 500

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

/**
 * HU-26 · Tarea 185: registrar el pedido especial del cliente con su fecha de entrega (sin fecha no se guarda) y seguir los
 * pendientes, el de entrega más próxima primero; al entregarlo se marca atendido y sale de la lista.
 */
export default function SolicitudesTab({ clienteId, setError, setOk }: Props) {
  const [pendientes, setPendientes] = useState<SolicitudEspecial[]>([])
  const [tipo, setTipo] = useState<TipoSolicitud>('CONSTANCIA')
  const [fechaEntrega, setFechaEntrega] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [guardando, setGuardando] = useState(false)

  const cargar = useCallback(() => {
    listarSolicitudesPendientes(clienteId).then(setPendientes).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const registrar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    if (!fechaEntrega) return setError('Indique la fecha de entrega: sin ella el pedido no se guarda')
    setGuardando(true)
    try {
      const nota = descripcion.trim()
      await registrarSolicitud(clienteId, { tipo, fechaEntrega, ...(nota && { descripcion: nota }) })
      setOk(`${TIPOS_SOLICITUD[tipo]} registrada: queda en seguimiento hasta el ${fechaCorta(fechaEntrega)}`)
      setFechaEntrega('')
      setDescripcion('')
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  const atender = async (s: SolicitudEspecial) => {
    setError('')
    setOk('')
    try {
      await marcarSolicitudAtendida(clienteId, s.id)
      setOk(`${TIPOS_SOLICITUD[s.tipo]} marcada como atendida`)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <div className="space-y-5">
      <form onSubmit={registrar} noValidate className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Select label="Tipo de pedido" value={tipo} onChange={(e) => setTipo(e.target.value as TipoSolicitud)}>
            {Object.entries(TIPOS_SOLICITUD).map(([id, texto]) => <option key={id} value={id}>{texto}</option>)}
          </Select>
          <Input label="Fecha de entrega" type="date" required min={aIso(new Date())} value={fechaEntrega}
            onChange={(e) => setFechaEntrega(e.target.value)} />
        </div>
        <div>
          <label htmlFor="solicitud-descripcion" className="mb-1 block text-sm font-medium text-slate-700">Descripción (opcional)</label>
          <textarea id="solicitud-descripcion" rows={2} maxLength={MAX_DESCRIPCION} value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-100" />
          <p className="mt-1 text-xs text-slate-500">{descripcion.length}/{MAX_DESCRIPCION}</p>
        </div>
        <Button type="submit" disabled={guardando}>
          <ClipboardList size={16} /> {guardando ? 'Registrando…' : 'Registrar pedido'}
        </Button>
      </form>

      <section aria-label="Pedidos en seguimiento">
        <h3 className="mb-2 font-medium">Pendientes en seguimiento</h3>
        <Table columnas={['Fecha de entrega', 'Tipo', 'Descripción', '']} hayFilas={pendientes.length > 0}
          vacio="El cliente no tiene pedidos pendientes.">
          {pendientes.map((s) => (
            <tr key={s.id}>
              <Td>{fechaCorta(s.fechaEntrega)}</Td>
              <Td>{TIPOS_SOLICITUD[s.tipo]}</Td>
              <Td>{s.descripcion || '—'}</Td>
              <Td>
                <Button variant="secondary" onClick={() => atender(s)}><Check size={16} /> Marcar atendida</Button>
              </Td>
            </tr>
          ))}
        </Table>
      </section>
    </div>
  )
}
