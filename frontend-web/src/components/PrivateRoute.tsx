import { Navigate, Outlet } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { isAuthenticated, logout } from '../services/authService'
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

  return authenticated ? <Outlet /> : <Navigate to="/login" replace />
}
