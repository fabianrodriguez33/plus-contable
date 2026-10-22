import { FormEvent, useState } from 'react'
import { Eye, Save } from 'lucide-react'
import { Input, Select } from '../Input'
import Button from '../Button'
import type { Cliente, Regimen } from '../../types'
import { motivoRucInvalido } from '../../utils/ruc'

interface Props {
  cliente: Cliente
  esNuevo: boolean
  onCambio: <K extends keyof Cliente>(k: K, v: Cliente[K]) => void
  onGuardar: (e: FormEvent) => void
  onRevelarSol: () => void
}

export default function DatosGeneralesTab({ cliente, esNuevo, onCambio, onGuardar, onRevelarSol }: Props) {
  const [verClave, setVerClave] = useState(false)
  const [rucTocado, setRucTocado] = useState(false)
  // se avisa al completar los 11 dígitos o al salir del campo, no mientras se escribe
  const errorRuc = cliente.ruc.length === 11 || rucTocado ? motivoRucInvalido(cliente.ruc) : null

  const enviar = (e: FormEvent) => {
    if (motivoRucInvalido(cliente.ruc)) {
      e.preventDefault()
      setRucTocado(true)
      return
    }
    onGuardar(e)
  }

  return (
    <form onSubmit={enviar} className="space-y-6">
      <div className="grid gap-4 md:grid-cols-2">
        <Input label="RUC (11 dígitos)" value={cliente.ruc} maxLength={11} inputMode="numeric" required
          error={errorRuc} onBlur={() => setRucTocado(true)}
          onChange={(e) => onCambio('ruc', e.target.value.replace(/\D/g, ''))} />
        <Input label="Razón social" value={cliente.razonSocial} required
          onChange={(e) => onCambio('razonSocial', e.target.value)} />
        <Select label="Régimen tributario" value={cliente.regimenTributario}
          onChange={(e) => onCambio('regimenTributario', e.target.value as Regimen)}>
          <option value="MYPE">MYPE Tributario</option>
          <option value="REMIPE">RER (REMIPE)</option>
          <option value="GENERAL">Régimen General</option>
        </Select>
        <Select label="Condición" value={cliente.condicionHabido}
          onChange={(e) => onCambio('condicionHabido', e.target.value as Cliente['condicionHabido'])}>
          <option value="HABIDO">Habido</option>
          <option value="NO_HABIDO">No habido</option>
        </Select>
        <div className="md:col-span-2">
          <Input label="Dirección fiscal" value={cliente.direccion ?? ''}
            onChange={(e) => onCambio('direccion', e.target.value)} />
        </div>
      </div>

      <div>
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Credenciales SUNAT (cifradas con AES-256)
        </h3>
        <div className="grid gap-4 md:grid-cols-2">
          <Input label="Usuario SOL" autoComplete="off" value={cliente.usuarioSol ?? ''}
            placeholder={cliente.tieneClaveSol ? 'Registrado (vacío para no cambiar)' : ''}
            onChange={(e) => onCambio('usuarioSol', e.target.value)} />
          <Input label="Clave SOL" type={verClave ? 'text' : 'password'} autoComplete="new-password"
            value={cliente.claveSol ?? ''} placeholder={cliente.tieneClaveSol ? '••••••••' : ''}
            onChange={(e) => onCambio('claveSol', e.target.value)} />
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button type="submit"><Save size={16} /> {esNuevo ? 'Registrar cliente' : 'Guardar cambios'}</Button>
        {!esNuevo && cliente.tieneClaveSol && (
          <Button variant="secondary" onClick={() => { setVerClave(true); onRevelarSol() }}>
            <Eye size={16} /> Mostrar credenciales SOL
          </Button>
        )}
      </div>
    </form>
  )
}
