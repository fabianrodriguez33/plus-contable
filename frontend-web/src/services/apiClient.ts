import axios, { AxiosInstance } from 'axios'

export const TOKEN_KEY = 'pc_token'
export const USER_KEY = 'pc_user'

function crear(baseURL: string): AxiosInstance {
  const instancia = axios.create({ baseURL })

  // Adjunta automaticamente el JWT almacenado en localStorage
  instancia.interceptors.request.use((config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  // Sesion expirada o token invalido: limpia y vuelve al login (excepto en el propio login)
  instancia.interceptors.response.use(
    (r) => r,
    (error) => {
      const url: string = error.config?.url ?? ''
      if (error.response?.status === 401 && !url.includes('/auth/login')) {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
        window.location.href = '/login'
      }
      return Promise.reject(error)
    },
  )
  return instancia
}

export const authApi = crear(import.meta.env.VITE_AUTH_URL ?? 'http://localhost:8081')
export const tributarioApi = crear(import.meta.env.VITE_TRIBUTARIO_URL ?? 'http://localhost:8082')

export function mensajeError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.data?.mensaje) return error.response.data.mensaje as string
    if (!error.response) return 'No se pudo conectar con el servidor'
    return `Error ${error.response.status}`
  }
  return 'Error inesperado'
}
