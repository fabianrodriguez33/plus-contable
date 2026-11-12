import { FormEvent, useEffect, useState } from 'react'
import { FileText } from 'lucide-react'
import { mensajeError } from '../services/apiClient'
import { listarClientes } from '../services/clienteService'
import { emitirEstadoCuenta, type EstadoCuenta } from '../services/expedienteService'
import type { Cliente } from '../types'
import { aIso } from '../utils/calendario'
import { fechaCorta } from '../utils/programacion'
import { soles } from '../utils/societario'
import { MEDIOS } from '../components/ficha/CobrosTab'
import Button from '../components/Button'
import Card from '../components/Card'
import { Input, Select } from '../components/Input'
import Table, { Td } from '../components/Table'

const ESTADO_COBRO = { PENDIENTE: 'Pendiente', PAGO_PARCIAL: 'Pago parcial', PAGADO: 'Pagado' } as const

/** El mes en curso, en hora local. */
function mesActual(): { desde: string; hasta: string } {
  const hoy = new Date()
  return { desde: aIso(new Date(hoy.getFullYear(), hoy.getMonth(), 1)), hasta: aIso(new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0)) }
}

/**
 * HU-30 · Tareas 145 a 147: se elige el cliente y el periodo (criterio 2); se ven los cobros, los pagos y el saldo del periodo
 * (criterio 1) o el aviso de que no hubo movimientos (criterio 3).
 */
export default function EstadoCuentaPage() {
  const [clientes, setClientes] = useState<Cliente[]>([])
  const [clienteId, setClienteId] = useState('')
  const [{ desde, hasta }, setPeriodo] = useState(mesActual)
  const [estado, setEstado] = useState<EstadoCuenta | null>(null)
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  useEffect(() => {
    listarClientes().then(setClientes).catch((e) => setError(mensajeError(e)))
  }, [])

  const emitir = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setEstado(null)
    if (!clienteId) return setError('Elija un cliente')
    if (!desde || !hasta) return setError('Indique el periodo')
    if (desde > hasta) return setError('La fecha «Desde» no puede ser posterior a «Hasta»')
    setCargando(true)
    try {
      setEstado(await emitirEstadoCuenta(Number(clienteId), desde, hasta))
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setCargando(false)
    }
  }

  return (
    <Card title="Estado de cuenta de honorarios">
      <form onSubmit={emitir} noValidate className="flex flex-wrap items-end gap-4">
        <div className="w-72">
          <Select label="Cliente" value={clienteId} onChange={(e) => setClienteId(e.target.value)}>
            <option value="">Elija un cliente</option>
            {clientes.map((c) => <option key={c.id} value={c.id}>{c.razonSocial} · {c.ruc}</option>)}
          </Select>
        </div>
        <Input label="Desde" type="date" value={desde} onChange={(e) => setPeriodo({ desde: e.target.value, hasta })} />
        <Input label="Hasta" type="date" value={hasta} onChange={(e) => setPeriodo({ desde, hasta: e.target.value })} />
        <Button type="submit" disabled={cargando}>
          <FileText size={16} /> {cargando ? 'Emitiendo…' : 'Ver estado de cuenta'}
        </Button>
      </form>

      {error && <p role="alert" className="mt-4 text-sm text-red-600">{error}</p>}

      {estado && (
        <div className="mt-6 space-y-5">
          <p className="text-sm text-slate-600">
            <strong>{estado.razonSocial}</strong> · del {fechaCorta(estado.desde)} al {fechaCorta(estado.hasta)}
          </p>
          <dl aria-label="Resumen" className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            {[
              ['Saldo anterior', estado.saldoAnterior],
              ['Cobrado en el periodo', estado.totalCobrado],
              ['Pagado en el periodo', estado.totalPagado],
              ['Saldo al cierre', estado.saldo],
            ].map(([etiqueta, valor]) => (
              <div key={etiqueta as string} className="rounded-lg border border-slate-200 p-3">
                <dt className="text-slate-500">{etiqueta}</dt>
                <dd className="text-base font-semibold text-slate-900">{soles(valor as number)}</dd>
              </div>
            ))}
          </dl>

          {estado.sinMovimientos ? (
            <p role="status" className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
              El cliente no tiene cobros ni pagos en este periodo.
            </p>
          ) : (
            <>
              <section aria-label="Cobros del periodo">
                <h3 className="mb-2 font-medium">Cobros</h3>
                <Table columnas={['Fecha de cobro', 'Monto', 'Estado actual']} hayFilas={estado.cobros.length > 0}
                  vacio="No hay cobros en este periodo.">
                  {estado.cobros.map((c) => (
                    <tr key={c.id}><Td>{fechaCorta(c.fechaCobro)}</Td><Td>{soles(c.monto)}</Td><Td>{ESTADO_COBRO[c.estado]}</Td></tr>
                  ))}
                </Table>
              </section>
              <section aria-label="Pagos del periodo">
                <h3 className="mb-2 font-medium">Pagos</h3>
                <Table columnas={['Fecha de pago', 'Cobro del', 'Medio', 'Monto']} hayFilas={estado.pagos.length > 0}
                  vacio="No hay pagos en este periodo.">
                  {estado.pagos.map((p) => (
                    <tr key={p.id}>
                      <Td>{fechaCorta(p.fechaPago)}</Td>
                      <Td>{fechaCorta(p.fechaCobro)}</Td>
                      <Td>{p.medioPago === 'OTRO' && p.medioPagoDetalle ? `Otro: ${p.medioPagoDetalle}` : MEDIOS[p.medioPago]}</Td>
                      <Td>{soles(p.monto)}</Td>
                    </tr>
                  ))}
                </Table>
              </section>
            </>
          )}
        </div>
      )}
    </Card>
  )
}
