import { FormEvent, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mensajeError } from '../services/apiClient'
import { cambiarVencimiento, listarObligaciones, Obligacion } from '../services/obligacionService'

const input = 'border rounded px-2 py-1'

/** HU-10 · Tarea 67: cambiar la fecha de vencimiento de una o varias obligaciones a la vez. */
export default function VencimientosPage() {
  const clienteId = Number(useParams().clienteId)
  const [obligaciones, setObligaciones] = useState<Obligacion[]>([])
  const [seleccion, setSeleccion] = useState<Set<number>>(new Set())
  const [nuevaFecha, setNuevaFecha] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    listarObligaciones(clienteId).then(setObligaciones).catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  const pendientes = obligaciones.filter((o) => o.estado === 'PENDIENTE')
  const todas = pendientes.length > 0 && pendientes.every((o) => seleccion.has(o.id))

  function alternar(id: number) {
    setSeleccion((s) => {
      const n = new Set(s)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })
  }

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setOk('')
    if (!nuevaFecha) {
      setError('Faltan o son inválidos: nuevaFecha')
      return
    }
    setGuardando(true)
    setError('')
    try {
      const cambiadas = await cambiarVencimiento([...seleccion], nuevaFecha)
      const porId = new Map(cambiadas.map((o) => [o.id, o]))
      setObligaciones((os) => os.map((o) => porId.get(o.id) ?? o))
      setOk(`Vencimiento actualizado en ${cambiadas.length} obligación(es)`)
      setSeleccion(new Set())
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold">Variar fecha de vencimiento</h1>

      <form onSubmit={guardar} noValidate className="space-y-3">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr>
                <th>
                  <input type="checkbox" aria-label="Seleccionar todas las pendientes" checked={todas}
                    onChange={() => setSeleccion(todas ? new Set() : new Set(pendientes.map((o) => o.id)))} />
                </th>
                <th>Periodo</th><th>Vencimiento</th><th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {obligaciones.map((o) => (
                <tr key={o.id}>
                  <td>
                    <input type="checkbox" aria-label={`Seleccionar periodo ${o.periodo.slice(0, 7)}`}
                      disabled={o.estado !== 'PENDIENTE'} checked={seleccion.has(o.id)} onChange={() => alternar(o.id)} />
                  </td>
                  <td>{o.periodo.slice(0, 7)}</td>
                  <td>{o.fechaVencimiento}</td>
                  <td>{o.estado}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex flex-wrap items-end gap-3">
          <label className="text-sm">
            Nueva fecha de vencimiento *
            <input type="date" className={`${input} block`} value={nuevaFecha} onChange={(e) => setNuevaFecha(e.target.value)} />
          </label>
          <button type="submit" disabled={guardando || seleccion.size === 0}
            className="bg-blue-700 text-white rounded px-4 py-2 disabled:opacity-50">
            {guardando ? 'Guardando…' : `Cambiar ${seleccion.size || ''} seleccionada(s)`}
          </button>
        </div>
        {error && <p role="alert" className="text-red-600">{error}</p>}
        {ok && <p role="status" className="text-green-700">{ok}</p>}
      </form>
    </section>
  )
}
