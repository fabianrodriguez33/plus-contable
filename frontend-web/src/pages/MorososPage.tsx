import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { listarMorosos, type Moroso } from '../services/expedienteService'
import { fechaCorta } from '../utils/programacion'
import { soles } from '../utils/societario'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'

/**
 * HU-31 · Tareas 151 a 153: los clientes con cobros vencidos, con el monto y los días de atraso (criterio 1); un cliente al día
 * no aparece (criterio 2). Solo el estudio la ve (criterio 3): el servidor responde 403 a cualquier otro perfil.
 */
export default function MorososPage() {
  const [morosos, setMorosos] = useState<Moroso[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    listarMorosos().then(setMorosos).catch((e) => { setError(mensajeError(e)); setMorosos([]) })
  }, [])

  const total = (morosos ?? []).reduce((acc, m) => acc + Number(m.montoVencido), 0)

  return (
    <Card title="Clientes morosos">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      {morosos === null ? (
        <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        <>
          <Table columnas={['Cliente', 'RUC', 'Monto vencido', 'Días de atraso', 'Cobros vencidos', 'Vencido desde']}
            hayFilas={morosos.length > 0} vacio={error ? 'No se pudo cargar la lista.' : 'No hay clientes morosos: todos están al día.'}>
            {morosos.map((m) => (
              <tr key={m.clienteId}>
                <Td><Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${m.clienteId}`}>{m.razonSocial}</Link></Td>
                <Td className="font-mono">{m.ruc}</Td>
                <Td>{soles(m.montoVencido)}</Td>
                <Td>{m.diasAtraso === 1 ? '1 día' : `${m.diasAtraso} días`}</Td>
                <Td>{m.cobrosVencidos}</Td>
                <Td>{fechaCorta(m.vencidoDesde)}</Td>
              </tr>
            ))}
          </Table>
          {morosos.length > 0 && (
            <p className="mt-3 text-sm text-slate-600">{morosos.length === 1 ? '1 cliente moroso' : `${morosos.length} clientes morosos`} · total vencido {soles(total)}</p>
          )}
        </>
      )}
    </Card>
  )
}
