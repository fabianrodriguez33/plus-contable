import { tributarioApi } from './apiClient'

export type TipoObligacion = 'IGV' | 'RENTA' | 'PLAME' | 'ESSALUD' | 'RETENCION_QUINTA' | 'AFP'

export interface ObligacionProgramada {
  id: number
  tipoObligacion: TipoObligacion
  periodo: string
  fechaVencimiento: string
  estado: 'PENDIENTE' | 'DECLARADO'
}

export interface TipoNoGenerado {
  tipo: TipoObligacion
  motivo: string
}

/** Respuesta de POST /api/tributario/obligaciones/programacion (HU-07). */
export interface ProgramacionMes {
  periodo: string
  fechaVencimiento: string
  creadas: ObligacionProgramada[]
  yaExistian: TipoObligacion[]
  noGeneradas: TipoNoGenerado[]
}

/** periodo: "AAAA-MM". Sin cronograma de ese mes el servidor responde 400 con "Falta el cronograma de AAAA-MM". */
export const programarMes = (clienteId: number, periodo: string) =>
  tributarioApi
    .post<ProgramacionMes>('/api/tributario/obligaciones/programacion', { clienteId, periodo })
    .then((r) => r.data)
