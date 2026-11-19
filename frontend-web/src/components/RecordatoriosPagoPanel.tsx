import { FormEvent, useCallback, useEffect, useState } from 'react'
import { mensajeError } from '../services/apiClient'
import { cancelarRecordatorio, listarRecordatorios, programarRecordatorio, type RecordatorioPago } from '../services/recordatorioPagoService'
import { ETIQUETA_ESTADO, hoyEnLima, validarFecha } from '../utils/recordatoriosPago'
import { fechaCorta } from '../utils/programacion'
import { soles } from '../utils/societario'
import Button from './Button'
import Card from './Card'
import { Input } from './Input'

interface Props {
  obligacionId: number
  /** Fecha límite de la obligación (AAAA-MM-DD): el recordatorio no puede ser posterior. */
  fechaVencimiento: string
}

/**
 * HU-15 · T35: el contador programa para qué día el cliente recibe el recordatorio de pago de esta obligación (declarada o con pago
 * parcial). El día elegido el sistema se lo entrega como aviso de pago, con el saldo de ese día, solo si la obligación sigue con saldo.
 * Solo el estudio la ve (el servidor responde 403 al cliente).
 */
export default function RecordatoriosPagoPanel({ obligacionId, fechaVencimiento }: Props) {
  const [lista, setLista] = useState<RecordatorioPago[] | null>(null)
  const [fecha, setFecha] = useState('')
  const [errorFecha, setErrorFecha] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [guardando, setGuardando] = useState(false)
  const hoy = hoyEnLima()

  const cargar = useCallback(() => {
    listarRecordatorios(obligacionId).then(setLista).catch((e) => { setError(mensajeError(e)); setLista([]) })
  }, [obligacionId])

  useEffect(cargar, [cargar])

  const programar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    const problema = validarFecha(fecha, hoy, fechaVencimiento)
    setErrorFecha(problema)
    if (problema) return
    setGuardando(true)
    try {
      await programarRecordatorio(obligacionId, fecha)
      setOk(`Recordatorio programado para el ${fechaCorta(fecha)}: ese día el cliente recibirá el aviso de pago.`)
      setFecha('')
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  const cancelar = async (r: RecordatorioPago) => {
    setError('')
    setOk('')
    try {
      await cancelarRecordatorio(obligacionId, r.id)
      setOk(`Recordatorio del ${fechaCorta(r.fechaRecordatorio)} cancelado.`)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <Card title="Recordatorios de pago">
      <p className="mb-3 text-sm text-slate-600">
        Elija el día en que el cliente recibirá un aviso de pago con el saldo pendiente. Si para ese día la obligación ya se pagó o venció, no se envía.
      </p>
      {error && <p role="alert" className="mb-3 text-sm text-red-600">{error}</p>}
      {ok && <p role="status" className="mb-3 text-sm text-emerald-700">{ok}</p>}
      <form onSubmit={programar} noValidate className="flex flex-wrap items-end gap-3">
        <div className="w-full sm:w-56">
          <Input label="Fecha del recordatorio" type="date" min={hoy} max={fechaVencimiento} value={fecha}
            onChange={(e) => setFecha(e.target.value)} error={errorFecha} />
        </div>
        <Button type="submit" disabled={guardando}>{guardando ? 'Guardando...' : 'Programar recordatorio'}</Button>
      </form>

      {lista === null ? (
        <p className="mt-4 text-sm text-slate-500">Cargando...</p>
      ) : lista.length === 0 ? (
        <p className="mt-4 text-sm text-slate-500">Todavía no hay recordatorios programados.</p>
      ) : (
        <ul className="mt-4 divide-y divide-slate-100 text-sm" aria-label="Recordatorios programados">
          {lista.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span>
                <strong>{fechaCorta(r.fechaRecordatorio)}</strong> · {ETIQUETA_ESTADO[r.estado]}
                {r.estado === 'PROGRAMADO' && r.saldo != null && <span className="text-slate-600"> · saldo hoy {soles(r.saldo)}</span>}
                <span className="block text-xs text-slate-500">Programado por {r.programadoPor}</span>
              </span>
              {r.estado === 'PROGRAMADO' && <Button variant="secondary" onClick={() => cancelar(r)}>Cancelar</Button>}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
