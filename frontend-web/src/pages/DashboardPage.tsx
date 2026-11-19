import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { KeyRound, Plus } from 'lucide-react'
import { listarClientes } from '../services/clienteService'
import { listarSolicitudesRestablecimiento, SolicitudRestablecimiento } from '../services/cuentaClienteService'
import { formatearVencimiento } from '../components/ficha/AccesoClienteTab'
import { mensajeError } from '../services/apiClient'
import type { Cliente } from '../types'
import Card from '../components/Card'
import Button from '../components/Button'
import '../styles/piloto.css'

export default function DashboardPage() {
  const [clientes, setClientes] = useState<Cliente[]>([])
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)
  const [solicitudes, setSolicitudes] = useState<SolicitudRestablecimiento[]>([])

  // HU-41: si falla (p. ej., 403 fuera del estudio) simplemente no se muestra el aviso
  useEffect(() => {
    listarSolicitudesRestablecimiento().then(setSolicitudes).catch(() => setSolicitudes([]))
  }, [])

  useEffect(() => {
    listarClientes()
      .then(setClientes)
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargando(false))
  }, [])

  return (
    <div className="piloto">
    <Card title="Cartera de clientes"
      actions={<Link to="/clientes/nuevo"><Button className="whitespace-nowrap"><Plus size={16} /> Nuevo cliente</Button></Link>}>
      {error && <div role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</div>}
      {solicitudes.length > 0 && (
        <section aria-label="Solicitudes de nueva contraseña" className="mb-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <p className="flex items-center gap-2 font-medium">
            <KeyRound size={16} /> Clientes que pidieron una nueva contraseña
          </p>
          <p className="mt-1 text-amber-800">
            Abra su ficha, use «Restablecer acceso» en la pestaña «Acceso del cliente» y entréguele la contraseña temporal.
          </p>
          <ul className="mt-2 space-y-1">
            {solicitudes.map((s) => (
              <li key={s.clienteId}>
                <Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${s.clienteId}`}>
                  {clientes.find((c) => c.id === s.clienteId)?.razonSocial ?? `Cliente ${s.clienteId}`}
                </Link>{' '}
                · {s.correo} · pedido el {formatearVencimiento(s.solicitadaEn)}
              </li>
            ))}
          </ul>
        </section>
      )}
      {cargando ? (
        <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        // Desplazable de lado en pantallas angostas; «Razón social» queda fija a la izquierda y no se parte palabra por palabra
        <div role="region" aria-label="Cartera de clientes" tabIndex={0} className="overflow-x-auto">
          <table className="min-w-[44rem] w-full text-sm">
            <thead>
              <tr className="border-b-2 border-tinta">
                <th scope="col" className="sticky left-0 z-10 min-w-[11rem] bg-white px-3 py-2 text-left font-semibold text-tinta shadow-[1px_0_0_rgb(var(--tinta)/0.12)]">Razón social</th>
                {['RUC', 'Régimen', 'Condición', 'Clave SOL'].map((c) => (
                  <th key={c} scope="col" className="whitespace-nowrap px-3 py-2 text-left font-semibold text-tinta">{c}</th>
                ))}
                <th scope="col" className="px-3 py-2"><span className="sr-only">Acciones</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-tinta/10">
              {clientes.length === 0 ? (
                <tr><td colSpan={6} className="px-3 py-8 text-center text-slate-500">Aún no hay clientes registrados.</td></tr>
              ) : clientes.map((c) => (
                <tr key={c.id} className="group">
                  <td className="sticky left-0 z-10 min-w-[11rem] bg-white px-3 py-2.5 align-top font-medium text-tinta shadow-[1px_0_0_rgb(var(--tinta)/0.12)] group-hover:bg-slate-50">{c.razonSocial}</td>
                  <td className="whitespace-nowrap px-3 py-2.5 align-top font-mono text-slate-700 group-hover:bg-slate-50">{c.ruc}</td>
                  <td className="whitespace-nowrap px-3 py-2.5 align-top text-slate-700 group-hover:bg-slate-50">{c.regimenTributario}</td>
                  <td className="whitespace-nowrap px-3 py-2.5 align-top text-slate-700 group-hover:bg-slate-50">{c.condicionHabido === 'HABIDO' ? 'Habido' : 'No habido'}</td>
                  <td className="whitespace-nowrap px-3 py-2.5 align-top text-slate-700 group-hover:bg-slate-50">{c.tieneClaveSol ? 'Registrada' : '—'}</td>
                  <td className="whitespace-nowrap px-3 py-2.5 align-top group-hover:bg-slate-50"><Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${c.id}`}>Ficha maestra</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
    </div>
  )
}
