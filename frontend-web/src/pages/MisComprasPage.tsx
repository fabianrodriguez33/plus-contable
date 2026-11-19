import { FormEvent, useCallback, useEffect, useState } from 'react'
import { clienteIdActual } from '../services/authService'
import { mensajeError } from '../services/apiClient'
import { proponerClasificacion, listarCompras, type Compra, type TipoCompra } from '../services/compraService'
import { TIPOS_COMPRA, DESCRIPCION_MAXIMO, ETIQUETA_ESTADO_CLIENTE, nombrePeriodo, ordenar, validarClasificacion, type ErroresClasificacion } from '../utils/compras'
import { fechaCorta } from '../utils/programacion'
import { soles } from '../utils/societario'
import Button from '../components/Button'
import Card from '../components/Card'
import { Select } from '../components/Input'

const COLOR_ESTADO = {
  SIN_CLASIFICAR: 'bg-amber-50 text-amber-700',
  PROPUESTA: 'bg-sky-50 text-sky-700',
  VALIDADA: 'bg-emerald-50 text-emerald-700',
} as const

function FormularioProponer({ compra, onListo, onError }: { compra: Compra; onListo: () => void; onError: (m: string) => void }) {
  const [tipo, setTipo] = useState<string>(compra.tipo ?? '')
  const [descripcion, setDescripcion] = useState(compra.descripcion ?? '')
  const [errores, setErrores] = useState<ErroresClasificacion>({})
  const [guardando, setGuardando] = useState(false)

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    onError('')
    const v = validarClasificacion(tipo, descripcion)
    setErrores(v)
    if (v.tipo || v.descripcion) return
    setGuardando(true)
    try {
      await proponerClasificacion(compra.clienteId, compra.id, tipo as TipoCompra, descripcion.trim())
      onListo()
    } catch (err) {
      onError(mensajeError(err))
      setGuardando(false)
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="mt-3 space-y-3 border-t border-slate-200 pt-3" aria-label={`Proponer clasificación de ${compra.comprobante}`}>
      <div>
        <Select label="Tipo de compra" value={tipo} onChange={(e) => setTipo(e.target.value)}>
          <option value="">Elija…</option>
          {Object.entries(TIPOS_COMPRA).map(([id, texto]) => <option key={id} value={id}>{texto}</option>)}
        </Select>
        {errores.tipo && <p role="alert" className="mt-1 text-xs text-peligro-texto">{errores.tipo}</p>}
      </div>
      <div>
        <label htmlFor={`desc-${compra.id}`} className="mb-1 block text-sm font-medium text-tinta">Descripción</label>
        <textarea id={`desc-${compra.id}`} value={descripcion} maxLength={DESCRIPCION_MAXIMO + 1} rows={2}
          onChange={(e) => setDescripcion(e.target.value)} placeholder="Qué se compró y para qué"
          className="w-full rounded-campo border border-slate-300 bg-white/90 px-3 py-2 text-sm focus:border-acento focus:outline-none" />
        {errores.descripcion && <p role="alert" className="mt-1 text-xs text-peligro-texto">{errores.descripcion}</p>}
      </div>
      <Button type="submit" disabled={guardando}>{guardando ? 'Guardando...' : 'Guardar propuesta'}</Button>
    </form>
  )
}

/**
 * HU-25 · T210 y T213: el cliente ve las compras de su empresa que el contador cargó, las que debe clasificar primero, y <b>propone</b>
 * cómo clasificarlas («mercadería o gasto» o «activo fijo», con descripción). La propuesta queda pendiente de validación del contador y
 * el cliente puede cambiarla hasta que la valide. Solo ve las suyas: la empresa sale de su token y el servidor vuelve a verificarla.
 * La alerta del fin de mes es solo del contador: aquí no se muestra.
 */
export default function MisComprasPage() {
  const clienteId = clienteIdActual()
  const [compras, setCompras] = useState<Compra[] | null>(null)
  const [error, setError] = useState('')
  const [abierta, setAbierta] = useState<number | null>(null)

  const cargar = useCallback(() => {
    if (clienteId === null) return
    listarCompras(clienteId).then(setCompras).catch((e) => { setError(mensajeError(e)); setCompras([]) })
  }, [clienteId])

  useEffect(cargar, [cargar])

  if (clienteId === null) {
    return <Card title="Mis compras"><p role="alert" className="text-sm text-red-600">Su cuenta no está vinculada a una empresa. Comuníquese con el estudio.</p></Card>
  }
  const lista = ordenar(compras ?? [], 'cliente')

  return (
    <Card title="Mis compras">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      {compras === null ? (
        <p className="text-sm text-slate-500">Cargando...</p>
      ) : lista.length === 0 ? (
        <p className="text-sm text-slate-500">Todavía no hay compras registradas por su contador.</p>
      ) : (
        <ul className="space-y-3">
          {lista.map((c) => (
            <li key={c.id} className="hoja rounded-campo p-4 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-semibold">{c.proveedorRazonSocial ?? c.proveedorRuc}</span>
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${COLOR_ESTADO[c.estado]}`}>{ETIQUETA_ESTADO_CLIENTE[c.estado]}</span>
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-2 md:grid-cols-4">
                <div><dt className="text-slate-500">Comprobante</dt><dd>{c.comprobante}</dd></div>
                <div><dt className="text-slate-500">Fecha</dt><dd>{fechaCorta(c.fechaComprobante)}</dd></div>
                <div><dt className="text-slate-500">Importe</dt><dd>{soles(c.importe)}</dd></div>
                <div><dt className="text-slate-500">Mes</dt><dd>{nombrePeriodo(c.periodo)}</dd></div>
              </dl>
              {c.tipo && (
                <p className="mt-2 text-slate-700">
                  <span className="text-slate-500">{c.estado === 'VALIDADA' ? 'Clasificación: ' : 'Su propuesta: '}</span>{TIPOS_COMPRA[c.tipo]}
                  <span className="block"><span className="text-slate-500">Descripción: </span>{c.descripcion}</span>
                </p>
              )}
              {c.estado !== 'VALIDADA' && (abierta === c.id ? (
                <FormularioProponer compra={c} onError={setError} onListo={() => { setAbierta(null); setError(''); cargar() }} />
              ) : (
                <div className="mt-3">
                  <Button variant="secondary" onClick={() => { setError(''); setAbierta(c.id) }}>
                    {c.estado === 'PROPUESTA' ? 'Cambiar propuesta' : 'Proponer clasificación'}
                  </Button>
                </div>
              ))}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
