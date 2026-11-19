import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { listarVencidasSinPago, type ObligacionVencidaSinPago } from '../services/vencidasSinPagoService'
import type { TipoObligacion } from '../services/programacionService'
import { etiquetaTipo, fechaCorta } from '../utils/programacion'
import { soles } from '../utils/societario'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'

export const ETIQUETA_VENCIDA = 'VENCIDA SIN PAGO'
const SIN_MONTO = 'Aún sin monto'
const dias = (n: number) => (n === 1 ? '1 día' : `${n} días`)

/**
 * HU-16 · tarea 43: las obligaciones vencidas y sin constancia de pago, en rojo como «VENCIDA SIN PAGO». La marca la decide el
 * servidor (la fecha límite ya pasó y no hay ninguna constancia): esta pantalla no recalcula nada. Solo el estudio la ve.
 */
export default function VencidasSinPagoPage() {
  const [lista, setLista] = useState<ObligacionVencidaSinPago[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    listarVencidasSinPago().then(setLista).catch((e) => { setError(mensajeError(e)); setLista([]) })
  }, [])

  return (
    <Card title="Vencidas sin pago">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      {lista === null ? (
        <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        <>
          <Table columnas={['Cliente', 'Tributo', 'Periodo', 'Fecha límite', 'Atraso', 'Monto', 'Situación']}
            hayFilas={lista.length > 0}
            vacio={error ? 'No se pudo cargar la lista.' : 'No hay obligaciones vencidas sin constancia de pago.'}>
            {lista.map((o) => (
              <tr key={o.obligacionId} className="bg-red-50/40">
                <Td><Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${o.clienteId}`}>{o.razonSocial}</Link>
                  <span className="block font-mono text-xs text-slate-500">{o.ruc}</span></Td>
                <Td>{o.tipoObligacion ? etiquetaTipo(o.tipoObligacion as TipoObligacion) : 'Sin clasificar'}</Td>
                <Td>{o.periodo.slice(0, 7)}</Td>
                <Td>{fechaCorta(o.fechaVencimiento)}</Td>
                <Td>{dias(o.diasVencida)}</Td>
                <Td>{o.montoDeterminado != null ? soles(o.montoDeterminado) : SIN_MONTO}</Td>
                <Td><span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-bold text-white">{ETIQUETA_VENCIDA}</span></Td>
              </tr>
            ))}
          </Table>
          {lista.length > 0 && (
            <p className="mt-3 text-sm text-slate-600">{lista.length === 1 ? '1 obligación vencida sin pago' : `${lista.length} obligaciones vencidas sin pago`}</p>
          )}
        </>
      )}
    </Card>
  )
}
