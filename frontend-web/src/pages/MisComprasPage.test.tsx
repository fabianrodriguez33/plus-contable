// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import MisComprasPage, { ETIQUETA_CIERRE } from './MisComprasPage'
import { clienteIdActual } from '../services/authService'
import { clasificarCompra, listarCompras, type Compra } from '../services/compraService'
import { soles } from '../utils/societario'

vi.mock('../services/authService', () => ({ clienteIdActual: vi.fn() }))
vi.mock('../services/compraService', () => ({ listarCompras: vi.fn(), clasificarCompra: vi.fn() }))

const compra = (id: number, extra: Partial<Compra> = {}): Compra => ({
  id, clienteId: 4, periodo: '2026-11-01', proveedorRuc: '20100070970', proveedorRazonSocial: 'Proveedor SAC', comprobante: `F001-${id}`,
  fechaComprobante: '2026-11-05', importe: 1180, clasificada: false, tipo: null, descripcion: null, clasificadaEn: null, alertaCierre: false, ...extra,
})

afterEach(() => { cleanup(); vi.clearAllMocks() })

async function abrir(lista: Compra[]) {
  vi.mocked(clienteIdActual).mockReturnValue(4)
  vi.mocked(listarCompras).mockResolvedValue(lista)
  render(<MisComprasPage />)
  await screen.findAllByRole('listitem').catch(() => null)
}

describe('Mis compras (HU-25 · T210, T213)', () => {
  it('T210: el cliente ve las compras sin clasificar primero y las clasificadas con su tipo y descripción', async () => {
    await abrir([
      compra(1, { clasificada: true, tipo: 'ACTIVO_FIJO', descripcion: 'Laptop del área contable' }),
      compra(2),
    ])

    const tarjetas = screen.getAllByRole('listitem')
    expect(tarjetas[0].textContent).toContain('F001-2')
    expect(tarjetas[0].textContent).toContain('Sin clasificar')
    expect(tarjetas[0].textContent).toContain(soles(1180))
    expect(tarjetas[1].textContent).toContain('Activo fijo')
    expect(tarjetas[1].textContent).toContain('Descripción: Laptop del área contable')
    expect(within(tarjetas[1]).queryByRole('button', { name: 'Clasificar' })).toBeNull()   // una clasificada no se cambia
  })

  it('T213: pide solo las compras de la empresa de su propio token', async () => {
    await abrir([compra(1)])
    expect(listarCompras).toHaveBeenCalledTimes(1)
    expect(listarCompras).toHaveBeenCalledWith(4)
  })

  it('el cliente clasifica con tipo y descripción y la lista se recarga', async () => {
    vi.mocked(clasificarCompra).mockResolvedValue(compra(2, { clasificada: true, tipo: 'MERCADERIA_O_GASTO', descripcion: 'Útiles' }))
    await abrir([compra(2)])
    vi.mocked(listarCompras).mockResolvedValue([compra(2, { clasificada: true, tipo: 'MERCADERIA_O_GASTO', descripcion: 'Útiles' })])

    fireEvent.click(screen.getByRole('button', { name: 'Clasificar' }))
    fireEvent.change(screen.getByLabelText('Tipo de compra'), { target: { value: 'MERCADERIA_O_GASTO' } })
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: '  Útiles  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar clasificación' }))

    await waitFor(() => expect(clasificarCompra).toHaveBeenCalledWith(4, 2, 'MERCADERIA_O_GASTO', 'Útiles'))
    await screen.findByText('Mercadería o gasto')
    expect(screen.queryByRole('button', { name: 'Guardar clasificación' })).toBeNull()
  })

  it('sin tipo o sin descripción no envía y avisa', async () => {
    await abrir([compra(2)])
    fireEvent.click(screen.getByRole('button', { name: 'Clasificar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar clasificación' }))

    expect(screen.getByText('Elija si es mercadería o gasto, o activo fijo')).toBeTruthy()
    expect(screen.getByText('La descripción es obligatoria')).toBeTruthy()
    expect(clasificarCompra).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza la clasificación muestra su mensaje y no recarga como si hubiera salido bien', async () => {
    vi.mocked(clasificarCompra).mockRejectedValue(new Error('x'))
    await abrir([compra(2)])
    fireEvent.click(screen.getByRole('button', { name: 'Clasificar' }))
    fireEvent.change(screen.getByLabelText('Tipo de compra'), { target: { value: 'ACTIVO_FIJO' } })
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Laptop' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar clasificación' }))

    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
    expect(listarCompras).toHaveBeenCalledTimes(1)
  })

  it('cerca del cierre las pendientes se marcan y se avisa cuántas faltan; las clasificadas no', async () => {
    await abrir([
      compra(1, { alertaCierre: true }),
      compra(2, { alertaCierre: true }),
      compra(3, { clasificada: true, tipo: 'ACTIVO_FIJO', descripcion: 'x' }),
    ])
    expect(screen.getAllByText(ETIQUETA_CIERRE)).toHaveLength(2)
    expect(screen.getByText(/Tiene 2 compras sin clasificar/)).toBeTruthy()
  })

  it('lejos del cierre no hay alerta', async () => {
    await abrir([compra(1)])
    expect(screen.queryByText(ETIQUETA_CIERRE)).toBeNull()
    expect(screen.queryByText(/sin clasificar y el cierre/)).toBeNull()
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
