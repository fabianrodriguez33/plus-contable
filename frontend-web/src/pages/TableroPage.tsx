import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { listarClientes } from '../services/clienteService'
import { consultarTablero, type FilaTablero } from '../services/tableroService'
import type { Cliente } from '../types'
import { fechaCorta, etiquetaTipo } from '../utils/programacion'
import { soles } from '../utils/societario'
import { ESTILO_ESTADO, ETIQUETA_ESTADO, mesActualLima, mesCorto, periodoDeMes, resumenTablero } from '../utils/tablero'
import Card from '../components/Card'
import { Input, Select } from '../components/Input'
import Table, { Td } from '../components/Table'

const monto = (n: number | null) => (n === null || n === undefined ? '—' : soles(n))

/**
 * HU-14 · Tareas 27 a 30: el tablero Declarado vs. Pagado. Cada obligación con su estado real (criterio 1), por cliente y por
 * mes (criterio 2), con su monto y su fecha límite (criterio 3) y el filtro «declarado sin pago» (criterio 4). «Vencida» solo
 * aparece si el servidor la marca vencida: una obligación pagada o declarada nunca la muestra.
 */
export default function TableroPage() {
  const [clientes, setClientes] = useState<Cliente[]>([])
  const [clienteId, setClienteId] = useState('')
  const [mes, setMes] = useState(mesActualLima)
  const [soloSinPago, setSoloSinPago] = useState(false)
  const [filas, setFilas] = useState<FilaTablero[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    listarClientes().then(setClientes).catch((e) => setError(mensajeError(e)))
  }, [])

  useEffect(() => {
    let vigente = true // si el filtro cambia antes de que llegue la respuesta, la vieja no pisa a la nueva
    setFilas(null)
    setError('')
    consultarTablero({ clienteId: clienteId ? Number(clienteId) : undefined, periodo: periodoDeMes(mes), soloDeclaradoSinPago: soloSinPago })
      .then((r) => { if (vigente) setFilas(r) })
      .catch((e) => { if (vigente) { setError(mensajeError(e)); setFilas([]) } })
    return () => { vigente = false }
  }, [clienteId, mes, soloSinPago])

  const resumen = resumenTablero(filas ?? [])

  return (
    <Card title="Tablero Declarado vs. Pagado">
      <div className="flex flex-wrap items-end gap-4">
        <div className="w-72">
          <Select label="Cliente" value={clienteId} onChange={(e) => setClienteId(e.target.value)}>
            <option value="">Todos los clientes</option>
            {clientes.map((c) => <option key={c.id} value={c.id}>{c.razonSocial} · {c.ruc}</option>)}
          </Select>
        </div>
        <Input label="Mes" type="month" value={mes} onChange={(e) => setMes(e.target.value)} />
        <label className="flex items-center gap-2 pb-2 text-sm text-slate-700">
          <input type="checkbox" checked={soloSinPago} onChange={(e) => setSoloSinPago(e.target.checked)} />
          Solo declaradas sin pago
        </label>
      </div>
      <p className="mt-1 text-xs text-slate-500">El mes es el periodo tributario; déjelo vacío para ver todos los meses.</p>

      {error && <p role="alert" className="mt-4 text-sm text-red-600">{error}</p>}

      {filas === null ? (
        <p className="mt-4 text-sm text-slate-500">Cargando...</p>
      ) : (
        <div className="mt-4 space-y-3">
          {filas.length > 0 && (
            <p aria-label="Resumen" className="text-sm text-slate-600">
              {resumen.pendientes} pendientes · {resumen.declaradas} declaradas · {resumen.pagoParcial} con pago parcial · {resumen.pagadas} pagadas
              {resumen.vencidas > 0 && <> · <strong className="text-red-700">{resumen.vencidas} vencidas</strong></>}
            </p>
          )}
          <Table columnas={['Cliente', 'Tributo', 'Mes', 'Fecha límite', 'Monto', 'Pagado', 'Saldo', 'Estado']}
            hayFilas={filas.length > 0} vacio={error ? 'No se pudo cargar el tablero.' : 'No hay obligaciones para este filtro.'}>
            {filas.map((f) => (
              <tr key={f.obligacionId}>
                <Td><Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${f.clienteId}`}>{f.razonSocial}</Link></Td>
                <Td>{f.tipoObligacion ? etiquetaTipo(f.tipoObligacion) : '—'}</Td>
                <Td>{mesCorto(f.periodo)}</Td>
                <Td>{fechaCorta(f.fechaVencimiento)}</Td>
                <Td>{monto(f.montoDeterminado)}</Td>
                <Td>{soles(f.montoPagado)}</Td>
                <Td>{monto(f.saldo)}</Td>
                <Td>
                  <span className="flex flex-wrap items-center gap-2">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${ESTILO_ESTADO[f.estado]}`}>{ETIQUETA_ESTADO[f.estado]}</span>
                    {f.vencida && <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">Vencida</span>}
                  </span>
                </Td>
              </tr>
            ))}
          </Table>
        </div>
      )}
    </Card>
  )
}
