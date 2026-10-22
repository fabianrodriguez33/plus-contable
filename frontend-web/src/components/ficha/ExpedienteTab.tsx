import { ChangeEvent, useCallback, useEffect, useState } from 'react'
import { Download, FileText, Trash2, Upload } from 'lucide-react'
import { mensajeError } from '../../services/apiClient'
import { descargarDocumento, eliminarDocumento, listarDocumentos, subirDocumento } from '../../services/clienteService'
import type { Documento, TipoDocumento } from '../../types'
import Button from '../Button'
import Table, { Td } from '../Table'

const TIPOS_DOC: { tipo: TipoDocumento; etiqueta: string }[] = [
  { tipo: 'MINUTA', etiqueta: 'Minuta de constitución' },
  { tipo: 'COPIA_LITERAL', etiqueta: 'Copia literal' },
  { tipo: 'VIGENCIA_PODERES', etiqueta: 'Vigencia de poderes' },
  { tipo: 'FICHA_RUC', etiqueta: 'Ficha RUC' },
]

interface Props {
  clienteId: number
  setError: (m: string) => void
}

export default function ExpedienteTab({ clienteId, setError }: Props) {
  const [docs, setDocs] = useState<Documento[]>([])

  const cargar = useCallback(() => {
    listarDocumentos(clienteId).then(setDocs).catch((e) => setError(mensajeError(e)))
  }, [clienteId, setError])

  useEffect(cargar, [cargar])

  const subir = async (tipo: TipoDocumento, e: ChangeEvent<HTMLInputElement>) => {
    const archivo = e.target.files?.[0]
    e.target.value = ''
    if (!archivo) return
    setError('')
    try {
      await subirDocumento(clienteId, tipo, archivo)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  const borrar = async (d: Documento) => {
    setError('')
    try {
      await eliminarDocumento(clienteId, d.id)
      cargar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <div className="space-y-5">
      <p className="text-sm text-slate-500">Formatos permitidos: PDF, PNG, JPG, DOC, DOCX (máx. 10 MB).</p>
      {TIPOS_DOC.map(({ tipo, etiqueta }) => {
        const delTipo = docs.filter((d) => d.tipo === tipo)
        return (
          <div key={tipo} className="rounded-lg border border-slate-200 p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <strong className="flex items-center gap-2 text-slate-800"><FileText size={18} /> {etiqueta}</strong>
              <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
                <Upload size={16} /> Subir archivo
                <input type="file" className="sr-only" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx"
                  onChange={(e) => subir(tipo, e)} />
              </label>
            </div>
            <Table columnas={['Archivo', 'Tamaño', 'Subido', '']} hayFilas={delTipo.length > 0}
              vacio="Sin documentos cargados.">
              {delTipo.map((d) => (
                <tr key={d.id}>
                  <Td>{d.nombreArchivo}</Td>
                  <Td>{(d.tamanoBytes / 1024).toFixed(0)} KB</Td>
                  <Td>{new Date(d.subidoEn).toLocaleString()}</Td>
                  <Td>
                    <div className="flex gap-2">
                      <Button variant="secondary" aria-label="Descargar"
                        onClick={() => descargarDocumento(d).catch((e) => setError(mensajeError(e)))}>
                        <Download size={16} />
                      </Button>
                      <Button variant="danger" aria-label="Eliminar" onClick={() => borrar(d)}>
                        <Trash2 size={16} />
                      </Button>
                    </div>
                  </Td>
                </tr>
              ))}
            </Table>
          </div>
        )
      })}
    </div>
  )
}
