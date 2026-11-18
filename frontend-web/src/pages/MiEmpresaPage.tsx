import { useEffect, useState } from 'react'
import { clienteIdActual } from '../services/authService'
import { mensajeError } from '../services/apiClient'
import { obtenerDatosEmpresa, type DatosEmpresa, type TipoDocumento } from '../services/datosEmpresaService'
import { fechaCorta } from '../utils/programacion'
import Card from '../components/Card'
import Table, { Td } from '../components/Table'

const REGIMENES: Record<string, string> = {
  MYPE: 'MYPE Tributario',
  RER: 'Régimen Especial del Impuesto a la Renta (RER)',
  GENERAL: 'Régimen General',
}
const DOCUMENTOS: Record<TipoDocumento, string> = {
  MINUTA: 'Minuta de constitución',
  VIGENCIA_PODERES: 'Vigencia de poderes',
  FICHA_RUC: 'Ficha RUC',
}

/** Un dato que falta no se deja en blanco: se dice que está pendiente. */
export const PENDIENTE = 'Pendiente de registrar'
const dato = (valor: string | null | undefined) => (valor && valor.trim() ? valor : <span className="text-slate-500">{PENDIENTE}</span>)

/**
 * HU-35 · Tareas 218 a 221: el cliente ve el régimen tributario, los socios y los documentos vigentes de su empresa, solo
 * lectura (no hay formularios ni botones de guardar). La empresa sale de su propio token y el servidor vuelve a verificarla.
 */
export default function MiEmpresaPage() {
  const clienteId = clienteIdActual()
  const [datos, setDatos] = useState<DatosEmpresa | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (clienteId === null) return
    obtenerDatosEmpresa(clienteId).then(setDatos).catch((e) => setError(mensajeError(e)))
  }, [clienteId])

  if (clienteId === null) {
    return <Card title="Mi empresa"><p role="alert" className="text-sm text-red-600">Su cuenta no está vinculada a una empresa. Comuníquese con el estudio.</p></Card>
  }
  return (
    <Card title="Mi empresa">
      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      {datos === null ? (
        !error && <p className="text-sm text-slate-500">Cargando...</p>
      ) : (
        <div className="space-y-8">
          <dl className="grid gap-4 text-sm md:grid-cols-2">
            <div><dt className="text-slate-500">Razón social</dt><dd className="font-medium">{datos.razonSocial}</dd></div>
            <div><dt className="text-slate-500">RUC</dt><dd className="font-mono">{datos.ruc}</dd></div>
            <div><dt className="text-slate-500">Régimen tributario</dt><dd>{dato(REGIMENES[datos.regimenTributario] ?? datos.regimenTributario)}</dd></div>
            <div><dt className="text-slate-500">Dirección fiscal</dt><dd>{dato(datos.direccion)}</dd></div>
            <div><dt className="text-slate-500">Teléfono</dt><dd>{dato(datos.telefono)}</dd></div>
            <div><dt className="text-slate-500">Correo</dt><dd>{dato(datos.email)}</dd></div>
          </dl>

          <section aria-labelledby="socios-titulo">
            <h3 id="socios-titulo" className="mb-2 font-semibold">Socios</h3>
            {datos.socios.length === 0 ? (
              <p className="text-sm text-slate-500">{PENDIENTE}</p>
            ) : (
              <Table columnas={['Socio', 'Cargo', 'Participación']} hayFilas vacio="">
                {datos.socios.map((s, i) => (
                  <tr key={i}>
                    <Td>{s.nombreCompleto}</Td>
                    <Td>{dato(s.cargo)}</Td>
                    <Td>{Number(s.porcentaje).toFixed(2)} %</Td>
                  </tr>
                ))}
              </Table>
            )}
          </section>

          <section aria-labelledby="docs-titulo">
            <h3 id="docs-titulo" className="mb-2 font-semibold">Documentos vigentes</h3>
            <Table columnas={['Documento', 'Fecha de emisión', 'Versión']} hayFilas vacio="">
              {datos.documentos.map((d) => (
                <tr key={d.tipo}>
                  <Td>{DOCUMENTOS[d.tipo]}</Td>
                  <Td>{d.fechaEmision ? fechaCorta(d.fechaEmision) : dato(null)}</Td>
                  <Td>{d.version ?? '—'}</Td>
                </tr>
              ))}
            </Table>
          </section>
        </div>
      )}
    </Card>
  )
}
