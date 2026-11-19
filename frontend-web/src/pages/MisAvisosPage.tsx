import { useState } from 'react'
import { clienteIdActual } from '../services/authService'
import AvisosPagoTab from '../components/ficha/AvisosPagoTab'
import Card from '../components/Card'

/** HU-33 · Tarea 204: el cliente ve los avisos de pago de su empresa. La empresa sale de su token y el servidor la vuelve a verificar. */
export default function MisAvisosPage() {
  const clienteId = clienteIdActual()
  const [error, setError] = useState('')

  if (clienteId === null) {
    return <Card title="Avisos de pago"><p role="alert" className="text-sm text-red-600">Su cuenta no está vinculada a una empresa. Comuníquese con el estudio.</p></Card>
  }
  return (
    <Card title="Avisos de pago">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      <AvisosPagoTab clienteId={clienteId} setError={setError} />
    </Card>
  )
}
