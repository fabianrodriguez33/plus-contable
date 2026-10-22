import { FormEvent, ReactNode, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { listarTrabajadores, registrarTrabajador, Trabajador } from '../services/expedienteService'

const VACIO: Trabajador = {
  nombreCompleto: '',
  tipoDocumento: 'DNI',
  numeroDocumento: '',
  extranjero: false,
  sistemaPensionario: 'ONP',
  fechaIngreso: '',
}

const input = 'w-full border rounded px-2 py-1 aria-[invalid=true]:border-red-600'

function Campo({ id, label, invalido, children }: { id: string; label: string; invalido: boolean; children: ReactNode }) {
  return (
    <label htmlFor={id} className="block text-sm">
      <span className={invalido ? 'text-red-600 font-medium' : ''}>{label}</span>
      {children}
    </label>
  )
}

/** HU-03 · Tarea 33: formulario de trabajador y lista con sistema pensionario. */
export default function TrabajadoresPage() {
  const clienteId = Number(useParams().clienteId)
  const [trabajadores, setTrabajadores] = useState<Trabajador[]>([])
  const [form, setForm] = useState<Trabajador>(VACIO)
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    listarTrabajadores(clienteId).then(setTrabajadores).catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  // el backend devuelve "Faltan o son inválidos: campo1, campo2"; se resaltan los campos nombrados
  const invalido = (campo: keyof Trabajador) => error.includes(campo)

  function set<K extends keyof Trabajador>(campo: K, valor: Trabajador[K]) {
    setForm((f) => ({ ...f, [campo]: valor }))
  }

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setGuardando(true)
    setError('')
    try {
      // vacíos como null para que el backend los reporte como faltantes
      const limpio = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v === '' ? null : v])) as Trabajador
      const nuevo = await registrarTrabajador(clienteId, limpio)
      setTrabajadores((ts) => [...ts, nuevo])
      setForm(VACIO)
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  const props = (campo: keyof Trabajador) => ({
    id: campo,
    className: input,
    'aria-invalid': invalido(campo),
  })

  return (
    <section className="space-y-6">
      <h1 className="text-xl font-semibold">Expediente laboral</h1>

      <form onSubmit={guardar} noValidate className="grid gap-3 sm:grid-cols-2 border rounded p-4">
        <fieldset className="sm:col-span-2 flex gap-4">
          <legend className="text-sm mb-1">Tipo de trabajador</legend>
          <label><input type="radio" checked={!form.extranjero}
            onChange={() => setForm((f) => ({ ...f, extranjero: false, tipoDocumento: 'DNI' }))} /> Nacional</label>
          <label><input type="radio" checked={form.extranjero}
            onChange={() => setForm((f) => ({ ...f, extranjero: true, tipoDocumento: 'CE' }))} /> Extranjero</label>
        </fieldset>

        <Campo id="nombreCompleto" label="Nombre completo *" invalido={invalido('nombreCompleto')}>
          <input {...props('nombreCompleto')} value={form.nombreCompleto} onChange={(e) => set('nombreCompleto', e.target.value)} />
        </Campo>
        <Campo id="tipoDocumento" label="Tipo de documento *" invalido={invalido('tipoDocumento')}>
          <select {...props('tipoDocumento')} value={form.tipoDocumento}
            onChange={(e) => set('tipoDocumento', e.target.value as Trabajador['tipoDocumento'])}>
            {!form.extranjero && <option value="DNI">DNI</option>}
            <option value="CE">Carné de extranjería</option>
            <option value="PASAPORTE">Pasaporte</option>
            <option value="PTP">PTP</option>
          </select>
        </Campo>
        <Campo id="numeroDocumento" label="Número de documento *" invalido={invalido('numeroDocumento')}>
          <input {...props('numeroDocumento')} value={form.numeroDocumento} onChange={(e) => set('numeroDocumento', e.target.value)} />
        </Campo>
        <Campo id="fechaIngreso" label="Fecha de ingreso *" invalido={invalido('fechaIngreso')}>
          <input type="date" {...props('fechaIngreso')} value={form.fechaIngreso} onChange={(e) => set('fechaIngreso', e.target.value)} />
        </Campo>
        <Campo id="sistemaPensionario" label="Sistema pensionario *" invalido={invalido('sistemaPensionario')}>
          <select {...props('sistemaPensionario')} value={form.sistemaPensionario}
            onChange={(e) => set('sistemaPensionario', e.target.value as Trabajador['sistemaPensionario'])}>
            <option value="ONP">ONP</option>
            <option value="AFP">AFP</option>
          </select>
        </Campo>
        {form.sistemaPensionario === 'AFP' && (
          <Campo id="nombreAfp" label="Nombre de la AFP *" invalido={invalido('nombreAfp')}>
            <input {...props('nombreAfp')} value={form.nombreAfp ?? ''} onChange={(e) => set('nombreAfp', e.target.value)} />
          </Campo>
        )}
        <Campo id="remuneracion" label="Remuneración (S/)" invalido={invalido('remuneracion')}>
          <input type="number" min="0" step="0.01" {...props('remuneracion')} value={form.remuneracion ?? ''}
            onChange={(e) => set('remuneracion', e.target.value === '' ? undefined : Number(e.target.value))} />
        </Campo>

        {form.extranjero && (
          <fieldset className="sm:col-span-2 grid gap-3 sm:grid-cols-2 border-t pt-3">
            <legend className="text-sm font-medium">Datos de extranjería y contrato</legend>
            <Campo id="nacionalidad" label="Nacionalidad *" invalido={invalido('nacionalidad')}>
              <input {...props('nacionalidad')} value={form.nacionalidad ?? ''} onChange={(e) => set('nacionalidad', e.target.value)} />
            </Campo>
            <Campo id="tipoContrato" label="Tipo de contrato *" invalido={invalido('tipoContrato')}>
              <input {...props('tipoContrato')} value={form.tipoContrato ?? ''} onChange={(e) => set('tipoContrato', e.target.value)} />
            </Campo>
            <Campo id="fechaInicioContrato" label="Inicio de contrato *" invalido={invalido('fechaInicioContrato')}>
              <input type="date" {...props('fechaInicioContrato')} value={form.fechaInicioContrato ?? ''}
                onChange={(e) => set('fechaInicioContrato', e.target.value)} />
            </Campo>
            <Campo id="fechaFinContrato" label="Fin de contrato" invalido={invalido('fechaFinContrato')}>
              <input type="date" {...props('fechaFinContrato')} value={form.fechaFinContrato ?? ''}
                onChange={(e) => set('fechaFinContrato', e.target.value)} />
            </Campo>
          </fieldset>
        )}

        {error && <p role="alert" className="sm:col-span-2 text-red-600">{error}</p>}
        <div className="sm:col-span-2">
          <button type="submit" disabled={guardando} className="bg-blue-700 text-white rounded px-4 py-2 disabled:opacity-50">
            {guardando ? 'Guardando…' : 'Registrar trabajador'}
          </button>
        </div>
      </form>

      <div className="overflow-x-auto">
        <table className="w-full text-left">
          <thead>
            <tr><th>Nombre</th><th>Documento</th><th>Tipo</th><th>Sistema pensionario</th></tr>
          </thead>
          <tbody>
            {trabajadores.map((t) => (
              <tr key={t.id}>
                <td>{t.nombreCompleto}</td>
                <td>{t.tipoDocumento} {t.numeroDocumento}</td>
                <td>{t.extranjero ? `Extranjero · ${t.nacionalidad}` : 'Nacional'}</td>
                <td>{t.sistemaPensionario}{t.nombreAfp ? ` · ${t.nombreAfp}` : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
