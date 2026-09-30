import { ChangeEvent, FormEvent, useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { mensajeError, tributarioApi } from '../services/apiClient'
import {
  Cliente, Documento, Regimen, Socio, TipoDocumento, Trabajador,
  actualizarCliente, agregarTrabajador, crearCliente, descargarDocumento, eliminarDocumento,
  eliminarTrabajador, guardarSocios, listarDocumentos, listarSocios, listarTrabajadores,
  obtenerCliente, subirDocumento,
} from '../services/clienteService'

type Tab = 'general' | 'legal' | 'societaria'

const TIPOS_DOC: { tipo: TipoDocumento; etiqueta: string }[] = [
  { tipo: 'MINUTA', etiqueta: 'Minuta de constitución' },
  { tipo: 'COPIA_LITERAL', etiqueta: 'Copia literal' },
  { tipo: 'VIGENCIA_PODERES', etiqueta: 'Vigencia de poderes' },
  { tipo: 'FICHA_RUC', etiqueta: 'Ficha RUC' },
]

const clienteVacio: Cliente = {
  ruc: '', razonSocial: '', regimenTributario: 'MYPE', condicionHabido: 'HABIDO',
  direccion: '', usuarioSol: '', claveSol: '',
}

const socioVacio: Socio = { nombreCompleto: '', tipoDocumento: 'DNI', numeroDocumento: '', porcentaje: 0, cargo: '' }

const trabajadorVacio: Trabajador = {
  nombreCompleto: '', tipoDocumento: 'DNI', numeroDocumento: '', extranjero: false, nacionalidad: '',
  regimenLaboral: 'MYPE', sistemaPensionario: 'AFP', nombreAfp: '', fechaIngreso: '', remuneracion: undefined,
  registroSunafil: false, registroMtpe: false,
}

export default function FichaMaestraPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const clienteId = id ? Number(id) : null

  const [tab, setTab] = useState<Tab>('general')
  const [cliente, setCliente] = useState<Cliente>(clienteVacio)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const limpiar = () => { setError(''); setOk('') }

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
      const { data } = await tributarioApi.get<{ usuarioSol: string; claveSol: string }>(`/api/clientes/${clienteId}/sol`)
      setCliente((c) => ({ ...c, usuarioSol: data.usuarioSol, claveSol: data.claveSol }))
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const set = <K extends keyof Cliente>(k: K, v: Cliente[K]) => setCliente((c) => ({ ...c, [k]: v }))

  return (
    <div className="card">
      <h2>{clienteId === null ? 'Nuevo cliente' : `Ficha maestra — ${cliente.razonSocial || cliente.ruc}`}</h2>
      <div className="tabs">
        <button className={tab === 'general' ? 'active' : ''} onClick={() => { limpiar(); setTab('general') }}>
          Datos generales
        </button>
        <button className={tab === 'legal' ? 'active' : ''} disabled={clienteId === null}
          onClick={() => { limpiar(); setTab('legal') }}>
          Expediente legal
        </button>
        <button className={tab === 'societaria' ? 'active' : ''} disabled={clienteId === null}
          onClick={() => { limpiar(); setTab('societaria') }}>
          Estructura societaria y laboral
        </button>
      </div>
      {clienteId === null && <p className="muted">Registre primero los datos generales para habilitar las demás pestañas.</p>}
      {error && <div className="error">{error}</div>}
      {ok && <div className="ok">{ok}</div>}

      {tab === 'general' && (
        <form onSubmit={guardarGeneral}>
          <div className="grid">
            <div>
              <label htmlFor="ruc">RUC (11 dígitos)</label>
              <input id="ruc" value={cliente.ruc} maxLength={11} inputMode="numeric" pattern="[0-9]{11}"
                title="11 dígitos numéricos" required
                onChange={(e) => set('ruc', e.target.value.replace(/\D/g, ''))} />
            </div>
            <div>
              <label htmlFor="rs">Razón social</label>
              <input id="rs" value={cliente.razonSocial} required onChange={(e) => set('razonSocial', e.target.value)} />
            </div>
            <div>
              <label htmlFor="reg">Régimen tributario</label>
              <select id="reg" value={cliente.regimenTributario}
                onChange={(e) => set('regimenTributario', e.target.value as Regimen)}>
                <option value="MYPE">MYPE Tributario</option>
                <option value="REMIPE">RER (REMIPE)</option>
                <option value="GENERAL">Régimen General</option>
              </select>
            </div>
            <div>
              <label htmlFor="hab">Condición</label>
              <select id="hab" value={cliente.condicionHabido}
                onChange={(e) => set('condicionHabido', e.target.value as Cliente['condicionHabido'])}>
                <option value="HABIDO">Habido</option>
                <option value="NO_HABIDO">No habido</option>
              </select>
            </div>
          </div>
          <label htmlFor="dir">Dirección fiscal</label>
          <input id="dir" value={cliente.direccion ?? ''} onChange={(e) => set('direccion', e.target.value)} />

          <h3 style={{ marginTop: 24 }}>Credenciales SUNAT (cifradas con AES-256)</h3>
          <div className="grid">
            <div>
              <label htmlFor="usol">Usuario SOL</label>
              <input id="usol" autoComplete="off" value={cliente.usuarioSol ?? ''}
                placeholder={cliente.tieneClaveSol ? 'Registrado (dejar vacío para no cambiar)' : ''}
                onChange={(e) => set('usuarioSol', e.target.value)} />
            </div>
            <div>
              <label htmlFor="csol">Clave SOL</label>
              <input id="csol" type="password" autoComplete="new-password" value={cliente.claveSol ?? ''}
                placeholder={cliente.tieneClaveSol ? '••••••••' : ''}
                onChange={(e) => set('claveSol', e.target.value)} />
            </div>
          </div>
          <div style={{ marginTop: 20, display: 'flex', gap: 8 }}>
            <button type="submit">{clienteId === null ? 'Registrar cliente' : 'Guardar cambios'}</button>
            {clienteId !== null && cliente.tieneClaveSol && (
              <button type="button" className="secondary" onClick={revelarSol}>Mostrar credenciales SOL</button>
            )}
          </div>
        </form>
      )}

      {tab === 'legal' && clienteId !== null && (
        <ExpedienteLegal clienteId={clienteId} setError={setError} limpiar={limpiar} />
      )}
      {tab === 'societaria' && clienteId !== null && (
        <EstructuraSocietaria clienteId={clienteId} setError={setError} setOk={setOk} limpiar={limpiar} />
      )}
    </div>
  )
}

