import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { programarMes, type ProgramacionMes } from '../services/programacionService'
import { mensajeError } from '../services/apiClient'
import Button from '../components/Button'
import { Input } from '../components/Input'
import { etiquetaTipo, fechaCorta, mesAnterior, periodoValido } from '../utils/programacion'

interface Props {
  /** Solo para las pruebas: fecha de hoy. */
  hoy?: Date
}

/** HU-07 · Tarea 55: programar las obligaciones de un mes con la fecha del cronograma SUNAT. */
export default function ProgramacionPage({ hoy = new Date() }: Props) {
  const clienteId = Number(useParams().clienteId)
  const [periodo, setPeriodo] = useState(() => mesAnterior(hoy))
  const [resultado, setResultado] = useState<ProgramacionMes | null>(null)
  const [error, setError] = useState('')
  const [programando, setProgramando] = useState(false)

  const programar = async () => {
    setProgramando(true)
    setError('')
    setResultado(null)
    try {
      setResultado(await programarMes(clienteId, periodo))
    } catch (e) {
      setError(mensajeError(e))
    } finally {
      setProgramando(false)
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        Se programan IGV, Renta y PLAME con la fecha del cronograma SUNAT del mes elegido. EsSalud, la retención de quinta
        categoría y AFP no se generan todavía: el resultado indica el motivo de cada una. Programar dos veces el mismo
        mes no duplica nada.
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <Input label="Mes a programar" type="month" value={periodo}
          onChange={(e) => { setPeriodo(e.target.value); setResultado(null); setError('') }} />
        <Button disabled={programando || !periodoValido(periodo)} onClick={programar}>
          {programando ? 'Programando…' : 'Programar'}
        </Button>
      </div>

      {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}

      {resultado && (
        <div role="status" aria-label="Resultado de la programación" className="space-y-4 text-sm">
          <p className="text-slate-700">
            Mes {resultado.periodo}: vencimiento el <strong>{fechaCorta(resultado.fechaVencimiento)}</strong>.
          </p>

          <section aria-label="Creadas">
            <h3 className="mb-1 font-semibold text-green-700">Creadas ({resultado.creadas.length})</h3>
            {resultado.creadas.length === 0 ? (
              <p className="text-slate-500">No se creó ninguna obligación nueva.</p>
            ) : (
              <ul className="list-disc space-y-1 pl-6 text-slate-700">
                {resultado.creadas.map((o) => (
                  <li key={o.id}>{etiquetaTipo(o.tipoObligacion)}: vence el {fechaCorta(o.fechaVencimiento)}</li>
                ))}
              </ul>
            )}
          </section>

          <section aria-label="Ya existentes">
            <h3 className="mb-1 font-semibold text-slate-700">Ya existentes ({resultado.yaExistian.length})</h3>
            {resultado.yaExistian.length === 0 ? (
              <p className="text-slate-500">Ninguna estaba programada.</p>
            ) : (
              <ul className="list-disc space-y-1 pl-6 text-slate-700">
                {resultado.yaExistian.map((t) => <li key={t}>{etiquetaTipo(t)}</li>)}
              </ul>
            )}
          </section>

          <section aria-label="No generadas">
            <h3 className="mb-1 font-semibold text-amber-700">No generadas ({resultado.noGeneradas.length})</h3>
            {resultado.noGeneradas.length === 0 ? (
              <p className="text-slate-500">Se generaron todos los tipos.</p>
            ) : (
              <ul className="list-disc space-y-1 rounded-lg border border-amber-200 bg-amber-50 p-3 pl-7 text-amber-800">
                {resultado.noGeneradas.map((n) => (
                  <li key={n.tipo}><strong>{etiquetaTipo(n.tipo)}:</strong> {n.motivo}</li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </div>
  )
}
