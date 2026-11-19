// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import MiEmpresaPage from './MiEmpresaPage'
import { clienteIdActual } from '../services/authService'
import { obtenerDatosEmpresa, type DatosEmpresa } from '../services/datosEmpresaService'

vi.mock('../services/authService', () => ({ clienteIdActual: vi.fn() }))
vi.mock('../services/datosEmpresaService', () => ({ obtenerDatosEmpresa: vi.fn() }))

const completos: DatosEmpresa = {
  clienteId: 4, ruc: '20123456789', razonSocial: 'Acme SAC', regimenTributario: 'RER',
  direccion: 'Av. Lima 123', telefono: '999888777', email: 'acme@acme.pe',
  socios: [{ nombreCompleto: 'Ana Pérez', cargo: 'Gerente', porcentaje: 60 }, { nombreCompleto: 'Luis Díaz', cargo: null, porcentaje: 40 }],
  documentos: [
    { tipo: 'MINUTA', id: 7, version: 2, fechaEmision: '2026-02-02' },
    { tipo: 'VIGENCIA_PODERES', id: null, version: null, fechaEmision: null },
    { tipo: 'FICHA_RUC', id: 9, version: 1, fechaEmision: '2026-10-01' },
  ],
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Mi empresa (HU-35)', () => {
  it('criterio 1: muestra régimen, socios y documentos vigentes de la empresa del token', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValue(completos)
    render(<MiEmpresaPage />)

    await screen.findByText('Acme SAC')
    expect(obtenerDatosEmpresa).toHaveBeenCalledWith(4)
    expect(screen.getByText('Régimen Especial del Impuesto a la Renta (RER)')).toBeTruthy()
    const socios = within(screen.getByRole('region', { name: 'Socios' })).getAllByRole('row').slice(1).map((f) => f.textContent)
    expect(socios).toEqual(['Ana PérezGerente60.00 %', 'Luis DíazPendiente de registrar40.00 %'])
    const docs = within(screen.getByRole('region', { name: 'Documentos vigentes' })).getAllByRole('row').slice(1).map((f) => f.textContent)
    expect(docs).toEqual(['Minuta de constitución02/02/20262', 'Vigencia de poderesPendiente de registrar—', 'Ficha RUC01/10/20261'])
  })

  it('criterio 2: es solo lectura, sin campos ni botones', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValue(completos)
    render(<MiEmpresaPage />)

    await screen.findByText('Acme SAC')
    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(screen.queryAllByRole('combobox')).toHaveLength(0)
  })

  it('criterio 3: un dato faltante se muestra como «pendiente de registrar»', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValue({ ...completos, direccion: null, telefono: null, email: null, socios: [],
      documentos: completos.documentos.map((d) => ({ ...d, id: null, version: null, fechaEmision: null })) })
    render(<MiEmpresaPage />)

    await screen.findByText('Acme SAC')
    // dirección, teléfono, correo, socios y los 3 documentos
    expect(screen.getAllByText('Pendiente de registrar')).toHaveLength(7)
  })

  it('criterio 4: sin empresa en la cuenta no pide datos y lo dice', () => {
    vi.mocked(clienteIdActual).mockReturnValue(null)
    render(<MiEmpresaPage />)

    expect(screen.getByRole('alert').textContent).toMatch(/no está vinculada a una empresa/)
    expect(obtenerDatosEmpresa).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza la petición muestra el mensaje', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockRejectedValue(new Error('x'))
    render(<MiEmpresaPage />)

    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
  })
})
