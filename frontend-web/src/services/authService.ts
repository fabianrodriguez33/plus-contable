import { authApi, TOKEN_KEY, USER_KEY } from './apiClient'
import type { Usuario } from '../types'
import { isJwtActive, jwtRequiereCambioPassword } from './jwtSession'

interface LoginResponse {
  token: string
  tipo: string
  expiraEnSegundos: number
  usuario: Usuario
  /** HU-37: el token es el restringido del primer ingreso y solo sirve para cambiar la contraseña. */
  debeCambiarPassword?: boolean
}

export async function login(username: string, password: string): Promise<Usuario> {
  const { data } = await authApi.post<LoginResponse>('/api/auth/login', { username, password })
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data.usuario))
  return data.usuario
}

/** HU-37: verdadero si la sesión guardada es la del primer ingreso con la contraseña temporal. */
export function debeCambiarPassword(): boolean {
  const token = localStorage.getItem(TOKEN_KEY)
  return !!token && isJwtActive(token) && jwtRequiereCambioPassword(token)
}

/** HU-37: cambio obligatorio de la contraseña temporal; el servidor responde con la sesión normal, que reemplaza a la restringida. */
export async function cambiarPassword(passwordNueva: string): Promise<Usuario> {
  const { data } = await authApi.post<LoginResponse>('/api/auth/cambiar-password', { passwordNueva })
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data.usuario))
  return data.usuario
}

export function logout(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** HU-36: cierra la sesión en el servidor (revoca el token) y limpia la local; si el servidor no responde igual se limpia. */
export async function cerrarSesion(): Promise<void> {
  try {
    await authApi.post('/api/auth/logout')
  } catch {
    // el token vence solo; en este navegador la sesión se cierra igual
  } finally {
    logout()
  }
}

export function isAuthenticated(): boolean {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token && isJwtActive(token)) return true
  if (token) logout()
  return false
}

export function usuarioActual(): Usuario | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as Usuario) : null
}
