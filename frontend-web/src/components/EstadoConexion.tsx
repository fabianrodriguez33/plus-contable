import Aviso from './Aviso'
import { fechaHora } from '../services/cacheLocal'
import type { EstadoConsulta } from '../hooks/useConsultaConCache'

interface Props {
  estado: EstadoConsulta
  actualizadoEn: string | null
}

/** HU-34: dice al cliente que está viendo la última información guardada (con su fecha) o que debe conectarse la primera vez. */
export default function EstadoConexion({ estado, actualizadoEn }: Props) {
  if (estado === 'sin-conexion' && actualizadoEn) {
    return <Aviso tipo="advertencia" className="mb-4">Sin conexión. Se muestra la información de su última actualización: {fechaHora(actualizadoEn)}.</Aviso>
  }
  if (estado === 'primera-vez') {
    return <Aviso tipo="info">Sin conexión. Conéctese a internet la primera vez para ver su información.</Aviso>
  }
  return null
}
