import { FormEvent, useCallback, useEffect, useState } from 'react'
import { mensajeError } from '../../services/apiClient'
import { listarCompras, registrarCompra, type Compra } from '../../services/compraService'
import { TIPOS_COMPRA, nombrePeriodo, pendientesPrimero } from '../../utils/compras'
import { fechaCorta } from '../../utils/programacion'
import { soles } from '../../utils/societario'
import Button from '../Button'
import { Input } from '../Input'
import Table, { Td } from '../Table'

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

/**
 * HU-25 · T211: el contador carga las compras del cliente (con el SIRE como referencia) y las ve ya clasificadas o pendientes. Solo
 * el cliente clasifica: aquí no hay botón para hacerlo. La alerta marca las que siguen sin clasificar cerca del cierre del mes.
 */
export default function ComprasTab({ clienteId, setError, setOk }: Props) {
  const [compras, setCompras] = useState<Compra[] | null>(null)
  const [mes, setMes] = useState('')
  const [ruc, setRuc] = useState('')
  const [razon, setRazon] = useState('')
  const [comprobante, setComprobante] = useState('')
  const [fecha, setFecha] = useState('')
  const [importe, setImporte] = useState('')
  const [guardando, setGuardando] = useState(false)

  const cargar = useCallback(() => {
    listarCompras(clienteId).then(setCompras).catch((e) => { setError(mensajeError(e)); setCompras([]) })
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const registrar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    const monto = Number(importe)
    if (!mes || !ruc.trim() || !comprobante.trim() || !fecha || !(monto > 0)) {
      return setError('Complete el mes, el RUC del proveedor, el comprobante, la fecha y un importe mayor que cero')
    }
    setGuardando(true)
    try {
      await registrarCompra(clienteId, {
        periodo: `${mes}-01`, proveedorRuc: ruc.trim(), comprobante: comprobante.trim(), fechaComprobante: fecha, importe: monto,
        ...(razon.trim() && { proveedorRazonSocial: razon.trim() }),
      })
      setOk(`Compra ${comprobante.trim()} registrada: queda sin clasificar hasta que el cliente la clasifique`)
      setRuc(''); setRazon(''); setComprobante(''); setFecha(''); setImporte('')
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  if (compras === null) return <p className="text-sm text-slate-500">Cargando...</p>
  const lista = pendientesPrimero(compras)
  return (
    <div className="space-y-5">
      <form onSubmit={registrar} noValidate className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Input label="Mes tributario" type="month" value={mes} onChange={(e) => setMes(e.target.value)} />
          <Input label="RUC del proveedor" inputMode="numeric" maxLength={11} value={ruc} onChange={(e) => setRuc(e.target.value)} />
          <Input label="Razón social (opcional)" maxLength={200} value={razon} onChange={(e) => setRazon(e.target.value)} />
          <Input label="Comprobante" maxLength={30} value={comprobante} onChange={(e) => setComprobante(e.target.value)} />
          <Input label="Fecha del comprobante" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
          <Input label="Importe (S/)" type="number" step="0.01" min="0" value={importe} onChange={(e) => setImporte(e.target.value)} />
        </div>
        <Button type="submit" disabled={guardando}>{guardando ? 'Guardando...' : 'Registrar compra'}</Button>
      </form>

      <Table columnas={['Mes', 'Proveedor', 'Comprobante', 'Fecha', 'Importe', 'Clasificación', 'Descripción']} hayFilas={lista.length > 0}
        vacio="No hay compras registradas para este cliente.">
        {lista.map((c) => (
          <tr key={c.id}>
            <Td>{nombrePeriodo(c.periodo)}</Td>
            <Td>{c.proveedorRazonSocial ?? c.proveedorRuc}<span className="block font-mono text-xs text-slate-500">{c.proveedorRuc}</span></Td>
            <Td>{c.comprobante}</Td>
            <Td>{fechaCorta(c.fechaComprobante)}</Td>
            <Td>{soles(c.importe)}</Td>
            <Td>
              {c.clasificada
                ? <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">{TIPOS_COMPRA[c.tipo!]}</span>
                : <span className="flex flex-wrap gap-1">
                    <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">Sin clasificar</span>
                    {c.alertaCierre && <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-bold text-white">Cierre cerca</span>}
                  </span>}
            </Td>
            <Td>{c.descripcion ?? '—'}</Td>
          </tr>
        ))}
      </Table>
    </div>
  )
}
