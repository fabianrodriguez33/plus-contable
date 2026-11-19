import { tributarioApi } from './apiClient'

export type TipoNotificacion = 'ESQUELA' | 'CARTA_INDUCTIVA' | 'COMUNICADO'

/** Una alerta del plazo de respuesta de una notificación del Buzón SOL (HU-19). diasRestantes lo calcula el servidor con el «hoy» de Lima. */
export interface AlertaPlazo {
  id: number
  notificacionId: number
  clienteId: number
  ruc: string
  razonSocial: string
  tipo: TipoNotificacion
  /** AAAA-MM-DD */
  plazoRespuesta: string
  /** El escalón en que se generó: 5, 3, 1 o 0 días antes del plazo. */
  diasAntes: number
  fechaAlerta: string
  diasRestantes: number
}

export const listarAlertasPlazo = () => tributarioApi.get<AlertaPlazo[]>('/api/tributario/alertas-plazo').then((r) => r.data)
