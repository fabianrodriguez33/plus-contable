import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { KeyRound, Plus } from 'lucide-react'
import { listarClientes } from '../services/clienteService'
import { listarSolicitudesRestablecimiento, SolicitudRestablecimiento } from '../services/cuentaClienteService'
import { formatearVencimiento } from '../components/ficha/AccesoClienteTab'
import { mensajeError } from '../services/apiClient'
import type { Cliente } from '../types'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'
import Button from '../components/Button'

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
    <Card title="Cartera de clientes"
      actions={<Link to="/clientes/nuevo"><Button><Plus size={16} /> Nuevo cliente</Button></Link>}>
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
        <Table columnas={['RUC', 'Razón social', 'Régimen', 'Condición', 'Clave SOL', '']}
          hayFilas={clientes.length > 0} vacio="Aún no hay clientes registrados.">
          {clientes.map((c) => (
            <tr key={c.id} className="hover:bg-slate-50">
              <Td className="font-mono">{c.ruc}</Td>
              <Td>{c.razonSocial}</Td>
              <Td>{c.regimenTributario}</Td>
              <Td>{c.condicionHabido === 'HABIDO' ? 'Habido' : 'No habido'}</Td>
              <Td>{c.tieneClaveSol ? 'Registrada' : '—'}</Td>
              <Td><Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${c.id}`}>Ficha maestra</Link></Td>
            </tr>
          ))}
        </Table>
      )}
    </Card>
  )
}
