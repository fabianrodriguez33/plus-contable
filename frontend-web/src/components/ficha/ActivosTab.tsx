import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { agregarActivo, eliminarActivo, listarActivos } from '../../services/clienteService'
import type { ActivoFijo } from '../../types'
import Button from '../Button'
import { Input } from '../Input'
import Table, { Td } from '../Table'

const activoVacio: ActivoFijo = { codigo: '', descripcion: '', categoria: '', valorAdquisicion: 0, fechaAdquisicion: '' }

interface Props {
  clienteId: number
  setError: (m: string) => void
}

export default function ActivosTab({ clienteId, setError }: Props) {
  const [activos, setActivos] = useState<ActivoFijo[]>([])
  const [nuevo, setNuevo] = useState<ActivoFijo>(activoVacio)

  const cargar = useCallback(() => {
    listarActivos(clienteId).then(setActivos).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const alta = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await agregarActivo(clienteId, { ...nuevo, fechaAdquisicion: nuevo.fechaAdquisicion || undefined })
      setNuevo(activoVacio)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const baja = async (a: ActivoFijo) => {
    setError('')
    try {
      await eliminarActivo(clienteId, a.id!)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const set = <K extends keyof ActivoFijo>(k: K, v: ActivoFijo[K]) => setNuevo((a) => ({ ...a, [k]: v }))
  const total = activos.reduce((acc, a) => acc + Number(a.valorAdquisicion), 0)

  return (
    <div className="space-y-5">
      <form onSubmit={alta} className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Input label="Código" value={nuevo.codigo ?? ''} onChange={(e) => set('codigo', e.target.value)} />
          <Input label="Descripción" value={nuevo.descripcion} required
            onChange={(e) => set('descripcion', e.target.value)} />
          <Input label="Categoría" value={nuevo.categoria ?? ''} onChange={(e) => set('categoria', e.target.value)} />
          <Input label="Valor de adquisición (S/)" type="number" step="0.01" min="0" required
            value={nuevo.valorAdquisicion || ''} onChange={(e) => set('valorAdquisicion', Number(e.target.value))} />
          <Input label="Fecha de adquisición" type="date" value={nuevo.fechaAdquisicion ?? ''}
            onChange={(e) => set('fechaAdquisicion', e.target.value)} />
        </div>
        <Button type="submit"><Plus size={16} /> Agregar activo</Button>
      </form>

      <Table columnas={['Código', 'Descripción', 'Categoría', 'Valor (S/)', 'Adquisición', '']}
        hayFilas={activos.length > 0} vacio="Aún no hay activos fijos registrados.">
        {activos.map((a) => (
          <tr key={a.id}>
            <Td>{a.codigo}</Td>
            <Td>{a.descripcion}</Td>
            <Td>{a.categoria}</Td>
            <Td>{Number(a.valorAdquisicion).toFixed(2)}</Td>
            <Td>{a.fechaAdquisicion}</Td>
            <Td>
              <Button variant="danger" aria-label="Quitar" onClick={() => baja(a)}><Trash2 size={16} /></Button>
            </Td>
          </tr>
        ))}
      </Table>
      <p className="text-right text-sm font-semibold text-slate-700">Total activos: S/ {total.toFixed(2)}</p>
    </div>
  )
}
