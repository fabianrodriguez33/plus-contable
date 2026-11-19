import { useMemo, useState } from 'react'
import { clienteIdActual } from '../services/authService'
import { useConsultaConCache } from '../hooks/useConsultaConCache'
import EstadoConexion from '../components/EstadoConexion'
import { listarObligaciones } from '../services/obligacionService'
import { COLOR_ESTADO, ETIQUETA_ESTADO, delMes, mesDeHoyEnLima, mesesDisponibles } from '../utils/estadoCuentaCliente'
import { etiquetaTipo, fechaCorta } from '../utils/programacion'
import type { TipoObligacion } from '../services/programacionService'
import { soles } from '../utils/societario'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre']
const nombreMes = (m: string) => `${MESES[Number(m.slice(5, 7)) - 1]} ${m.slice(0, 4)}`
const SIN_MONTO = 'Aún sin monto'

const Estado = ({ estado }: { estado: keyof typeof ETIQUETA_ESTADO }) => (
  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${COLOR_ESTADO[estado]}`}>{ETIQUETA_ESTADO[estado]}</span>
)

/**
 * HU-32 · tareas 197 a 199: el cliente ve cada tributo de un mes con su estado, monto y fecha límite. Solo lectura y solo de
 * su empresa (sale de su propio token y el servidor vuelve a verificarla). En el celular cada tributo es una tarjeta.
 */
export default function MiEstadoCuentaPage() {
  const clienteId = clienteIdActual()
  const mesActual = useMemo(() => mesDeHoyEnLima(), [])
  const [mes, setMes] = useState(mesActual)
  const { datos, estado, actualizadoEn, error } = useConsultaConCache(
    clienteId === null ? null : `estado-cuenta-tributario:${clienteId}`, () => listarObligaciones(clienteId!))

  if (clienteId === null) {
    return <Card title="Mi estado de cuenta"><p role="alert" className="text-sm text-red-600">Su cuenta no está vinculada a una empresa. Comuníquese con el estudio.</p></Card>
  }
  const tributos = datos === null ? [] : delMes(datos, mes)
  return (
    <Card title="Mi estado de cuenta">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      <EstadoConexion estado={estado} actualizadoEn={actualizadoEn} />
      {datos === null ? (
        estado === 'cargando' && <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        <div className="space-y-4">
          <label className="block text-sm">
            <span className="mb-1 block text-slate-600">Mes</span>
            <select value={mes} onChange={(e) => setMes(e.target.value)}
              className="w-full rounded border border-slate-300 px-3 py-2 sm:w-64">
              {mesesDisponibles(datos, mesActual).map((m) => <option key={m} value={m}>{nombreMes(m)}</option>)}
            </select>
          </label>

          {tributos.length === 0 ? (
            <p className="text-sm text-slate-500">No hay tributos registrados para {nombreMes(mes)}.</p>
          ) : (
            <>
              <div className="hidden md:block" data-testid="vista-tabla">
                <Table columnas={['Tributo', 'Estado', 'Monto', 'Fecha límite']} hayFilas vacio="">
                  {tributos.map((o) => (
                    <tr key={o.id}>
                      <Td>{o.tipoObligacion ? etiquetaTipo(o.tipoObligacion as TipoObligacion) : 'Sin clasificar'}</Td>
                      <Td><Estado estado={o.estado} /></Td>
                      <Td>{o.montoDeterminado != null ? soles(o.montoDeterminado) : SIN_MONTO}</Td>
                      <Td>{fechaCorta(o.fechaVencimiento)}</Td>
                    </tr>
                  ))}
                </Table>
              </div>
              <ul className="space-y-3 md:hidden" data-testid="vista-tarjetas">
                {tributos.map((o) => (
                  <li key={o.id} className="panel-solido rounded-campo p-4 text-sm">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">{o.tipoObligacion ? etiquetaTipo(o.tipoObligacion as TipoObligacion) : 'Sin clasificar'}</span>
                      <Estado estado={o.estado} />
                    </div>
                    <dl className="mt-2 grid grid-cols-2 gap-2">
                      <div><dt className="text-slate-500">Monto</dt><dd>{o.montoDeterminado != null ? soles(o.montoDeterminado) : SIN_MONTO}</dd></div>
                      <div><dt className="text-slate-500">Fecha límite</dt><dd>{fechaCorta(o.fechaVencimiento)}</dd></div>
                    </dl>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </Card>
  )
}
