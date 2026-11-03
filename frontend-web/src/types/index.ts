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

export type ResultadoConsultaRuc = 'ENCONTRADO' | 'NO_ENCONTRADO' | 'NO_DISPONIBLE' | 'FUERA_DE_COBERTURA'

/** Sugerencia de datos de SUNAT para el alta. No se guarda por sí sola: el contador la revisa y guarda el formulario. */
export interface ConsultaRuc {
  resultado: ResultadoConsultaRuc
  mensaje: string
  datos: {
    ruc: string
    razonSocial: string
    direccion?: string | null
    condicionHabido?: 'HABIDO' | 'NO_HABIDO' | null
    estado?: string | null
    condicion?: string | null
    fuente?: string | null
    /** yyyy-MM-dd, fecha a la que corresponden los datos según el proveedor */
    fechaDatos?: string | null
  } | null
  avisos: string[]
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
