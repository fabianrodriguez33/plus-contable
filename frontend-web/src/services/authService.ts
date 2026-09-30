import { authApi, TOKEN_KEY, USER_KEY } from './apiClient'

export interface Usuario {
  id: number
  username: string
  nombreCompleto: string
  roles: string[]
}

interface LoginResponse {
  token: string
  tipo: string
  expiraEnSegundos: number
  usuario: Usuario
}

export async function login(username: string, password: string): Promise<Usuario> {
  const { data } = await authApi.post<LoginResponse>('/api/auth/login', { username, password })
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data.usuario))
  return data.usuario
}

export function logout(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isAuthenticated(): boolean {
  return !!localStorage.getItem(TOKEN_KEY)
}

export function usuarioActual(): Usuario | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as Usuario) : null
}
