import { FormEvent, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { isAuthenticated, login } from '../services/authService'
import { mensajeError } from '../services/apiClient'

export default function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  if (isAuthenticated()) return <Navigate to="/" replace />

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setCargando(true)
    try {
      await login(username.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="card" onSubmit={enviar}>
        <h2>Plus Contable</h2>
        <p className="muted">V&amp;F Asesores Empresarial — Control tributario MYPE</p>
        {error && <div className="error">{error}</div>}
        <label htmlFor="username">Usuario</label>
        <input id="username" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus required />
        <label htmlFor="password">Contraseña</label>
        <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <div style={{ marginTop: 20 }}>
          <button type="submit" disabled={cargando} style={{ width: '100%' }}>
            {cargando ? 'Ingresando...' : 'Ingresar'}
          </button>
        </div>
      </form>
    </div>
  )
}
