import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { buscarObligacion, type Obligacion } from '../services/obligacionService'
import { listarPagos, registrarPago, type Pago } from '../services/pagoService'
import type { CalendarioItem } from '../services/calendarioService'
import { aIso, deIso, etiquetaSituacion } from '../utils/calendario'
import { fechaCorta } from '../utils/programacion'
import { CONSTANCIA_MAXIMO, resumenDePagos, validarPago, type ErroresPago } from '../utils/pagos'
import { soles } from '../utils/societario'
import Button from '../components/Button'
import Card from '../components/Card'
import { Input } from '../components/Input'

const mesPeriodo = (p: string) => p.slice(0, 7)

/** Estado y vencimiento con la misma regla del calendario: una pagada o declarada nunca sale «Vencida»; con pago parcial se debe saldo. */
function etiquetaEstado(o: Obligacion, hoy: string): string {
  const dias = Math.round((deIso(o.fechaVencimiento).getTime() - deIso(hoy).getTime()) / 86_400_000)
  const item: CalendarioItem = { obligacion: o, diasRestantes: dias, vencimientoPasado: dias < 0 }
  if (o.estado === 'PENDIENTE') return 'Pendiente de declarar'
  if (o.estado === 'PAGO_PARCIAL') return `Pago parcial · ${etiquetaSituacion(item)}`
  return etiquetaSituacion(item)
}

/** HU-13 · Tarea 7: registrar un pago de una obligación declarada. El comprobante es el número de constancia; no se carga archivo. */
export default function PagoPage() {
  const obligacionId = Number(useParams().obligacionId)
  const [obligacion, setObligacion] = useState<Obligacion | null>(null)
  const [pagos, setPagos] = useState<Pago[]>([])
  const [fecha, setFecha] = useState(() => aIso(new Date()))
  const [monto, setMonto] = useState('')
  const [constancia, setConstancia] = useState('')
  const [errores, setErrores] = useState<ErroresPago>({})
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [guardando, setGuardando] = useState(false)

  const hoy = aIso(new Date())

  const cargar = useCallback(async () => {
    const [o, p] = await Promise.all([buscarObligacion(obligacionId), listarPagos(obligacionId)])
    setObligacion(o)
    setPagos(p)
  }, [obligacionId])

  useEffect(() => {
    cargar().catch((e) => setError(mensajeError(e)))
  }, [cargar])

  const { acumulado, saldo } = obligacion ? resumenDePagos(obligacion, pagos) : { acumulado: 0, saldo: 0 }
  const admitePagos = obligacion?.estado === 'DECLARADO' || obligacion?.estado === 'PAGO_PARCIAL'

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setError('')
    setAviso('')
    const problemas = validarPago({ fechaPago: fecha, montoPagado: monto, numeroConstancia: constancia }, saldo, hoy, pagos)
    setErrores(problemas)
    if (Object.keys(problemas).length > 0) return
    setGuardando(true)
    try {
      const r = await registrarPago(obligacionId, { fechaPago: fecha, montoPagado: Number(monto), numeroConstancia: constancia.trim() })
      setAviso(r.estado === 'PAGADO' ? 'Pago registrado: la obligación quedó pagada.' : `Pago registrado: queda pago parcial con saldo de ${soles(r.saldo)}.`)
      setMonto('')
      setConstancia('')
      await cargar()
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="max-w-3xl space-y-4 p-4">
      <h1 className="text-xl font-semibold">Registrar pago</h1>

      {obligacion && (
        <Card>
          <p>
            {obligacion.tipoObligacion ?? 'Obligación'} · periodo <strong>{mesPeriodo(obligacion.periodo)}</strong> · vence {fechaCorta(obligacion.fechaVencimiento)}
          </p>
          <p data-testid="estado" className="mt-1 font-medium">{etiquetaEstado(obligacion, hoy)}</p>
          {obligacion.estado !== 'PENDIENTE' && (
            <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt>Monto determinado</dt><dd data-testid="determinado">{soles(obligacion.montoDeterminado ?? 0)}</dd>
              <dt>Acumulado pagado</dt><dd data-testid="acumulado">{soles(acumulado)}</dd>
              <dt>Saldo pendiente</dt><dd data-testid="saldo">{soles(saldo)}</dd>
            </dl>
          )}
        </Card>
      )}

      {obligacion?.estado === 'PENDIENTE' && (
        <p role="status" className="text-sm text-slate-600">
          Solo se registra pago sobre una obligación ya declarada.{' '}
          <Link className="text-brand-600 hover:underline" to={`/obligaciones/${obligacion.id}/declarar`}>Registrar la declaración</Link>
        </p>
      )}
      {obligacion?.estado === 'PAGADO' && (
        <p role="status" className="text-sm text-slate-600">La obligación ya está pagada: no admite más pagos.</p>
      )}

      {admitePagos && (
        <Card title="Nuevo pago">
          <form onSubmit={guardar} noValidate className="grid gap-4 sm:grid-cols-3">
            <Input label="Fecha del pago *" type="date" value={fecha} max={hoy} error={errores.fechaPago}
              onChange={(e) => setFecha(e.target.value)} />
            <Input label="Monto pagado (S/) *" type="number" min="0" step="0.01" value={monto} error={errores.montoPagado}
              onChange={(e) => setMonto(e.target.value)} />
            <Input label="N° de constancia *" value={constancia} maxLength={CONSTANCIA_MAXIMO} error={errores.numeroConstancia}
              onChange={(e) => setConstancia(e.target.value)} />
            {error && <p role="alert" className="text-sm text-red-600 sm:col-span-3">{error}</p>}
            <div className="sm:col-span-3">
              <Button type="submit" disabled={guardando}>{guardando ? 'Registrando…' : 'Registrar pago'}</Button>
            </div>
          </form>
        </Card>
      )}

      {aviso && <p role="status" className="text-sm font-medium text-emerald-700">{aviso}</p>}
      {!obligacion && error && <p role="alert" className="text-red-600">{error}</p>}

      {pagos.length > 0 && (
        <Card title="Pagos registrados">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b text-slate-600">
                <th className="py-1">Fecha</th><th>N° de constancia</th><th className="text-right">Monto</th>
                <th className="text-right">Acumulado</th><th className="text-right">Saldo</th>
              </tr>
            </thead>
            <tbody>
              {pagos.map((p) => (
                <tr key={p.id} className="border-b last:border-0">
                  <td className="py-1">{fechaCorta(p.fechaPago)}</td>
                  <td>{p.numeroConstancia}</td>
                  <td className="text-right">{soles(p.montoPagado)}</td>
                  <td className="text-right">{soles(p.acumuladoDespues)}</td>
                  <td className="text-right">{soles(p.saldoDespues)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </section>
  )
}
