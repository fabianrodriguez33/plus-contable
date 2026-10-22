import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { agregarTrabajador, eliminarTrabajador, listarTrabajadores } from '../../services/clienteService'
import type { Regimen, Trabajador } from '../../types'
import Button from '../Button'
import { Input, Select } from '../Input'
import Table, { Td } from '../Table'

const trabajadorVacio: Trabajador = {
  nombreCompleto: '', tipoDocumento: 'DNI', numeroDocumento: '', extranjero: false, nacionalidad: '',
  regimenLaboral: 'MYPE', sistemaPensionario: 'AFP', nombreAfp: '', fechaIngreso: '', remuneracion: undefined,
  registroSunafil: false, registroMtpe: false,
}

interface Props {
  clienteId: number
  setError: (m: string) => void
}

export default function TrabajadoresTab({ clienteId, setError }: Props) {
  const [trabajadores, setTrabajadores] = useState<Trabajador[]>([])
  const [nuevo, setNuevo] = useState<Trabajador>(trabajadorVacio)

  const cargar = useCallback(() => {
    listarTrabajadores(clienteId).then(setTrabajadores).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const alta = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await agregarTrabajador(clienteId, { ...nuevo, fechaIngreso: nuevo.fechaIngreso || undefined })
      setNuevo(trabajadorVacio)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const baja = async (t: Trabajador) => {
    setError('')
    try {
      await eliminarTrabajador(clienteId, t.id!)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const tt = <K extends keyof Trabajador>(k: K, v: Trabajador[K]) => setNuevo((t) => ({ ...t, [k]: v }))
  const check = 'h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-600'

  return (
    <div className="space-y-5">
      <form onSubmit={alta} className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Input label="Nombre completo" value={nuevo.nombreCompleto} required
            onChange={(e) => tt('nombreCompleto', e.target.value)} />
          <Select label="Tipo doc." value={nuevo.tipoDocumento} onChange={(e) => tt('tipoDocumento', e.target.value)}>
            <option>DNI</option><option>CE</option><option>PASAPORTE</option><option>PTP</option>
          </Select>
          <Input label="N.º documento" value={nuevo.numeroDocumento} required
            onChange={(e) => tt('numeroDocumento', e.target.value)} />
          <Select label="Régimen laboral" value={nuevo.regimenLaboral}
            onChange={(e) => tt('regimenLaboral', e.target.value as Regimen)}>
            <option value="MYPE">MYPE</option><option value="REMIPE">REMIPE</option><option value="GENERAL">General</option>
          </Select>
          <Select label="Sistema pensionario" value={nuevo.sistemaPensionario}
            onChange={(e) => tt('sistemaPensionario', e.target.value as 'AFP' | 'ONP')}>
            <option value="AFP">AFP</option><option value="ONP">ONP</option>
          </Select>
          {nuevo.sistemaPensionario === 'AFP' && (
            <Select label="AFP" value={nuevo.nombreAfp ?? ''} required onChange={(e) => tt('nombreAfp', e.target.value)}>
              <option value="">Seleccione</option>
              <option>Integra</option><option>Prima</option><option>Profuturo</option><option>Habitat</option>
            </Select>
          )}
          <Input label="Fecha de ingreso" type="date" value={nuevo.fechaIngreso ?? ''}
            onChange={(e) => tt('fechaIngreso', e.target.value)} />
          <Input label="Remuneración (S/)" type="number" step="0.01" min="0" value={nuevo.remuneracion ?? ''}
            onChange={(e) => tt('remuneracion', e.target.value === '' ? undefined : Number(e.target.value))} />
        </div>
        <div className="flex flex-wrap items-center gap-6 text-sm">
          <label className="flex items-center gap-2">
            <input type="checkbox" className={check} checked={nuevo.extranjero}
              onChange={(e) => tt('extranjero', e.target.checked)} /> Trabajador extranjero
          </label>
          {nuevo.extranjero && (
            <div className="w-52">
              <Input label="Nacionalidad" value={nuevo.nacionalidad ?? ''} required
                onChange={(e) => tt('nacionalidad', e.target.value)} />
            </div>
          )}
          <label className="flex items-center gap-2">
            <input type="checkbox" className={check} checked={nuevo.registroSunafil}
              onChange={(e) => tt('registroSunafil', e.target.checked)} /> Registro SUNAFIL
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" className={check} checked={nuevo.registroMtpe}
              onChange={(e) => tt('registroMtpe', e.target.checked)} /> Registro MTPE
          </label>
        </div>
        <Button type="submit"><Plus size={16} /> Agregar trabajador</Button>
      </form>

      <Table columnas={['Nombre', 'Documento', 'Origen', 'Régimen', 'Pensión', 'SUNAFIL', 'MTPE', '']}
        hayFilas={trabajadores.length > 0} vacio="Aún no hay trabajadores registrados.">
        {trabajadores.map((t) => (
          <tr key={t.id}>
            <Td>{t.nombreCompleto}</Td>
            <Td>{t.tipoDocumento} {t.numeroDocumento}</Td>
            <Td>{t.extranjero ? `Extranjero (${t.nacionalidad})` : 'Nacional'}</Td>
            <Td>{t.regimenLaboral}</Td>
            <Td>{t.sistemaPensionario}{t.nombreAfp ? ` – ${t.nombreAfp}` : ''}</Td>
            <Td>{t.registroSunafil ? 'Sí' : 'No'}</Td>
            <Td>{t.registroMtpe ? 'Sí' : 'No'}</Td>
            <Td>
              <Button variant="danger" aria-label="Quitar" onClick={() => baja(t)}><Trash2 size={16} /></Button>
            </Td>
          </tr>
        ))}
      </Table>
    </div>
  )
}
