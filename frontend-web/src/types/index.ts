export type Regimen = 'MYPE' | 'REMIPE' | 'GENERAL'
export type TipoDocumento = 'MINUTA' | 'COPIA_LITERAL' | 'VIGENCIA_PODERES' | 'FICHA_RUC'

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

export interface Documento {
  id: number
  tipo: TipoDocumento
  nombreArchivo: string
  tamanoBytes: number
  subidoEn: string
}

export interface Socio {
  id?: number
  nombreCompleto: string
  tipoDocumento: string
  numeroDocumento: string
  porcentaje: number
  cargo?: string
}

export interface Trabajador {
  id?: number
  nombreCompleto: string
  tipoDocumento: string
  numeroDocumento: string
  extranjero: boolean
  nacionalidad?: string
  regimenLaboral: Regimen
  sistemaPensionario: 'AFP' | 'ONP'
  nombreAfp?: string
  fechaIngreso?: string
  remuneracion?: number
  registroSunafil: boolean
  registroMtpe: boolean
}

export interface ActivoFijo {
  id?: number
  codigo?: string
  descripcion: string
  categoria?: string
  valorAdquisicion: number
  fechaAdquisicion?: string
}
