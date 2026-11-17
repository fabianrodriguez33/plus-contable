import { FormEvent, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertCircle, CheckCircle2, Loader2, Mail } from 'lucide-react'
import { mensajeError } from '../services/apiClient'
import { solicitarRestablecimiento } from '../services/cuentaClienteService'
import Button from '../components/Button'
import Input from '../components/Input'

/**
 * HU-41 · Tarea 229: el cliente que olvidó su contraseña la pide con su correo. El estudio la atiende y le entrega una nueva
 * contraseña temporal (el sistema no envía correos). El mensaje es el mismo exista o no la cuenta (criterio 2).
 */
export default function RestablecerPage() {
  const [correo, setCorreo] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setEnviando(true)
    try {
      setMensaje(await solicitarRestablecimiento(correo.trim()))
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-900 to-brand-600 p-4">
      <form onSubmit={enviar} className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-2xl">
        <h1 className="text-xl font-bold text-brand-900">¿Olvidaste tu contraseña?</h1>
        {mensaje ? (
          <div role="status" className="mt-4 flex items-start gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            <CheckCircle2 size={16} className="mt-0.5 shrink-0" /> {mensaje}
          </div>
        ) : (
          <>
            <p className="mt-2 text-sm text-slate-600">
              Escribe el correo con el que ingresas. El estudio recibirá tu pedido y te entregará una nueva contraseña temporal.
            </p>
            {error && (
              <div role="alert" className="mt-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                <AlertCircle size={16} className="mt-0.5 shrink-0" /> {error}
              </div>
            )}
            <div className="relative mt-4">
              <Input label="Correo" type="email" value={correo} onChange={(e) => setCorreo(e.target.value)}
                autoFocus required maxLength={60} autoComplete="username" className="pl-9" />
              <Mail size={16} className="absolute left-3 top-[34px] text-slate-400" />
            </div>
            <Button type="submit" disabled={enviando} className="mt-6 w-full">
              {enviando && <Loader2 size={16} className="animate-spin" />}
              {enviando ? 'Enviando...' : 'Pedir nueva contraseña'}
            </Button>
          </>
        )}
        <Link to="/login" className="mt-4 block text-center text-sm font-medium text-brand-600 hover:underline">Volver al ingreso</Link>
      </form>
    </div>
  )
}
