import { useMemo, useState } from 'react'
import { clienteIdActual } from '../services/authService'
import { useConsultaConCache } from '../hooks/useConsultaConCache'
import EstadoConexion from '../components/EstadoConexion'
import { listarObligaciones, type Obligacion } from '../services/obligacionService'
import {
  COLOR_ESTADO, ETIQUETA_ESTADO, delMes, estaVencida, hoyEnLima, mesDeHoyEnLima, mesesDisponibles, montosDe, sinDeclararAtrasada,
  sinDeclararDe, vencidasDe,
} from '../utils/estadoCuentaCliente'
import { etiquetaTipo, fechaCorta } from '../utils/programacion'
import type { TipoObligacion } from '../services/programacionService'
import { soles } from '../utils/societario'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre']
const nombreMes = (m: string) => `${MESES[Number(m.slice(5, 7)) - 1]} ${m.slice(0, 4)}`
const SIN_MONTO = 'Aún sin monto'

export const ETIQUETA_VENCIDA = 'Vencida'
export const ETIQUETA_ATENCION = 'Pendiente de atención'
/** Vista que reúne las vencidas y las sin declarar atrasadas de todos los meses. */
export const VISTA_ATENCION = 'ATENCION'
const cifra = (n: number | null) => (n === null ? '—' : soles(n))

