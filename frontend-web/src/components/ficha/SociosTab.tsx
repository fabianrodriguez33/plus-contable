import { FormEvent, useEffect, useState } from 'react'
import { AlertTriangle, Check, CheckCircle2, Pencil, Plus, Save, Trash2, X } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { actualizarAporte, guardarSocios, obtenerResumenSocietario } from '../../services/clienteService'
import type { Socio } from '../../types'
import { mensajeDiferencia, resumenLocal, soles } from '../../utils/societario'
import Button from '../Button'
import { Input, Select } from '../Input'
import Table, { Td } from '../Table'

const socioVacio: Socio = {
  nombreCompleto: '', tipoDocumento: 'DNI', numeroDocumento: '', aporte: 0, porcentaje: 0, cargo: '',
}

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

export default function SociosTab({ clienteId, setError, setOk }: Props) {
  const [socios, setSocios] = useState<Socio[]>([])
  const [nuevo, setNuevo] = useState<Socio>(socioVacio)
  const [editando, setEditando] = useState<number | null>(null)
  const [aporteEditado, setAporteEditado] = useState('')

  useEffect(() => {
    obtenerResumenSocietario(clienteId).then((r) => setSocios(r.socios)).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  const r = resumenLocal(socios)

  const agregar = (e: FormEvent) => {
    e.preventDefault()
    setError('')
    if (Number(nuevo.porcentaje) <= 0) { setError('El porcentaje debe ser mayor a 0'); return }
    if (Number(nuevo.aporte) < 0) { setError('El aporte no puede ser negativo'); return }
    setSocios([...socios, { ...nuevo, aporte: Number(nuevo.aporte), porcentaje: Number(nuevo.porcentaje) }])
    setNuevo(socioVacio)
  }

  const persistir = async () => {
    setError('')
    try {
      const guardado = await guardarSocios(clienteId, socios)
      setSocios(guardado.socios)
      setOk(`Estructura societaria guardada. Capital social total: ${soles(guardado.capitalSocialTotal)}`)
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
    if (aporteEditado.trim() === '' || Number.isNaN(aporte) || aporte < 0) {
      setError('El aporte debe ser un monto válido, mayor o igual a 0')
      return
    }
    const socio = socios[i]
    try {
      if (socio.id !== undefined) {
        // socio ya guardado: se persiste solo el aporte y el capital social se recalcula en el servidor
        const actualizado = await actualizarAporte(clienteId, socio.id, aporte)
        const delServidor = actualizado.socios.find((s) => s.id === socio.id)
        setSocios(socios.map((s, j) => (j === i && delServidor ? { ...s, aporte: delServidor.aporte } : s)))
        setOk(`Aporte de ${socio.nombreCompleto} actualizado (${soles(delServidor?.aporte ?? aporte)})`)
      } else {
        setSocios(socios.map((s, j) => (j === i ? { ...s, aporte } : s)))
      }
      setEditando(null)
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const st = <K extends keyof Socio>(k: K, v: Socio[K]) => setNuevo((s) => ({ ...s, [k]: v }))

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
          <Input label="Aporte (S/)" type="number" step="0.01" min="0" value={nuevo.aporte || ''} required
            onChange={(e) => st('aporte', Number(e.target.value))} />
          <Input label="% participación" type="number" step="0.01" min="0.01" max="100"
            value={nuevo.porcentaje || ''} required onChange={(e) => st('porcentaje', Number(e.target.value))} />
          <Input label="Cargo" value={nuevo.cargo ?? ''} onChange={(e) => st('cargo', e.target.value)} />
        </div>
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
                  <input type="number" step="0.01" min="0" autoFocus aria-label="Nuevo aporte"
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
            <Td>{Number(s.porcentaje).toFixed(2)}%</Td>
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
          <p className="text-2xl font-semibold text-slate-900" data-testid="capital-total">{soles(r.capitalSocialTotal)}</p>
        </div>
        <div className={`rounded-lg border p-4 ${
          r.completo ? 'border-green-200 bg-green-50 text-green-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
          <p className="flex items-center gap-2 text-sm font-semibold">
            {r.completo ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}
            Total participación: {r.porcentajeTotal.toFixed(2)}%
          </p>
          <p className="text-sm" data-testid="diferencia">{mensajeDiferencia(r.diferencia)}</p>
        </div>
      </div>

      <div className="flex justify-end">
        <Button disabled={!r.completo} onClick={persistir}><Save size={16} /> Guardar estructura societaria</Button>
      </div>
    </div>
  )
}
