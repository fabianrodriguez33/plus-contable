import { FormEvent, useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Boxes, Building, FolderOpen, PieChart, Users } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { mensajeError, tributarioApi } from '../services/apiClient'
import { actualizarCliente, crearCliente, obtenerCliente } from '../services/clienteService'
import type { Cliente } from '../types'
import Card from '../components/Card'
import DatosGeneralesTab from '../components/ficha/DatosGeneralesTab'
import ExpedienteTab from '../components/ficha/ExpedienteTab'
import SociosTab from '../components/ficha/SociosTab'
import TrabajadoresTab from '../components/ficha/TrabajadoresTab'
import ActivosTab from '../components/ficha/ActivosTab'

type Tab = 'general' | 'expediente' | 'socios' | 'trabajadores' | 'activos'

const TABS: { id: Tab; etiqueta: string; icono: LucideIcon }[] = [
  { id: 'general', etiqueta: 'Datos generales', icono: Building },
  { id: 'expediente', etiqueta: 'Expediente digital', icono: FolderOpen },
  { id: 'socios', etiqueta: 'Socios y capital', icono: PieChart },
  { id: 'trabajadores', etiqueta: 'Planilla', icono: Users },
  { id: 'activos', etiqueta: 'Activos fijos', icono: Boxes },
]

const clienteVacio: Cliente = {
  ruc: '', razonSocial: '', regimenTributario: 'MYPE', condicionHabido: 'HABIDO',
  direccion: '', usuarioSol: '', claveSol: '',
}

export default function FichaMaestraPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const clienteId = id ? Number(id) : null

  const [tab, setTab] = useState<Tab>('general')
  const [cliente, setCliente] = useState<Cliente>(clienteVacio)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const limpiar = useCallback(() => { setError(''); setOk('') }, [])

  useEffect(() => {
    if (clienteId === null) { setCliente(clienteVacio); setTab('general'); return }
    obtenerCliente(clienteId)
      .then((c) => setCliente({ ...c, usuarioSol: '', claveSol: '' }))
      .catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  const guardarGeneral = async (e: FormEvent) => {
    e.preventDefault()
    limpiar()
    try {
      if (clienteId === null) {
        const creado = await crearCliente(cliente)
        navigate(`/clientes/${creado.id}`, { replace: true })
      } else {
        const actualizado = await actualizarCliente(clienteId, cliente)
        setCliente({ ...actualizado, usuarioSol: '', claveSol: '' })
        setOk('Datos generales guardados')
      }
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const revelarSol = async () => {
    if (clienteId === null) return
    limpiar()
    try {
      const { data } = await tributarioApi.get<{ usuarioSol: string; claveSol: string }>(
        `/api/tributario/clientes/${clienteId}/sol`)
      setCliente((c) => ({ ...c, usuarioSol: data.usuarioSol, claveSol: data.claveSol }))
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const cambiar = <K extends keyof Cliente>(k: K, v: Cliente[K]) => setCliente((c) => ({ ...c, [k]: v }))

  return (
    <Card title={clienteId === null ? 'Nuevo cliente' : `Ficha maestra — ${cliente.razonSocial || cliente.ruc}`}>
      <div role="tablist" className="mb-5 flex flex-wrap gap-1 border-b border-slate-200">
        {TABS.map(({ id: tid, etiqueta, icono: Icono }) => {
          const activa = tab === tid
          const bloqueada = clienteId === null && tid !== 'general'
          return (
            <button key={tid} type="button" role="tab" aria-selected={activa} disabled={bloqueada}
              onClick={() => { limpiar(); setTab(tid) }}
              className={`-mb-px flex items-center gap-2 border-b-2 px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:text-slate-300 ${
                activa ? 'border-brand-600 text-brand-600' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>
              <Icono size={16} /> {etiqueta}
            </button>
          )
        })}
      </div>

      {clienteId === null && (
        <p className="mb-4 text-sm text-slate-500">Registre primero los datos generales para habilitar las demás pestañas.</p>
      )}
      {error && <div role="alert" className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
      {ok && <div role="status" className="mb-4 rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-700">{ok}</div>}

      {tab === 'general' && (
        <DatosGeneralesTab cliente={cliente} esNuevo={clienteId === null} onCambio={cambiar}
          onGuardar={guardarGeneral} onRevelarSol={revelarSol} />
      )}
      {clienteId !== null && tab === 'expediente' && <ExpedienteTab clienteId={clienteId} setError={setError} />}
      {clienteId !== null && tab === 'socios' && <SociosTab clienteId={clienteId} setError={setError} setOk={setOk} />}
      {clienteId !== null && tab === 'trabajadores' && <TrabajadoresTab clienteId={clienteId} setError={setError} />}
      {clienteId !== null && tab === 'activos' && <ActivosTab clienteId={clienteId} setError={setError} />}
    </Card>
  )
}
