import { FormEvent, useState } from 'react'
import { Inbox } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { registrarNotificacion, TipoNotificacion } from '../../services/notificacionService'
import { aIso } from '../../utils/calendario'
import Button from '../Button'
import { Input, Select } from '../Input'

/** HU-18, criterio 2: el tipo se elige entre estos tres. */
const TIPOS: { id: TipoNotificacion; etiqueta: string }[] = [
  { id: 'ESQUELA', etiqueta: 'Esquela' },
  { id: 'CARTA_INDUCTIVA', etiqueta: 'Carta inductiva' },
  { id: 'COMUNICADO', etiqueta: 'Comunicado' },
]

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

/** HU-18 · Tareas 65 y 66: registrar una notificación del Buzón SOL (tipo y plazo obligatorios). */
export default function NotificacionesTab({ clienteId, setError, setOk }: Props) {
  const [tipo, setTipo] = useState<'' | TipoNotificacion>('')
  const [fechaRecepcion, setFechaRecepcion] = useState(() => aIso(new Date()))
  const [plazoRespuesta, setPlazoRespuesta] = useState('')
  const [errores, setErrores] = useState<Errores>({})
  const [guardando, setGuardando] = useState(false)

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
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
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
  )
}
