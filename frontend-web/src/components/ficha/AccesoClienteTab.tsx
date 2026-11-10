import { FormEvent, useEffect, useState } from 'react'
import { Copy, EyeOff, KeyRound, TriangleAlert } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { crearCuentaCliente } from '../../services/cuentaClienteService'
import Button from '../Button'
import Input from '../Input'

/** La contraseña temporal se oculta sola pasado este tiempo, aunque nadie pulse «ocultar». */
export const SEGUNDOS_VISIBLE = 120
const CORREO_MAXIMO = 60
const FORMATO_CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

/** Las mismas reglas del servidor, para avisar antes de enviar. */
export function validarCorreo(correo: string): string | null {
  const c = correo.trim()
  if (!c) return 'Escriba el correo con el que entrará el cliente'
  if (c.length > CORREO_MAXIMO) return `El correo puede tener hasta ${CORREO_MAXIMO} caracteres`
  if (!FORMATO_CORREO.test(c)) return 'El correo no tiene un formato válido'
  return null
}

/**
 * HU-37 · Tarea 13: el contador crea el acceso del cliente con su correo. El servidor devuelve una contraseña temporal que se
 * muestra UNA sola vez: vive solo en el estado de este componente y se borra al ocultarla, al pasar el tiempo, al cambiar de
 * cliente o al salir de la pestaña. No va al aviso de la ficha, ni al almacenamiento del navegador.
 */
export default function AccesoClienteTab({ clienteId, setError, setOk }: Props) {
  const [correo, setCorreo] = useState('')
  const [errorCorreo, setErrorCorreo] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [creada, setCreada] = useState<{ correo: string; password: string } | null>(null)
  const [accesoCreadoPara, setAccesoCreadoPara] = useState<string | null>(null)
  const [copiada, setCopiada] = useState(false)

  // otro cliente: nada de lo del anterior queda en pantalla
  useEffect(() => {
    setCreada(null)
    setAccesoCreadoPara(null)
    setCorreo('')
    setErrorCorreo(null)
    setCopiada(false)
  }, [clienteId])

  // se oculta sola
  useEffect(() => {
    if (!creada) return
    const temporizador = window.setTimeout(() => setCreada(null), SEGUNDOS_VISIBLE * 1000)
    return () => window.clearTimeout(temporizador)
  }, [creada])

  // al salir de la pestaña el estado desaparece con el componente

  const crear = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    const problema = validarCorreo(correo)
    setErrorCorreo(problema)
    if (problema) return
    setGuardando(true)
    try {
      const r = await crearCuentaCliente(clienteId, correo.trim())
      setCreada({ correo: r.correo, password: r.passwordTemporal })
      setAccesoCreadoPara(r.correo)
      setCorreo('')
      setCopiada(false)
      setOk(`Acceso creado para ${r.correo}`) // la contraseña no va en este aviso
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  const ocultar = () => {
    setCreada(null)
    setCopiada(false)
  }

  const copiar = async () => {
    if (!creada) return
    try {
      await navigator.clipboard.writeText(creada.password)
      setCopiada(true)
    } catch {
      setError('No se pudo copiar: anótela a mano antes de ocultarla')
    }
  }

  return (
    <div className="space-y-5">
      {creada && (
        <section aria-label="Contraseña temporal" className="rounded-lg border border-amber-300 bg-amber-50 p-4">
          <p className="flex items-start gap-2 text-sm font-medium text-amber-900">
            <TriangleAlert size={18} className="mt-0.5 shrink-0" />
            Esta contraseña se muestra una sola vez y no se podrá volver a ver. Anótela y entréguela al cliente por su medio:
            el sistema no envía correos.
          </p>
          <p className="mt-3 text-sm text-slate-700">
            Correo de ingreso: <strong>{creada.correo}</strong>
          </p>
          <p className="mt-2 text-sm text-slate-700">Contraseña temporal:</p>
          <p data-testid="password-temporal" className="mt-1 select-all rounded-md bg-white px-3 py-2 font-mono text-lg tracking-wider text-slate-900">
            {creada.password}
          </p>
          <p className="mt-2 text-xs text-slate-600">
            El cliente debe cambiarla en su primer ingreso y la temporal solo sirve una vez. Se oculta sola en {SEGUNDOS_VISIBLE / 60} minutos.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button type="button" variant="secondary" onClick={copiar}>
              <Copy size={16} /> {copiada ? 'Copiada' : 'Copiar'}
            </Button>
            <Button type="button" onClick={ocultar}>
              <EyeOff size={16} /> Ya la anoté: ocultar
            </Button>
          </div>
        </section>
      )}

      {accesoCreadoPara && !creada ? (
        <p role="status" className="text-sm text-slate-600">
          El acceso de este cliente ya fue creado con el correo <strong>{accesoCreadoPara}</strong>. La contraseña temporal ya no se
          puede volver a ver.
        </p>
      ) : !creada && (
        <form onSubmit={crear} noValidate className="max-w-xl space-y-4">
          <p className="text-sm text-slate-600">
            Cada empresa tiene una sola cuenta de acceso. El cliente entra con este correo y una contraseña temporal que
            deberá cambiar en su primer ingreso.
          </p>
          <Input label="Correo del cliente" type="text" inputMode="email" value={correo} autoComplete="off" error={errorCorreo}
            onChange={(e) => setCorreo(e.target.value)} />
          <Button type="submit" disabled={guardando}>
            <KeyRound size={16} /> {guardando ? 'Creando…' : 'Crear acceso'}
          </Button>
        </form>
      )}
    </div>
  )
}
