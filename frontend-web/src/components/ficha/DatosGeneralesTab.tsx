import { FormEvent, useEffect, useRef, useState } from 'react'
import { Eye, Save, Search } from 'lucide-react'
import { Input, Select } from '../Input'
import Button from '../Button'
import type { Cliente, ConsultaRuc, Regimen } from '../../types'
import { motivoRucInvalido } from '../../utils/ruc'
import { mensajeError } from '../../services/apiClient'
import { consultarRuc } from '../../services/clienteService'

/** yyyy-MM-dd -> dd/MM/yyyy, sin pasar por Date para no mover el día por la zona horaria. */
function fechaCorta(iso?: string | null): string | null {
  const m = iso ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso) : null
  return m ? `${m[3]}/${m[2]}/${m[1]}` : null
}

/** Resultado de la consulta de RUC: informa, avisa y nunca bloquea el registro. */
function PanelConsultaRuc({ consulta }: { consulta: ConsultaRuc }) {
  const encontrado = consulta.resultado === 'ENCONTRADO' && consulta.datos
  const fecha = fechaCorta(consulta.datos?.fechaDatos)
  return (
    <div role="status" className="space-y-2 text-sm">
      {encontrado ? (
        <div className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-blue-800">
          <p>
            Datos de SUNAT{fecha ? ` al ${fecha}` : ''}. Se completaron razón social, dirección y condición: revíselos
            antes de registrar. El régimen tributario y las credenciales SOL se ingresan manualmente.
          </p>
        </div>
      ) : (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-slate-700">
          <p>{consulta.mensaje}</p>
        </div>
      )}
      {consulta.avisos.length > 0 && (
        <ul className="list-disc space-y-1 rounded-lg border border-amber-200 bg-amber-50 p-3 pl-7 text-amber-800">
          {consulta.avisos.map((a) => <li key={a}>{a}</li>)}
        </ul>
      )}
    </div>
  )
}

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

  const [consulta, setConsulta] = useState<ConsultaRuc | null>(null)
  const [consultando, setConsultando] = useState(false)
  const [errorConsulta, setErrorConsulta] = useState('')
  const rucActual = useRef(cliente.ruc)
  rucActual.current = cliente.ruc
  // al cambiar el RUC, lo consultado ya no corresponde
  useEffect(() => { setConsulta(null); setErrorConsulta('') }, [cliente.ruc])

  // la consulta la hace ms-tributario; si no responde, el contador completa los datos a mano
  const consultar = async () => {
    const ruc = cliente.ruc
    setConsultando(true)
    setConsulta(null)
    setErrorConsulta('')
    try {
      const r = await consultarRuc(ruc)
      if (rucActual.current !== ruc) return
      setConsulta(r)
      if (r.resultado === 'ENCONTRADO' && r.datos) {
        onCambio('razonSocial', r.datos.razonSocial)
        if (r.datos.direccion) onCambio('direccion', r.datos.direccion)
        if (r.datos.condicionHabido) onCambio('condicionHabido', r.datos.condicionHabido)
      }
    } catch (err) {
      if (rucActual.current === ruc) setErrorConsulta(`${mensajeError(err)}. Complete los datos manualmente.`)
    } finally {
      setConsultando(false)
    }
  }

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
        <div>
          <Input label="RUC (11 dígitos)" value={cliente.ruc} maxLength={11} inputMode="numeric" required
            error={errorRuc} onBlur={() => setRucTocado(true)}
            onChange={(e) => onCambio('ruc', e.target.value.replace(/\D/g, ''))} />
          {esNuevo && (
            <Button variant="secondary" className="mt-2" disabled={consultando || motivoRucInvalido(cliente.ruc) !== null}
              onClick={consultar}>
              <Search size={16} /> {consultando ? 'Consultando…' : 'Consultar RUC'}
            </Button>
          )}
        </div>
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

      {consulta && <PanelConsultaRuc consulta={consulta} />}
      {errorConsulta && (
        <div role="status" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">{errorConsulta}</div>
      )}

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
