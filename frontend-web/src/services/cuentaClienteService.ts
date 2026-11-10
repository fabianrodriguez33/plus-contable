import { authApi } from './apiClient'

// ---- HU-37 ----
export interface CuentaCreada {
  usuarioId: number
  correo: string
  clienteId: number
  /** Solo viene en la respuesta de creación: el servidor no la vuelve a entregar ni la guarda en claro. */
  passwordTemporal: string
}

/** El contador crea la cuenta del cliente con su correo; la contraseña temporal sale una única vez. */
export const crearCuentaCliente = (clienteId: number, correo: string) =>
  authApi.post<CuentaCreada>(`/api/auth/clientes/${clienteId}/cuenta`, { correo }).then((r) => r.data)
