import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { listarAlertasPlazo, type AlertaPlazo } from '../services/alertaPlazoService'
import { ETIQUETA_TIPO, textoDiasRestantes, textoEscalon } from '../utils/alertas'
import { fechaCorta } from '../utils/programacion'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'
import Sello, { type TonoSello } from '../components/Sello'

/** Cuanto más cerca el plazo, más urgente: hoy y mañana en rojo, hasta 3 días en ámbar, el resto neutro. */
const tonoDe = (dias: number): TonoSello => (dias <= 1 ? 'peligro' : dias <= 3 ? 'aviso' : 'neutro')

/**
 * HU-19 · Tarea 90: las alertas escalonadas del plazo de respuesta de las notificaciones del Buzón SOL que siguen pendientes.
 * Cada alerta muestra el cliente y los días que faltan (criterio 3). Solo dentro del sistema: sin correo ni WhatsApp.
 */
export default function AlertasPlazoPage() {
  const [alertas, setAlertas] = useState<AlertaPlazo[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    listarAlertasPlazo().then(setAlertas).catch((e) => { setError(mensajeError(e)); setAlertas([]) })
  }, [])

  return (
    <Card title="Alertas de plazo de respuesta">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      {alertas === null ? (
        <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        <Table columnas={['Cliente', 'Notificación', 'Plazo', 'Días restantes', 'Alerta']} hayFilas={alertas.length > 0}
          vacio={error ? 'No se pudieron cargar las alertas.' : 'No hay alertas de plazo.'}>
          {alertas.map((a) => (
            <tr key={a.id}>
              <Td>
                <Link className="font-medium text-brand-600 hover:underline" to={`/clientes/${a.clienteId}`}>{a.razonSocial}</Link>
                <span className="block font-mono text-xs text-slate-500">{a.ruc}</span>
              </Td>
              <Td>{ETIQUETA_TIPO[a.tipo]}</Td>
              <Td>{fechaCorta(a.plazoRespuesta)}</Td>
              <Td>
                <Sello tono={tonoDe(a.diasRestantes)} cifra={a.diasRestantes} texto={a.diasRestantes === 0 ? 'hoy' : a.diasRestantes === 1 ? 'día' : 'días'}
                  etiqueta={textoDiasRestantes(a.diasRestantes)} />
              </Td>
              <Td>{textoEscalon(a.diasAntes)} · {fechaCorta(a.fechaAlerta)}</Td>
            </tr>
          ))}
        </Table>
      )}
    </Card>
  )
}
