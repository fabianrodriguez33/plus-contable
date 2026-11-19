// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import MisComprasPage from './MisComprasPage'
import { clienteIdActual } from '../services/authService'
import { proponerClasificacion, listarCompras, type Compra } from '../services/compraService'
import { soles } from '../utils/societario'

vi.mock('../services/authService', () => ({ clienteIdActual: vi.fn() }))
vi.mock('../services/compraService', () => ({ listarCompras: vi.fn(), proponerClasificacion: vi.fn() }))

const compra = (id: number, extra: Partial<Compra> = {}): Compra => ({
  id, clienteId: 4, periodo: '2026-11-01', proveedorRuc: '20100070970', proveedorRazonSocial: 'Proveedor SAC', comprobante: `F001-${id}`,
  fechaComprobante: '2026-11-05', importe: 1180, estado: 'SIN_CLASIFICAR', tipo: null, descripcion: null, clasificadaEn: null,
  validadaPor: null, validadaEn: null, alertaCierre: false, ...extra,
})
const propuesta = (id: number) => compra(id, { estado: 'PROPUESTA', tipo: 'ACTIVO_FIJO', descripcion: 'Laptop del área contable' })
const validada = (id: number) => compra(id, { estado: 'VALIDADA', tipo: 'MERCADERIA_O_GASTO', descripcion: 'Útiles', validadaEn: '2026-11-18T12:00:00Z' })

afterEach(() => { cleanup(); vi.clearAllMocks() })

async function abrir(lista: Compra[]) {
  vi.mocked(clienteIdActual).mockReturnValue(4)
  vi.mocked(listarCompras).mockResolvedValue(lista)
  render(<MisComprasPage />)
  await screen.findAllByRole('listitem').catch(() => null)
}

describe('Mis compras (HU-25 · T210, T213)', () => {
  it('T210: las sin clasificar primero, luego las propuestas pendientes de validación y al final las validadas', async () => {
    await abrir([validada(1), propuesta(2), compra(3)])

    const tarjetas = screen.getAllByRole('listitem')
    expect(tarjetas[0].textContent).toContain('F001-3')
    expect(tarjetas[0].textContent).toContain('Sin clasificar')
    expect(tarjetas[0].textContent).toContain(soles(1180))
    expect(tarjetas[1].textContent).toContain('Pendiente de validación del contador')
    expect(tarjetas[1].textContent).toContain('Su propuesta: Activo fijo')
    expect(tarjetas[1].textContent).toContain('Descripción: Laptop del área contable')
    expect(tarjetas[2].textContent).toContain('Validada')
    expect(tarjetas[2].textContent).toContain('Clasificación: Mercadería o gasto')
    expect(within(tarjetas[2]).queryByRole('button')).toBeNull()   // una validada ya no se cambia
  })

  it('T213: pide solo las compras de la empresa de su propio token', async () => {
    await abrir([compra(1)])
    expect(listarCompras).toHaveBeenCalledTimes(1)
    expect(listarCompras).toHaveBeenCalledWith(4)
  })

  it('el cliente propone con tipo y descripción y la lista se recarga con la propuesta pendiente de validación', async () => {
    vi.mocked(proponerClasificacion).mockResolvedValue(propuesta(2))
    await abrir([compra(2)])
    vi.mocked(listarCompras).mockResolvedValue([compra(2, { estado: 'PROPUESTA', tipo: 'MERCADERIA_O_GASTO', descripcion: 'Útiles' })])

    fireEvent.click(screen.getByRole('button', { name: 'Proponer clasificación' }))
    fireEvent.change(screen.getByLabelText('Tipo de compra'), { target: { value: 'MERCADERIA_O_GASTO' } })
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: '  Útiles  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar propuesta' }))

    await waitFor(() => expect(proponerClasificacion).toHaveBeenCalledWith(4, 2, 'MERCADERIA_O_GASTO', 'Útiles'))
    await screen.findByText('Pendiente de validación del contador')
    expect(screen.queryByRole('button', { name: 'Guardar propuesta' })).toBeNull()
  })

  it('mientras el contador no valide, puede cambiar su propuesta (el formulario parte de lo ya propuesto)', async () => {
    await abrir([propuesta(2)])
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar propuesta' }))
    expect((screen.getByLabelText('Tipo de compra') as HTMLSelectElement).value).toBe('ACTIVO_FIJO')
    expect((screen.getByLabelText('Descripción') as HTMLTextAreaElement).value).toBe('Laptop del área contable')
  })

  it('sin tipo o sin descripción no envía y avisa', async () => {
    await abrir([compra(2)])
    fireEvent.click(screen.getByRole('button', { name: 'Proponer clasificación' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar propuesta' }))

    expect(screen.getByText('Elija si es mercadería o gasto, o activo fijo')).toBeTruthy()
    expect(screen.getByText('La descripción es obligatoria')).toBeTruthy()
    expect(proponerClasificacion).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza la propuesta muestra su mensaje y no recarga como si hubiera salido bien', async () => {
    vi.mocked(proponerClasificacion).mockRejectedValue(new Error('x'))
    await abrir([compra(2)])
    fireEvent.click(screen.getByRole('button', { name: 'Proponer clasificación' }))
    fireEvent.change(screen.getByLabelText('Tipo de compra'), { target: { value: 'ACTIVO_FIJO' } })
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Laptop' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar propuesta' }))

    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
    expect(listarCompras).toHaveBeenCalledTimes(1)
  })

  it('la alerta de fin de mes es solo del contador: aunque el servidor la marque, el cliente no ve ni aviso ni marca', async () => {
    await abrir([compra(1, { alertaCierre: true }), compra(2, { alertaCierre: true })])
    expect(screen.queryByText(/Cierre/i)).toBeNull()
    expect(screen.queryByText(/Fin de mes/i)).toBeNull()
    expect(screen.queryByText(/sin clasificar y/)).toBeNull()
  })

  it('sin compras lo dice; una cuenta sin empresa no pide nada', async () => {
    await abrir([])
    expect(screen.getByText('Todavía no hay compras registradas por su contador.')).toBeTruthy()
    cleanup()
    vi.mocked(clienteIdActual).mockReturnValue(null)
    vi.mocked(listarCompras).mockClear()
    render(<MisComprasPage />)
    expect(screen.getByRole('alert').textContent).toMatch(/no está vinculada a una empresa/)
    expect(listarCompras).not.toHaveBeenCalled()
  })
})
