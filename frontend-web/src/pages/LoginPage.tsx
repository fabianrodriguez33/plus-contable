import { FormEvent, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Building2, Loader2, Lock, User } from 'lucide-react'
import { isAuthenticated, login } from '../services/authService'
import { mensajeError } from '../services/apiClient'
import Aviso from '../components/Aviso'
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
      navigate('/', { replace: true }) // con la contraseña temporal, PrivateRoute lleva a cambiarla (HU-37)
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setCargando(false)
    }
  }

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden p-4">
      {/* Luces de fondo: dan algo que desenfocar al panel de vidrio */}
      <div aria-hidden="true" className="pointer-events-none absolute -left-32 -top-32 h-96 w-96 rounded-full bg-acento/25 blur-3xl" />
      <div aria-hidden="true" className="pointer-events-none absolute -bottom-40 -right-24 h-96 w-96 rounded-full bg-acento/20 blur-3xl" />

      <form onSubmit={enviar} className="vidrio relative w-full max-w-md rounded-panel p-8 md:p-10">
        <div className="mb-8 flex flex-col items-center text-center">
          <span className="mb-4 flex h-12 w-12 items-center justify-center rounded-campo bg-acento text-white shadow-sm">
            <Building2 size={24} aria-hidden="true" />
          </span>
          <h1 className="text-2xl font-semibold tracking-tight text-tinta">Plus Contable</h1>
          <span aria-hidden="true" className="mt-3 h-1 w-10 rounded-full bg-ambar" />
          <p className="mt-3 text-sm text-slate-500">V&amp;F Asesores Empresarial — Control tributario MYPE</p>
        </div>
        {error && <Aviso tipo="peligro" className="mb-5">{error}</Aviso>}
        <div className="space-y-4">
          <div className="relative">
            <Input label="Usuario" value={username} onChange={(e) => setUsername(e.target.value)}
              autoFocus required autoComplete="username" className="pl-9" />
            <User size={16} aria-hidden="true" className="pointer-events-none absolute left-3 top-[35px] text-slate-500" />
          </div>
          <div className="relative">
            <Input label="Contraseña" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              required autoComplete="current-password" className="pl-9" />
            <Lock size={16} aria-hidden="true" className="pointer-events-none absolute left-3 top-[35px] text-slate-500" />
          </div>
        </div>
        <Button type="submit" disabled={cargando} className="mt-7 w-full py-2.5">
          {cargando && <Loader2 size={16} className="animate-spin motion-reduce:animate-none" aria-hidden="true" />}
          {cargando ? 'Ingresando...' : 'Ingresar'}
        </Button>
      </form>
    </main>
  )
}
