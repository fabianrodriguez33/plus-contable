import { FormEvent, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Loader2, Lock, User } from 'lucide-react'
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
      navigate('/', { replace: true })
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setCargando(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center p-4">
      <form onSubmit={enviar} className="hoja w-full max-w-md rounded-panel p-8 md:p-10">
        <div className="mb-8 flex items-start gap-4 border-b border-tinta/15 pb-6">
          <span aria-hidden="true" className="sello bg-white font-serif text-xl font-bold text-acento">PC</span>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-tinta">Plus Contable</h1>
            <p className="mt-1 text-sm text-slate-500">V&amp;F Asesores Empresarial — Control tributario MYPE</p>
          </div>
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
        <Link to="/restablecer" className="mt-4 block text-center text-sm font-medium text-acento-oscuro hover:underline">
          ¿Olvidaste tu contraseña?
        </Link>
      </form>
    </main>
  )
}
