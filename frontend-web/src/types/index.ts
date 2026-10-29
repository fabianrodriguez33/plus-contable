export type Regimen = 'MYPE' | 'REMIPE' | 'GENERAL'

export interface Usuario {
  id: number
  username: string
  nombreCompleto: string
  roles: string[]
}

export interface Cliente {
  id?: number
  ruc: string
  razonSocial: string
  regimenTributario: Regimen
  condicionHabido: 'HABIDO' | 'NO_HABIDO'
  direccion?: string
  usuarioSol?: string
  claveSol?: string
  tieneClaveSol?: boolean
}

export interface Socio {
  id?: number
  nombreCompleto: string
  tipoDocumento: string
  numeroDocumento: string
  aporte: number
  porcentaje: number
  cargo?: string
}

export interface ResumenSocietario {
  socios: Socio[]
  capitalSocialTotal: number
  porcentajeTotal: number
  diferencia: number
  completo: boolean
}

export interface ActivoFijo {
  id?: number
  codigo?: string
  descripcion: string
  categoria?: string
  valorAdquisicion: number
  fechaAdquisicion?: string
}
