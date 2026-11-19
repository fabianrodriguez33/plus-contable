import { FormEvent, useCallback, useEffect, useState } from 'react'
import { CheckCheck, Inbox } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import {
  listarNotificaciones, marcarAtendida, Notificacion, registrarNotificacion, TipoNotificacion,
} from '../../services/notificacionService'
import { aIso } from '../../utils/calendario'
import { fechaCorta } from '../../utils/programacion'
import Button from '../Button'
import { Input, Select } from '../Input'
import Table, { Td } from '../Table'

/** HU-18, criterio 2: el tipo se elige entre estos tres. */
const TIPOS: { id: TipoNotificacion; etiqueta: string }[] = [
  { id: 'ESQUELA', etiqueta: 'Esquela' },
  { id: 'CARTA_INDUCTIVA', etiqueta: 'Carta inductiva' },
  { id: 'COMUNICADO', etiqueta: 'Comunicado' },
]
const etiqueta = (t: TipoNotificacion) => TIPOS.find((x) => x.id === t)?.etiqueta ?? t

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

interface Errores {
  tipo?: string
  fechaRecepcion?: string
  plazoRespuesta?: string
}

/** Las mismas reglas que valida el servidor, para avisar antes de enviar. */
export function validarNotificacion(tipo: string, fechaRecepcion: string, plazoRespuesta: string, hoy: string): Errores {
  const errores: Errores = {}
  if (!tipo) errores.tipo = 'Elija el tipo de notificación'
  if (!fechaRecepcion) errores.fechaRecepcion = 'Indique la fecha de recepción'
  else if (fechaRecepcion > hoy) errores.fechaRecepcion = 'La fecha de recepción no puede ser futura'
  if (!plazoRespuesta) errores.plazoRespuesta = 'Indique el plazo de respuesta: sin plazo la notificación no se guarda'
  else if (fechaRecepcion && plazoRespuesta < fechaRecepcion) {
    errores.plazoRespuesta = 'El plazo de respuesta no puede ser anterior a la fecha de recepción'
  }
  return errores
}

/**
 * HU-18 · Tareas 65, 66 y 67: registrar una notificación del Buzón SOL (tipo y plazo obligatorios), ver las pendientes
 * del cliente (la de plazo más próximo primero) y marcarlas como atendidas.
 */
export default function NotificacionesTab({ clienteId, setError, setOk }: Props) {
  const [tipo, setTipo] = useState<'' | TipoNotificacion>('')
  const [fechaRecepcion, setFechaRecepcion] = useState(() => aIso(new Date()))
  const [plazoRespuesta, setPlazoRespuesta] = useState('')
  const [errores, setErrores] = useState<Errores>({})
  const [guardando, setGuardando] = useState(false)
  const [pendientes, setPendientes] = useState<Notificacion[]>([])
  const [atendiendo, setAtendiendo] = useState<number | null>(null)

  const cargar = useCallback(() => {
    listarNotificaciones(clienteId, 'PENDIENTE').then(setPendientes).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const atender = async (n: Notificacion) => {
    // No hay forma de volver una notificación atendida a pendiente: se pide confirmar.
    if (!window.confirm('¿Marcar esta notificación como atendida? No se puede deshacer.')) return
    setError('')
    setOk('')
    setAtendiendo(n.id)
    try {
      await marcarAtendida(n.id)
      setOk('Notificación marcada como atendida')
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setAtendiendo(null)
      cargar() // también tras un error (por ejemplo, ya estaba atendida) para mostrar lo que hay de verdad
    }
  }

  const registrar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    const encontrados = validarNotificacion(tipo, fechaRecepcion, plazoRespuesta, aIso(new Date()))
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return
    setGuardando(true)
    try {
      await registrarNotificacion(clienteId, { tipo: tipo as TipoNotificacion, fechaRecepcion, plazoRespuesta })
      setOk('Notificación registrada: queda pendiente de atención')
      setTipo('')
      setPlazoRespuesta('')
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  const hoy = aIso(new Date())

  return (
    <div className="space-y-5">
    <form onSubmit={registrar} noValidate className="space-y-4">
      <div className="grid gap-4 md:grid-cols-3">
        <div>
          <Select label="Tipo de notificación" value={tipo} onChange={(e) => setTipo(e.target.value as '' | TipoNotificacion)}
            aria-invalid={errores.tipo ? true : undefined}>
            <option value="">Seleccione el tipo</option>
            {TIPOS.map((t) => <option key={t.id} value={t.id}>{t.etiqueta}</option>)}
          </Select>
          {errores.tipo && <p role="alert" className="mt-1 text-xs text-red-600">{errores.tipo}</p>}
        </div>
        <Input label="Fecha de recepción" type="date" value={fechaRecepcion} max={aIso(new Date())}
          error={errores.fechaRecepcion} onChange={(e) => setFechaRecepcion(e.target.value)} />
        <Input label="Plazo de respuesta" type="date" value={plazoRespuesta} min={fechaRecepcion || undefined}
          error={errores.plazoRespuesta} onChange={(e) => setPlazoRespuesta(e.target.value)} />
      </div>
      <Button type="submit" disabled={guardando}>
        <Inbox size={16} /> {guardando ? 'Registrando…' : 'Registrar notificación'}
      </Button>
    </form>

    <Table columnas={['Tipo', 'Recepción', 'Plazo de respuesta', '']} hayFilas={pendientes.length > 0}
      vacio="Este cliente no tiene notificaciones pendientes.">
      {pendientes.map((n) => (
        <tr key={n.id}>
          <Td>{etiqueta(n.tipo)}</Td>
          <Td>{fechaCorta(n.fechaRecepcion)}</Td>
          <Td>
            {fechaCorta(n.plazoRespuesta)}
            {n.plazoRespuesta < hoy && <span className="ml-2 rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">Plazo vencido</span>}
          </Td>
          <Td>
            <Button type="button" variant="secondary" disabled={atendiendo === n.id} onClick={() => atender(n)}>
              <CheckCheck size={16} /> Marcar atendida
            </Button>
          </Td>
        </tr>
      ))}
    </Table>
    </div>
  )
}
