import { useEffect, useState } from 'react'
import { mensajeError } from '../../services/apiClient'
import { listarAvisosPago, type AvisoPago } from '../../services/avisoClienteService'
import type { TipoObligacion } from '../../services/programacionService'
import { fechaCorta, etiquetaTipo } from '../../utils/programacion'
import { soles } from '../../utils/societario'
import Table, { Td } from '../Table'

interface Props {
  clienteId: number
  setError: (m: string) => void
}

/** HU-33 · Tarea 204: cada aviso de pago muestra el tributo, el monto por pagar y la fecha de vencimiento. */
export default function AvisosPagoTab({ clienteId, setError }: Props) {
  const [avisos, setAvisos] = useState<AvisoPago[] | null>(null)

  useEffect(() => {
    listarAvisosPago(clienteId).then(setAvisos).catch((e) => { setError(mensajeError(e)); setAvisos([]) })
  }, [clienteId, setError])

  if (avisos === null) return <p className="text-sm text-slate-500">Cargando...</p>
  return (
    <Table columnas={['Aviso del', 'Tributo', 'Monto por pagar', 'Vence']} hayFilas={avisos.length > 0}
      vacio="No hay avisos de pago: ningún pago vence en los próximos días.">
      {avisos.map((a) => (
        <tr key={a.id}>
          <Td>{fechaCorta(a.fechaAviso)}</Td>
          <Td>{etiquetaTipo(a.tributo as TipoObligacion)}</Td>
          <Td>{soles(a.monto)}</Td>
          <Td>{fechaCorta(a.fechaVencimiento)}</Td>
        </tr>
      ))}
    </Table>
  )
}
