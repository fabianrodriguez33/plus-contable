import { Navigate, Outlet } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { debeCambiarPassword, isAuthenticated, logout } from '../services/authService'
import { TOKEN_KEY } from '../services/apiClient'
import { jwtExpirationMs } from '../services/jwtSession'

export default function PrivateRoute() {
  const [authenticated, setAuthenticated] = useState(isAuthenticated)

  useEffect(() => {
    if (!authenticated) return
    let timer: number
    const check = () => {
      window.clearTimeout(timer)
      const token = localStorage.getItem(TOKEN_KEY)
      if (!token || !isAuthenticated()) {
        logout()
        setAuthenticated(false)
        return
      }
      const remaining = jwtExpirationMs(token)! - Date.now()
      timer = window.setTimeout(check, Math.min(remaining, 2_147_483_647))
    }
    check()
    document.addEventListener('visibilitychange', check)
    return () => {
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', check)
    }
  }, [authenticated])

  if (!authenticated) return <Navigate to="/login" replace />
  // HU-37: con la contraseña temporal no se puede entrar a nada más hasta cambiarla
  return debeCambiarPassword() ? <Navigate to="/cambiar-password" replace /> : <Outlet />
}
