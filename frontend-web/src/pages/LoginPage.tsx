import { FormEvent, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { AlertCircle, Loader2, Lock, User } from 'lucide-react'
import { isAuthenticated, login } from '../services/authService'
import { mensajeError } from '../services/apiClient'
import Button from '../components/Button'
import Input from '../components/Input'

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
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-900 to-brand-600 p-4">
      <form onSubmit={enviar} className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold text-brand-900">Plus Contable</h1>
          <p className="mt-1 text-sm text-slate-500">V&amp;F Asesores Empresarial — Control tributario MYPE</p>
        </div>
        {error && (
          <div role="alert" className="mb-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <AlertCircle size={16} className="mt-0.5 shrink-0" /> {error}
          </div>
        )}
        <div className="space-y-4">
          <div className="relative">
            <Input label="Usuario" value={username} onChange={(e) => setUsername(e.target.value)}
              autoFocus required autoComplete="username" className="pl-9" />
            <User size={16} className="absolute left-3 top-[34px] text-slate-400" />
          </div>
          <div className="relative">
            <Input label="Contraseña" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              required autoComplete="current-password" className="pl-9" />
            <Lock size={16} className="absolute left-3 top-[34px] text-slate-400" />
          </div>
        </div>
        <Button type="submit" disabled={cargando} className="mt-6 w-full">
          {cargando && <Loader2 size={16} className="animate-spin" />}
          {cargando ? 'Ingresando...' : 'Ingresar'}
        </Button>
      </form>
    </div>
  )
}
