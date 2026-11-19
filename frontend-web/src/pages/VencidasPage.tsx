import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { listarVencidas, type ObligacionVencida, type ObligacionesVencidas } from '../services/vencidasService'
import type { TipoObligacion } from '../services/programacionService'
import { etiquetaTipo, fechaCorta } from '../utils/programacion'
import { soles } from '../utils/societario'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'

export const ETIQUETA_SIN_PAGO = 'VENCIDA SIN PAGO'
export const ETIQUETA_CON_SALDO = 'VENCIDA CON SALDO'
const SIN_MONTO = 'Aún sin monto'
const dias = (n: number) => (n === 1 ? '1 día' : `${n} días`)
const monto = (n: number | null) => (n != null ? soles(n) : SIN_MONTO)

const Cliente = ({ o }: { o: ObligacionVencida }) => (
  <Td><Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${o.clienteId}`}>{o.razonSocial}</Link>
    <span className="block font-mono text-xs text-slate-500">{o.ruc}</span></Td>
)
const Tributo = ({ o }: { o: ObligacionVencida }) =>
  <Td>{o.tipoObligacion ? etiquetaTipo(o.tipoObligacion as TipoObligacion) : 'Sin clasificar'}</Td>

/**
 * HU-16 · tarea 43: las obligaciones vencidas en rojo y, aparte, las que siguen sin declarar con la fecha pasada (pendientes de
 * atención). Definición de Beto: vencida = fecha pasada y saldo pendiente; con pago parcial sigue vencida y se ve lo pagado y el
 * saldo. La marca la decide el servidor: esta pantalla no recalcula nada. Solo el estudio la ve.
 */
export default function VencidasPage() {
  const [datos, setDatos] = useState<ObligacionesVencidas | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    listarVencidas().then(setDatos).catch((e) => { setError(mensajeError(e)); setDatos({ vencidas: [], sinDeclarar: [] }) })
  }, [])

  return (
    <Card title="Obligaciones vencidas">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      {datos === null ? (
        <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        <div className="space-y-8">
          <section aria-labelledby="vencidas-titulo">
            <h3 id="vencidas-titulo" className="mb-2 font-semibold">Vencidas</h3>
            <Table columnas={['Cliente', 'Tributo', 'Periodo', 'Fecha límite', 'Atraso', 'Monto', 'Pagado', 'Saldo', 'Situación']}
              hayFilas={datos.vencidas.length > 0}
              vacio={error ? 'No se pudo cargar la lista.' : 'No hay obligaciones vencidas con saldo pendiente.'}>
              {datos.vencidas.map((o) => (
                <tr key={o.obligacionId} className="bg-red-50/40">
                  <Cliente o={o} />
                  <Tributo o={o} />
                  <Td className="whitespace-nowrap">{o.periodo.slice(0, 7)}</Td>
                  <Td className="whitespace-nowrap">{fechaCorta(o.fechaVencimiento)}</Td>
                  <Td className="whitespace-nowrap font-semibold text-peligro-texto">{dias(o.diasVencida)}</Td>
                  <Td className="whitespace-nowrap">{monto(o.montoDeterminado)}</Td>
                  <Td className="whitespace-nowrap">{soles(o.montoPagado)}</Td>
                  <Td className="whitespace-nowrap">{monto(o.saldo)}</Td>
                  <Td><span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-bold text-white">
                    {o.estado === 'PAGO_PARCIAL' ? ETIQUETA_CON_SALDO : ETIQUETA_SIN_PAGO}</span></Td>
                </tr>
              ))}
            </Table>
            {datos.vencidas.length > 0 && (
              <p className="mt-3 text-sm text-slate-600">{datos.vencidas.length === 1 ? '1 obligación vencida' : `${datos.vencidas.length} obligaciones vencidas`}</p>
            )}
          </section>

          <section aria-labelledby="sin-declarar-titulo">
            <h3 id="sin-declarar-titulo" className="mb-2 font-semibold">Sin declarar · pendientes de atención</h3>
            <Table columnas={['Cliente', 'Tributo', 'Periodo', 'Fecha límite', 'Atraso']}
              hayFilas={datos.sinDeclarar.length > 0} vacio="No hay obligaciones sin declarar con la fecha pasada.">
              {datos.sinDeclarar.map((o) => (
                <tr key={o.obligacionId} className="bg-amber-50/50">
                  <Cliente o={o} />
                  <Tributo o={o} />
                  <Td className="whitespace-nowrap">{o.periodo.slice(0, 7)}</Td>
                  <Td className="whitespace-nowrap">{fechaCorta(o.fechaVencimiento)}</Td>
                  <Td className="whitespace-nowrap font-semibold text-amber-700">{dias(o.diasVencida)}</Td>
                </tr>
              ))}
            </Table>
          </section>
        </div>
      )}
    </Card>
  )
}
