import { FormEvent, useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, Plus, Save, Trash2 } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { guardarSocios, listarSocios } from '../../services/clienteService'
import type { Socio } from '../../types'
import Button from '../Button'
import { Input, Select } from '../Input'
import Table, { Td } from '../Table'

const socioVacio: Socio = { nombreCompleto: '', tipoDocumento: 'DNI', numeroDocumento: '', porcentaje: 0, cargo: '' }

interface Props {
  clienteId: number
  setError: (m: string) => void
  setOk: (m: string) => void
}

export default function SociosTab({ clienteId, setError, setOk }: Props) {
  const [socios, setSocios] = useState<Socio[]>([])
  const [nuevo, setNuevo] = useState<Socio>(socioVacio)

  useEffect(() => {
    listarSocios(clienteId).then(setSocios).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  const total = socios.reduce((acc, s) => acc + Number(s.porcentaje), 0)
  const totalOk = Math.abs(total - 100) < 0.005

  const agregar = (e: FormEvent) => {
    e.preventDefault()
    setError('')
    if (Number(nuevo.porcentaje) <= 0) { setError('El porcentaje debe ser mayor a 0'); return }
    setSocios([...socios, { ...nuevo, porcentaje: Number(nuevo.porcentaje) }])
    setNuevo(socioVacio)
  }

  const persistir = async () => {
    setError('')
    try {
      setSocios(await guardarSocios(clienteId, socios))
      setOk('Estructura societaria guardada')
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
          <Input label="% participación" type="number" step="0.01" min="0.01" max="100"
            value={nuevo.porcentaje || ''} required onChange={(e) => st('porcentaje', Number(e.target.value))} />
          <Input label="Cargo" value={nuevo.cargo ?? ''} onChange={(e) => st('cargo', e.target.value)} />
        </div>
        <Button type="submit" variant="secondary"><Plus size={16} /> Agregar socio</Button>
      </form>

      <Table columnas={['Nombre', 'Documento', '%', 'Cargo', '']} hayFilas={socios.length > 0}
        vacio="Aún no hay socios registrados.">
        {socios.map((s, i) => (
          <tr key={i}>
            <Td>{s.nombreCompleto}</Td>
            <Td>{s.tipoDocumento} {s.numeroDocumento}</Td>
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

      <div className={`flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4 ${
        totalOk ? 'border-green-200 bg-green-50 text-green-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
        <strong className="flex items-center gap-2">
          {totalOk ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}
          Total participación: {total.toFixed(2)}% {totalOk ? '' : '(debe sumar exactamente 100.00%)'}
        </strong>
        <Button disabled={!totalOk} onClick={persistir}><Save size={16} /> Guardar estructura societaria</Button>
      </div>
    </div>
  )
}
