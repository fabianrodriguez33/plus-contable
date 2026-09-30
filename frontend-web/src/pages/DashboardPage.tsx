import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Cliente, listarClientes } from '../services/clienteService'
import { mensajeError } from '../services/apiClient'

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
    <div className="card">
      <div className="row">
        <h2>Cartera de clientes</h2>
        <Link to="/clientes/nuevo"><button>+ Nuevo cliente</button></Link>
      </div>
      {error && <div className="error">{error}</div>}
      {cargando ? (
        <p className="muted">Cargando...</p>
      ) : clientes.length === 0 ? (
        <p className="muted">Aún no hay clientes registrados.</p>
      ) : (
        <table>
          <thead>
            <tr><th>RUC</th><th>Razón social</th><th>Régimen</th><th>Condición</th><th>Clave SOL</th><th></th></tr>
          </thead>
          <tbody>
            {clientes.map((c) => (
              <tr key={c.id}>
                <td>{c.ruc}</td>
                <td>{c.razonSocial}</td>
                <td>{c.regimenTributario}</td>
                <td>{c.condicionHabido === 'HABIDO' ? 'Habido' : 'No habido'}</td>
                <td>{c.tieneClaveSol ? 'Registrada' : '—'}</td>
                <td><Link to={`/clientes/${c.id}`}>Ficha maestra</Link></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