const Estado = ({ o, hoy }: { o: Obligacion; hoy: string }) => (
  <span className="flex flex-wrap items-center gap-1">
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${COLOR_ESTADO[o.estado]}`}>{ETIQUETA_ESTADO[o.estado]}</span>
    {estaVencida(o, hoy) && <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-bold text-white">{ETIQUETA_VENCIDA}</span>}
    {sinDeclararAtrasada(o, hoy) && (
      <span className="rounded-full border border-amber-400 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">{ETIQUETA_ATENCION}</span>
    )}
  </span>
)

const Tributo = ({ o }: { o: Obligacion }) => <>{o.tipoObligacion ? etiquetaTipo(o.tipoObligacion as TipoObligacion) : 'Sin clasificar'}</>

/** Tabla en pantallas medianas y tarjetas en el celular; `id` distingue las listas de una misma pantalla. */
function Lista({ id, tributos, hoy }: { id: string; tributos: Obligacion[]; hoy: string }) {
  return (
    <>
      <div className="hidden md:block" data-testid={`${id}-tabla`}>
        <Table columnas={['Tributo', 'Mes', 'Estado', 'Monto', 'Pagado', 'Saldo', 'Fecha límite']} hayFilas vacio="">
          {tributos.map((o) => (
            <tr key={o.id}>
              <Td><Tributo o={o} /></Td>
              <Td>{nombreMes(o.periodo)}</Td>
              <Td><Estado o={o} hoy={hoy} /></Td>
              <Td>{o.montoDeterminado != null ? soles(o.montoDeterminado) : SIN_MONTO}</Td>
              <Td>{cifra(montosDe(o).pagado)}</Td>
              <Td>{cifra(montosDe(o).saldo)}</Td>
              <Td>{fechaCorta(o.fechaVencimiento)}</Td>
            </tr>
          ))}
        </Table>
      </div>
      <ul className="space-y-3 md:hidden" data-testid={`${id}-tarjetas`}>
        {tributos.map((o) => (
          <li key={o.id} className="panel-solido rounded-campo p-4 text-sm">
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold"><Tributo o={o} /></span>
              <Estado o={o} hoy={hoy} />
            </div>
            <dl className="mt-2 grid grid-cols-2 gap-2">
              <div><dt className="text-slate-500">Mes</dt><dd>{nombreMes(o.periodo)}</dd></div>
              <div><dt className="text-slate-500">Fecha límite</dt><dd>{fechaCorta(o.fechaVencimiento)}</dd></div>
              <div><dt className="text-slate-500">Monto</dt><dd>{o.montoDeterminado != null ? soles(o.montoDeterminado) : SIN_MONTO}</dd></div>
              <div><dt className="text-slate-500">Pagado</dt><dd>{cifra(montosDe(o).pagado)}</dd></div>
              <div><dt className="text-slate-500">Saldo</dt><dd>{cifra(montosDe(o).saldo)}</dd></div>
            </dl>
          </li>
        ))}
      </ul>
    </>
  )
}

/**
 * HU-32 · tareas 197 a 199: el cliente ve cada tributo con su estado, monto, lo pagado, el saldo y la fecha límite, y las vencidas
 * marcadas (definición de Beto: fecha pasada y saldo pendiente; con pago parcial sigue vencida). <b>Al abrir</b>, si tiene vencidas o
 * sin declarar atrasadas de cualquier mes, ve primero esas (todas, no solo las del mes actual); si no, el mes actual. Las sin
 * declarar se muestran aparte y con su propia marca. Solo lectura y solo de su empresa (token y servidor). En el celular, tarjetas.
 */
export default function MiEstadoCuentaPage() {
  const clienteId = clienteIdActual()
  const mesActual = useMemo(() => mesDeHoyEnLima(), [])
  const hoy = useMemo(() => hoyEnLima(), [])
  const [elegida, setElegida] = useState<string | null>(null)
  const { datos, estado, actualizadoEn, error } = useConsultaConCache(
    clienteId === null ? null : `estado-cuenta-tributario:${clienteId}`, () => listarObligaciones(clienteId!))

  if (clienteId === null) {
    return <Card title="Mi estado de cuenta"><p role="alert" className="text-sm text-red-600">Su cuenta no está vinculada a una empresa. Comuníquese con el estudio.</p></Card>
  }
  const vencidas = datos === null ? [] : vencidasDe(datos, hoy)
  const sinDeclarar = datos === null ? [] : sinDeclararDe(datos, hoy)
  const hayAtencion = vencidas.length + sinDeclarar.length > 0
  const vista = elegida ?? (hayAtencion ? VISTA_ATENCION : mesActual)
  const tributos = datos === null || vista === VISTA_ATENCION ? [] : delMes(datos, vista)
  return (
    <Card title="Mi estado de cuenta">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      <EstadoConexion estado={estado} actualizadoEn={actualizadoEn} />
      {datos === null ? (
        estado === 'cargando' && <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        <div className="space-y-4">
          <label className="block text-sm">
            <span className="mb-1 block text-slate-600">Ver</span>
            <select value={vista} onChange={(e) => setElegida(e.target.value)} className="w-full rounded border border-slate-300 px-3 py-2 sm:w-72">
              {hayAtencion && <option value={VISTA_ATENCION}>Vencidas y por declarar (todos los meses)</option>}
              {mesesDisponibles(datos, mesActual).map((m) => <option key={m} value={m}>{nombreMes(m)}</option>)}
            </select>
          </label>

          {vista === VISTA_ATENCION ? (
            <div className="space-y-6">
              <section aria-labelledby="vencidas-titulo">
                <h3 id="vencidas-titulo" className="mb-2 font-semibold">Vencidas</h3>
                {vencidas.length === 0
                  ? <p className="text-sm text-slate-500">No tiene obligaciones vencidas.</p>
                  : <Lista id="vencidas" tributos={vencidas} hoy={hoy} />}
              </section>
              <section aria-labelledby="sin-declarar-titulo">
                <h3 id="sin-declarar-titulo" className="mb-1 font-semibold">Sin declarar · pendientes de atención</h3>
                <p className="mb-2 text-sm text-slate-500">El estudio todavía no las declaró, así que aún no hay un monto por pagar.</p>
                {sinDeclarar.length === 0
                  ? <p className="text-sm text-slate-500">No tiene obligaciones sin declarar con la fecha pasada.</p>
                  : <Lista id="sin-declarar" tributos={sinDeclarar} hoy={hoy} />}
              </section>
            </div>
          ) : tributos.length === 0 ? (
            <p className="text-sm text-slate-500">No hay tributos registrados para {nombreMes(vista)}.</p>
          ) : (
            <Lista id="mes" tributos={tributos} hoy={hoy} />
          )}
        </div>
      )}
    </Card>
  )
}
