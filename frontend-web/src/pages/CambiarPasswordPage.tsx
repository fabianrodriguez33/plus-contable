import { FormEvent, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { AlertCircle, Loader2, Lock } from 'lucide-react'
import { cambiarPassword, cerrarSesion, debeCambiarPassword, usuarioActual } from '../services/authService'
import { mensajeError } from '../services/apiClient'
import { PASSWORD_MAXIMO, PASSWORD_MINIMO, validarPasswordNueva } from '../utils/password'
import Button from '../components/Button'
import Input from '../components/Input'

/**
 * HU-37: primer ingreso con la contraseña temporal. Es lo único que el cliente puede usar hasta cambiarla: no lleva el menú
 * del sistema y cualquier otra ruta lo trae de vuelta aquí (PrivateRoute).
 */
export default function CambiarPasswordPage() {
  const navigate = useNavigate()
  const [nueva, setNueva] = useState('')
  const [repetida, setRepetida] = useState('')
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  // sin sesión o sin cambio pendiente no hay nada que hacer aquí: PrivateRoute decide a dónde ir (login o inicio)
  if (!debeCambiarPassword()) return <Navigate to="/" replace />

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    const problema = validarPasswordNueva(nueva, repetida, usuarioActual()?.username ?? '')
    setError(problema ?? '')
    if (problema) return
    setCargando(true)
    try {
      await cambiarPassword(nueva)
      navigate('/', { replace: true })
    } catch (err) {
      setError(mensajeError(err))
      setCargando(false)
    }
  }

  const salir = async () => {
    await cerrarSesion()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-900 to-brand-600 p-4">
      <form onSubmit={enviar} noValidate className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold text-brand-900">Cambie su contraseña</h1>
          <p className="mt-1 text-sm text-slate-500">
            Entró con una contraseña temporal. Para usar el sistema debe elegir una contraseña propia de {PASSWORD_MINIMO} a {PASSWORD_MAXIMO} caracteres.
          </p>
        </div>
        {error && (
          <div role="alert" className="mb-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <AlertCircle size={16} className="mt-0.5 shrink-0" /> {error}
          </div>
        )}
        <div className="space-y-4">
          <Input label="Contraseña nueva" type="password" value={nueva} onChange={(e) => setNueva(e.target.value)}
            autoFocus autoComplete="new-password" />
          <Input label="Repita la contraseña nueva" type="password" value={repetida} onChange={(e) => setRepetida(e.target.value)}
            autoComplete="new-password" />
        </div>
        <Button type="submit" disabled={cargando} className="mt-6 w-full">
          {cargando ? <Loader2 size={16} className="animate-spin" /> : <Lock size={16} />}
          {cargando ? 'Guardando...' : 'Cambiar contraseña'}
        </Button>
        <button type="button" onClick={salir} className="mt-3 w-full text-center text-sm text-slate-500 hover:text-slate-800">
          Salir
        </button>
      </form>
    </div>
  )
}
