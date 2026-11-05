import { FormEvent, useEffect, useState } from 'react'
import { AlertTriangle, Check, CheckCircle2, Info, Pencil, Plus, Save, Trash2, X } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { actualizarAporte, guardarSocios, obtenerResumenSocietario } from '../../services/clienteService'
import type { ResumenSocietario, Socio } from '../../types'
import { estadoEstructura, mensajeDiferencia, soles } from '../../utils/societario'
import Button from '../Button'
import { Input, Select } from '../Input'
import Table, { Td } from '../Table'

const socioVacio: Socio = {
  nombreCompleto: '', tipoDocumento: 'DNI', numeroDocumento: '', aporte: 0, cargo: '',
}

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

export default function SociosTab({ clienteId, setError, setOk }: Props) {
  const [socios, setSocios] = useState<Socio[]>([])
  const [guardado, setGuardado] = useState<ResumenSocietario | null>(null)
  const [nuevo, setNuevo] = useState<Socio>(socioVacio)
  const [editando, setEditando] = useState<number | null>(null)
  const [aporteEditado, setAporteEditado] = useState('')

  useEffect(() => {
    obtenerResumenSocietario(clienteId)
      .then((r) => { setSocios(r.socios); setGuardado(r) })
      .catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  const estado = estadoEstructura(socios)
  const hayCambiosSinGuardar = estado.sinGuardar > 0 || socios.length !== (guardado?.socios.length ?? 0)

  const agregar = (e: FormEvent) => {
    e.preventDefault()
    setError('')
    if (Number(nuevo.aporte) <= 0) { setError('El aporte debe ser mayor a 0: sin aporte no se puede calcular el porcentaje'); return }
    setSocios([...socios, { ...nuevo, aporte: Number(nuevo.aporte) }])
    setNuevo(socioVacio)
  }

  const persistir = async () => {
    setError('')
    try {
      const resumen = await guardarSocios(clienteId, socios)
      setSocios(resumen.socios)
      setGuardado(resumen)
      setOk(`Estructura societaria guardada. Capital social total: ${soles(resumen.capitalSocialTotal)}`)
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const empezarEdicion = (i: number) => {
    setError('')
    setEditando(i)
    setAporteEditado(String(socios[i].aporte))
  }

  const guardarAporte = async (i: number) => {
    setError('')
    const aporte = Number(aporteEditado)
    if (aporteEditado.trim() === '' || Number.isNaN(aporte) || aporte <= 0) {
      setError('El aporte debe ser un monto válido, mayor a 0')
      return
    }
    const socio = socios[i]
    try {
      if (socio.id !== undefined) {
        // socio ya guardado: el servidor guarda el aporte y recalcula el porcentaje de todos los socios
        const actualizado = await actualizarAporte(clienteId, socio.id, aporte)
        setGuardado(actualizado)
        setSocios(socios.map((s) => actualizado.socios.find((d) => d.id === s.id && s.id !== undefined) ?? s))
        setOk(`Aporte de ${socio.nombreCompleto} actualizado. Los porcentajes se recalcularon: capital social ${soles(actualizado.capitalSocialTotal)}`)
      } else {
        setSocios(socios.map((s, j) => (j === i ? { ...s, aporte } : s)))
      }
      setEditando(null)
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const st = <K extends keyof Socio>(k: K, v: Socio[K]) => setNuevo((s) => ({ ...s, [k]: v }))

  const celdaPorcentaje = (s: Socio) => {
    if (hayCambiosSinGuardar || s.porcentaje === undefined) return <span className="text-slate-400">al guardar</span>
    return <>{Number(s.porcentaje).toFixed(2)}%</>
  }

  return (
    <div className="space-y-5">
      <form onSubmit={agregar} className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Input label="Nombre completo" value={nuevo.nombreCompleto} required
            onChange={(e) => st('nombreCompleto', e.target.value)} />
          <Select label="Tipo doc." value={nuevo.tipoDocumento} onChange={(e) => st('tipoDocumento', e.target.value)}>
            <option>DNI</option><option>CE</option><option>PASAPORTE</option><option>RUC</option>
          </Select>
          <Input label="N.º documento" value={nuevo.numeroDocumento} required
            onChange={(e) => st('numeroDocumento', e.target.value)} />
          <Input label="Aporte (S/)" type="number" step="0.01" min="0.01" value={nuevo.aporte || ''} required
            onChange={(e) => st('aporte', Number(e.target.value))} />
          <Input label="Cargo" value={nuevo.cargo ?? ''} onChange={(e) => st('cargo', e.target.value)} />
        </div>
        <p className="flex items-center gap-2 text-sm text-slate-500">
          <Info size={16} /> El porcentaje de participación lo calcula el sistema según el aporte de cada socio.
        </p>
        <Button type="submit" variant="secondary"><Plus size={16} /> Agregar socio</Button>
      </form>

      <Table columnas={['Nombre', 'Documento', 'Aporte (S/)', '%', 'Cargo', '']} hayFilas={socios.length > 0}
        vacio="Aún no hay socios registrados.">
        {socios.map((s, i) => (
          <tr key={s.id ?? `nuevo-${i}`}>
            <Td>{s.nombreCompleto}</Td>
            <Td>{s.tipoDocumento} {s.numeroDocumento}</Td>
            <Td>
              {editando === i ? (
                <div className="flex items-center gap-1">
                  <input type="number" step="0.01" min="0.01" autoFocus aria-label="Nuevo aporte"
                    className="w-28 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={aporteEditado} onChange={(e) => setAporteEditado(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') guardarAporte(i); if (e.key === 'Escape') setEditando(null) }} />
                  <Button variant="secondary" aria-label="Guardar aporte" onClick={() => guardarAporte(i)}><Check size={16} /></Button>
                  <Button variant="secondary" aria-label="Cancelar" onClick={() => setEditando(null)}><X size={16} /></Button>
                </div>
              ) : (
                <span className="inline-flex items-center gap-2">
                  {soles(s.aporte)}
                  <button type="button" aria-label={`Editar aporte de ${s.nombreCompleto}`} onClick={() => empezarEdicion(i)}
                    className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"><Pencil size={14} /></button>
                </span>
              )}
            </Td>
            <Td>{celdaPorcentaje(s)}</Td>
            <Td>{s.cargo}</Td>
            <Td>
              <Button variant="danger" aria-label="Quitar" onClick={() => setSocios(socios.filter((_, j) => j !== i))}>
                <Trash2 size={16} />
              </Button>
            </Td>
          </tr>
        ))}
      </Table>

      <div className="grid gap-3 md:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
          <p className="text-sm text-slate-500">Capital social total</p>
          <p className="text-2xl font-semibold text-slate-900" data-testid="capital-total">{soles(estado.capitalSocialTotal)}</p>
        </div>
        {estado.sinAporte > 0 ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-amber-800">
            <p className="flex items-center gap-2 text-sm font-semibold">
              <AlertTriangle size={18} /> Estructura incompleta
            </p>
            <p className="text-sm" data-testid="diferencia">
              {estado.sinAporte === 1 ? '1 socio no tiene aporte' : `${estado.sinAporte} socios no tienen aporte`}:
              registre el aporte para calcular los porcentajes.
            </p>
          </div>
        ) : hayCambiosSinGuardar || !guardado ? (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-slate-700">
            <p className="flex items-center gap-2 text-sm font-semibold"><Info size={18} /> Participación</p>
            <p className="text-sm" data-testid="diferencia">Los porcentajes se calculan al guardar y suman 100.00%.</p>
          </div>
        ) : (
          <div className={`rounded-lg border p-4 ${
            guardado.completo ? 'border-green-200 bg-green-50 text-green-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
            <p className="flex items-center gap-2 text-sm font-semibold">
              {guardado.completo ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}
              Total participación: {Number(guardado.porcentajeTotal).toFixed(2)}%
            </p>
            <p className="text-sm" data-testid="diferencia">{mensajeDiferencia(guardado.diferencia)}</p>
          </div>
        )}
      </div>

      <div className="flex justify-end">
        <Button disabled={!estado.puedeGuardar || !hayCambiosSinGuardar} onClick={persistir}>
          <Save size={16} /> Guardar estructura societaria
        </Button>
      </div>
    </div>
  )
}
