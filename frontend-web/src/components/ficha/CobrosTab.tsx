import axios from 'axios'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { CalendarPlus, FileSignature } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { CobroHonorario, listarCobrosPendientes, Periodicidad, programarCobros } from '../../services/expedienteService'
import { fechaCorta } from '../../utils/programacion'
import { soles } from '../../utils/societario'
import Button from '../Button'
import { Select } from '../Input'
import Table, { Td } from '../Table'

const SIN_ACUERDO = 'Registre primero el acuerdo de honorarios del cliente'

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
  /** Lleva a la pestaña del acuerdo cuando el cliente aún no tiene uno (criterio 3). */
  onIrAlAcuerdo: () => void
}

/** HU-28 · Tareas 129 a 131: elegir mensual o quincenal, programar los cobros y verlos pendientes en la ficha. */
export default function CobrosTab({ clienteId, setError, setOk, onIrAlAcuerdo }: Props) {
  const [cobros, setCobros] = useState<CobroHonorario[]>([])
  const [periodicidad, setPeriodicidad] = useState<Periodicidad>('MENSUAL')
  const [sinAcuerdo, setSinAcuerdo] = useState(false)
  const [programando, setProgramando] = useState(false)

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

  const total = cobros.reduce((acc, c) => acc + Number(c.monto), 0)

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

      <Table columnas={['Fecha de cobro', 'Monto', 'Estado']} hayFilas={cobros.length > 0}
        vacio="El cliente no tiene cobros pendientes.">
        {cobros.map((c) => (
          <tr key={c.id}>
            <Td>{fechaCorta(c.fechaCobro)}</Td>
            <Td>{soles(c.monto)}</Td>
            <Td>Pendiente</Td>
          </tr>
        ))}
      </Table>
      {cobros.length > 0 && (
        <p className="text-sm text-slate-600">{cobros.length} cobros pendientes · total {soles(total)}</p>
      )}
    </div>
  )
}
