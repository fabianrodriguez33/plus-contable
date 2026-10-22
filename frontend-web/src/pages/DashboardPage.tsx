import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { listarClientes } from '../services/clienteService'
import { mensajeError } from '../services/apiClient'
import type { Cliente } from '../types'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'
import Button from '../components/Button'

export default function DashboardPage() {
  const [clientes, setClientes] = useState<Cliente[]>([])
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)

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