interface SubProps {
  clienteId: number
  setError: (m: string) => void
  limpiar: () => void
}

function ExpedienteLegal({ clienteId, setError, limpiar }: SubProps) {
  const [docs, setDocs] = useState<Documento[]>([])

  const cargar = useCallback(() => {
    listarDocumentos(clienteId).then(setDocs).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const subir = async (tipo: TipoDocumento, e: ChangeEvent<HTMLInputElement>) => {
    const archivo = e.target.files?.[0]
    e.target.value = ''
    if (!archivo) return
    limpiar()
    try {
      await subirDocumento(clienteId, tipo, archivo)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const borrar = async (d: Documento) => {
    limpiar()
    try {
      await eliminarDocumento(clienteId, d.id)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <div>
      <p className="muted">Formatos permitidos: PDF, PNG, JPG, DOC, DOCX (máx. 10 MB).</p>
      {TIPOS_DOC.map(({ tipo, etiqueta }) => {
        const delTipo = docs.filter((d) => d.tipo === tipo)
        return (
          <div key={tipo} className="card" style={{ border: '1px solid #e5e7eb', boxShadow: 'none' }}>
            <div className="row">
              <strong>{etiqueta}</strong>
              <input type="file" style={{ width: 'auto' }} accept=".pdf,.png,.jpg,.jpeg,.doc,.docx"
                onChange={(e) => subir(tipo, e)} />
            </div>
            {delTipo.length === 0 ? (
              <p className="muted">Sin documentos cargados.</p>
            ) : (
              <table>
                <tbody>
                  {delTipo.map((d) => (
                    <tr key={d.id}>
                      <td>{d.nombreArchivo}</td>
                      <td>{(d.tamanoBytes / 1024).toFixed(0)} KB</td>
                      <td>{new Date(d.subidoEn).toLocaleString()}</td>
                      <td>
                        <button type="button" className="secondary" onClick={() => descargarDocumento(d).catch((e) => setError(mensajeError(e)))}>
                          Descargar
                        </button>{' '}
                        <button type="button" className="danger" onClick={() => borrar(d)}>Eliminar</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )
      })}
    </div>
  )
}

interface SocProps extends SubProps {
  setOk: (m: string) => void
}

function EstructuraSocietaria({ clienteId, setError, setOk, limpiar }: SocProps) {
  const [socios, setSocios] = useState<Socio[]>([])
  const [nuevoSocio, setNuevoSocio] = useState<Socio>(socioVacio)
  const [trabajadores, setTrabajadores] = useState<Trabajador[]>([])
  const [nuevoTrab, setNuevoTrab] = useState<Trabajador>(trabajadorVacio)

  const cargarTrabajadores = useCallback(() => {
    listarTrabajadores(clienteId).then(setTrabajadores).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(() => {
    listarSocios(clienteId).then(setSocios).catch((e) => setError(mensajeError(e)))
    cargarTrabajadores()
  }, [clienteId, cargarTrabajadores, setError])

  const total = socios.reduce((acc, s) => acc + Number(s.porcentaje), 0)
  const totalOk = Math.abs(total - 100) < 0.005

  const agregarSocio = (e: FormEvent) => {
    e.preventDefault()
    limpiar()
    if (Number(nuevoSocio.porcentaje) <= 0) { setError('El porcentaje debe ser mayor a 0'); return }
    setSocios([...socios, { ...nuevoSocio, porcentaje: Number(nuevoSocio.porcentaje) }])
    setNuevoSocio(socioVacio)
  }

  const persistirSocios = async () => {
    limpiar()
    try {
      setSocios(await guardarSocios(clienteId, socios))
      setOk('Estructura societaria guardada')
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const altaTrabajador = async (e: FormEvent) => {
    e.preventDefault()
    limpiar()
    try {
      await agregarTrabajador(clienteId, {
        ...nuevoTrab,
        remuneracion: nuevoTrab.remuneracion === undefined || String(nuevoTrab.remuneracion) === ''
          ? undefined : Number(nuevoTrab.remuneracion),
        fechaIngreso: nuevoTrab.fechaIngreso || undefined,
      })
      setNuevoTrab(trabajadorVacio)
      cargarTrabajadores()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const borrarTrabajador = async (t: Trabajador) => {
    limpiar()
    try {
      await eliminarTrabajador(clienteId, t.id!)
      cargarTrabajadores()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const st = <K extends keyof Socio>(k: K, v: Socio[K]) => setNuevoSocio((s) => ({ ...s, [k]: v }))
  const tt = <K extends keyof Trabajador>(k: K, v: Trabajador[K]) => setNuevoTrab((t) => ({ ...t, [k]: v }))

  return (
    <div>
      <h3>Socios</h3>
      <form onSubmit={agregarSocio}>
        <div className="grid">
          <div><label>Nombre completo</label>
            <input value={nuevoSocio.nombreCompleto} required onChange={(e) => st('nombreCompleto', e.target.value)} /></div>
          <div><label>Tipo doc.</label>
            <select value={nuevoSocio.tipoDocumento} onChange={(e) => st('tipoDocumento', e.target.value)}>
              <option>DNI</option><option>CE</option><option>PASAPORTE</option><option>RUC</option>
            </select></div>
          <div><label>N.º documento</label>
            <input value={nuevoSocio.numeroDocumento} required onChange={(e) => st('numeroDocumento', e.target.value)} /></div>
          <div><label>% participación</label>
            <input type="number" step="0.01" min="0.01" max="100" value={nuevoSocio.porcentaje || ''} required
              onChange={(e) => st('porcentaje', Number(e.target.value))} /></div>
          <div><label>Cargo</label>
            <input value={nuevoSocio.cargo ?? ''} onChange={(e) => st('cargo', e.target.value)} /></div>
        </div>
        <div style={{ marginTop: 12 }}><button type="submit">Agregar socio</button></div>
      </form>
      <table>
        <thead><tr><th>Nombre</th><th>Documento</th><th>%</th><th>Cargo</th><th></th></tr></thead>
        <tbody>
          {socios.map((s, i) => (
            <tr key={i}>
              <td>{s.nombreCompleto}</td>
              <td>{s.tipoDocumento} {s.numeroDocumento}</td>
              <td>{Number(s.porcentaje).toFixed(2)}%</td>
              <td>{s.cargo}</td>
              <td><button type="button" className="danger" onClick={() => setSocios(socios.filter((_, j) => j !== i))}>Quitar</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="row" style={{ marginTop: 12 }}>
        <strong style={{ color: totalOk ? '#166534' : '#991b1b' }}>
          Total participación: {total.toFixed(2)}% {totalOk ? '✔' : '(debe sumar 100.00%)'}
        </strong>
        <button type="button" disabled={!totalOk} onClick={persistirSocios}>Guardar estructura societaria</button>
      </div>

      <h3 style={{ marginTop: 32 }}>Trabajadores</h3>
      <form onSubmit={altaTrabajador}>
        <div className="grid">
          <div><label>Nombre completo</label>
            <input value={nuevoTrab.nombreCompleto} required onChange={(e) => tt('nombreCompleto', e.target.value)} /></div>
          <div><label>Tipo doc.</label>
            <select value={nuevoTrab.tipoDocumento} onChange={(e) => tt('tipoDocumento', e.target.value)}>
              <option>DNI</option><option>CE</option><option>PASAPORTE</option><option>PTP</option>
            </select></div>
          <div><label>N.º documento</label>
            <input value={nuevoTrab.numeroDocumento} required onChange={(e) => tt('numeroDocumento', e.target.value)} /></div>
          <div><label>Régimen laboral</label>
            <select value={nuevoTrab.regimenLaboral} onChange={(e) => tt('regimenLaboral', e.target.value as Regimen)}>
              <option value="MYPE">MYPE</option><option value="REMIPE">REMIPE</option><option value="GENERAL">General</option>
            </select></div>
          <div><label>Sistema pensionario</label>
            <select value={nuevoTrab.sistemaPensionario}
              onChange={(e) => tt('sistemaPensionario', e.target.value as 'AFP' | 'ONP')}>
              <option value="AFP">AFP</option><option value="ONP">ONP</option>
            </select></div>
          {nuevoTrab.sistemaPensionario === 'AFP' && (
            <div><label>AFP</label>
              <select value={nuevoTrab.nombreAfp ?? ''} required onChange={(e) => tt('nombreAfp', e.target.value)}>
                <option value="">Seleccione</option>
                <option>Integra</option><option>Prima</option><option>Profuturo</option><option>Habitat</option>
              </select></div>
          )}
          <div><label>Fecha de ingreso</label>
            <input type="date" value={nuevoTrab.fechaIngreso ?? ''} onChange={(e) => tt('fechaIngreso', e.target.value)} /></div>
          <div><label>Remuneración (S/)</label>
            <input type="number" step="0.01" min="0" value={nuevoTrab.remuneracion ?? ''}
              onChange={(e) => tt('remuneracion', e.target.value === '' ? undefined : Number(e.target.value))} /></div>
        </div>
        <div style={{ display: 'flex', gap: 24, marginTop: 12, flexWrap: 'wrap' }}>
          <label style={{ margin: 0 }}><input type="checkbox" checked={nuevoTrab.extranjero}
            onChange={(e) => tt('extranjero', e.target.checked)} />Trabajador extranjero</label>
          {nuevoTrab.extranjero && (
            <input style={{ width: 200 }} placeholder="Nacionalidad" value={nuevoTrab.nacionalidad ?? ''} required
              onChange={(e) => tt('nacionalidad', e.target.value)} />
          )}
          <label style={{ margin: 0 }}><input type="checkbox" checked={nuevoTrab.registroSunafil}
            onChange={(e) => tt('registroSunafil', e.target.checked)} />Registro SUNAFIL</label>
          <label style={{ margin: 0 }}><input type="checkbox" checked={nuevoTrab.registroMtpe}
            onChange={(e) => tt('registroMtpe', e.target.checked)} />Registro MTPE</label>
        </div>
        <div style={{ marginTop: 12 }}><button type="submit">Agregar trabajador</button></div>
      </form>
      <table>
        <thead>
          <tr><th>Nombre</th><th>Documento</th><th>Origen</th><th>Régimen</th><th>Pensión</th><th>SUNAFIL</th><th>MTPE</th><th></th></tr>
        </thead>
        <tbody>
          {trabajadores.map((t) => (
            <tr key={t.id}>
              <td>{t.nombreCompleto}</td>
              <td>{t.tipoDocumento} {t.numeroDocumento}</td>
              <td>{t.extranjero ? `Extranjero (${t.nacionalidad})` : 'Nacional'}</td>
              <td>{t.regimenLaboral}</td>
              <td>{t.sistemaPensionario}{t.nombreAfp ? ` – ${t.nombreAfp}` : ''}</td>
              <td>{t.registroSunafil ? 'Sí' : 'No'}</td>
              <td>{t.registroMtpe ? 'Sí' : 'No'}</td>
              <td><button type="button" className="danger" onClick={() => borrarTrabajador(t)}>Quitar</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
