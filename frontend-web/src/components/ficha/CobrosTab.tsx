import axios from 'axios'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { CalendarPlus, FileSignature, Wallet } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import {
  CobroHonorario, listarCobrosPendientes, MedioPago, Periodicidad, programarCobros, registrarPagoHonorario,
} from '../../services/expedienteService'
import { fechaCorta } from '../../utils/programacion'
import { soles } from '../../utils/societario'
import Button from '../Button'
import { Input, Select } from '../Input'
import Modal from '../Modal'
import Table, { Td } from '../Table'

const SIN_ACUERDO = 'Registre primero el acuerdo de honorarios del cliente'

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
  /** Lleva a la pestaña del acuerdo cuando el cliente aún no tiene uno (criterio 3). */
  onIrAlAcuerdo: () => void
}

/**
 * HU-28 · Tareas 129 a 131: elegir mensual o quincenal, programar los cobros y verlos pendientes en la ficha.
 * HU-29 · Tarea 141: registrar el pago de un cobro pendiente; el de pago parcial sigue en la lista con su saldo.
 */
export default function CobrosTab({ clienteId, setError, setOk, onIrAlAcuerdo }: Props) {
  const [cobros, setCobros] = useState<CobroHonorario[]>([])
  const [periodicidad, setPeriodicidad] = useState<Periodicidad>('MENSUAL')
  const [sinAcuerdo, setSinAcuerdo] = useState(false)
  const [programando, setProgramando] = useState(false)
  const [pagando, setPagando] = useState<CobroHonorario | null>(null)

  const cargar = useCallback(() => {
    listarCobrosPendientes(clienteId).then(setCobros).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const programar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    setSinAcuerdo(false)
    setProgramando(true)
    try {
      const r = await programarCobros(clienteId, periodicidad)
      setCobros(r.cobros)
      const ya = r.cobrosYaExistentes > 0 ? ` (${r.cobrosYaExistentes} ya estaban programados)` : ''
      setOk(`Cobro ${r.periodicidad === 'MENSUAL' ? 'mensual' : 'quincenal'} programado: ${r.cobrosGenerados} cobros nuevos${ya}`)
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 400 && mensajeError(err) === SIN_ACUERDO) setSinAcuerdo(true)
      else setError(mensajeError(err))
    } finally {
      setProgramando(false)
    }
  }

  const total = cobros.reduce((acc, c) => acc + Number(c.saldo), 0)

  return (
    <div className="space-y-5">
      <form onSubmit={programar} className="flex flex-wrap items-end gap-4">
        <div className="w-56">
          <Select label="Periodicidad del cobro" value={periodicidad}
            onChange={(e) => setPeriodicidad(e.target.value as Periodicidad)}>
            <option value="MENSUAL">Mensual</option>
            <option value="QUINCENAL">Quincenal (días 15 y último)</option>
          </Select>
        </div>
        <Button type="submit" disabled={programando}>
          <CalendarPlus size={16} /> {programando ? 'Programando…' : 'Programar cobros'}
        </Button>
      </form>
      <p className="text-sm text-slate-500">
        Se programan los cobros de los próximos 12 meses con el honorario del acuerdo. Mensual: el día de inicio del acuerdo.
        Quincenal: la mitad del honorario los días 15 y último de cada mes.
      </p>

      {sinAcuerdo && (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          <span>{SIN_ACUERDO} antes de programar sus cobros.</span>
          <Button variant="secondary" onClick={onIrAlAcuerdo}><FileSignature size={16} /> Ir al acuerdo de servicio</Button>
        </div>
      )}

      <Table columnas={['Fecha de cobro', 'Monto', 'Pagado', 'Saldo', 'Estado', '']} hayFilas={cobros.length > 0}
        vacio="El cliente no tiene cobros pendientes.">
        {cobros.map((c) => (
          <tr key={c.id}>
            <Td>{fechaCorta(c.fechaCobro)}</Td>
            <Td>{soles(c.monto)}</Td>
            <Td>{soles(c.pagado)}</Td>
            <Td>{soles(c.saldo)}</Td>
            <Td>{c.estado === 'PAGO_PARCIAL' ? 'Pago parcial' : 'Pendiente'}</Td>
            <Td>
              <Button variant="secondary" onClick={() => { setError(''); setOk(''); setPagando(c) }}>
                <Wallet size={16} /> Registrar pago
              </Button>
            </Td>
          </tr>
        ))}
      </Table>
      {cobros.length > 0 && (
        <p className="text-sm text-slate-600">{cobros.length} cobros pendientes · saldo total {soles(total)}</p>
      )}

      {pagando && (
        <PagoModal clienteId={clienteId} cobro={pagando} onCerrar={() => setPagando(null)}
          onPagado={(c) => {
            setPagando(null)
            setOk(c.estado === 'PAGADO'
              ? `Pago registrado: el cobro del ${fechaCorta(c.fechaCobro)} quedó pagado`
              : `Pago parcial registrado: queda un saldo de ${soles(c.saldo)}`)
            cargar()
          }} />
      )}
    </div>
  )
}

const hoyLocal = () => new Date().toLocaleDateString('en-CA') // AAAA-MM-DD en la hora local, no en UTC

interface PagoProps {
  clienteId: number
  cobro: CobroHonorario
  onCerrar: () => void
  onPagado: (c: CobroHonorario) => void
}

/** HU-29 · Tarea 141: registrar el pago (fecha, monto y medio) de un cobro pendiente; por defecto, el saldo completo. */
function PagoModal({ clienteId, cobro, onCerrar, onPagado }: PagoProps) {
  const [fechaPago, setFechaPago] = useState(hoyLocal())
  const [monto, setMonto] = useState(String(cobro.saldo))
  const [medioPago, setMedioPago] = useState<MedioPago>('TRANSFERENCIA')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setGuardando(true)
    try {
      onPagado(await registrarPagoHonorario(clienteId, cobro.id, { fechaPago, monto: Number(monto), medioPago }))
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal abierto titulo={`Registrar pago del cobro del ${fechaCorta(cobro.fechaCobro)}`} onCerrar={onCerrar}>
      <form onSubmit={guardar} className="space-y-4">
        <p className="text-sm text-slate-600">Monto del cobro {soles(cobro.monto)} · saldo pendiente {soles(cobro.saldo)}</p>
        <div className="grid gap-4 sm:grid-cols-3">
          <Input label="Fecha del pago" type="date" required max={hoyLocal()} value={fechaPago}
            onChange={(e) => setFechaPago(e.target.value)} />
          <Input label="Monto pagado (S/)" type="number" required min="0.01" step="0.01" max={cobro.saldo} value={monto}
            onChange={(e) => setMonto(e.target.value)} />
          <Select label="Medio de pago" value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
            {Object.entries(MEDIOS).map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
          </Select>
        </div>
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCerrar}>Cancelar</Button>
          <Button type="submit" disabled={guardando}>{guardando ? 'Guardando…' : 'Registrar pago'}</Button>
        </div>
      </form>
    </Modal>
  )
}

const MEDIOS: Record<MedioPago, string> = {
  EFECTIVO: 'Efectivo',
  TRANSFERENCIA: 'Transferencia',
  DEPOSITO: 'Depósito',
  YAPE_PLIN: 'Yape / Plin',
  OTRO: 'Otro',
}
