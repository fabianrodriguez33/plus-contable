import { authApi } from './apiClient'

// ---- HU-37 ----
export interface CuentaCreada {
  usuarioId: number
  correo: string
  clienteId: number
  /** Solo viene en la respuesta de creación o de restablecimiento: el servidor no la vuelve a entregar ni la guarda en claro. */
  passwordTemporal: string
  /** Hasta cuándo sirve la temporal (48 horas desde que se creó o se restableció), en hora del servidor. */
  venceEn?: string | null
}

/** El contador crea la cuenta del cliente con su correo; la contraseña temporal sale una única vez. */
export const crearCuentaCliente = (clienteId: number, correo: string) =>
  authApi.post<CuentaCreada>(`/api/auth/clientes/${clienteId}/cuenta`, { correo }).then((r) => r.data)

/**
 * El contador genera otra contraseña temporal para la cuenta existente («Restablecer acceso»): la anterior y las sesiones del
 * cliente dejan de valer y la nueva sale una única vez. Sin cuerpo: la empresa va en la ruta.
 */
export const restablecerAccesoCliente = (clienteId: number) =>
  authApi.post<CuentaCreada>(`/api/auth/clientes/${clienteId}/cuenta/restablecer`).then((r) => r.data)
