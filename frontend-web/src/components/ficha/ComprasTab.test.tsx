// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import ComprasTab from './ComprasTab'
import { listarCompras, registrarCompra, validarCompra, type Compra } from '../../services/compraService'
import { soles } from '../../utils/societario'

vi.mock('../../services/compraService', () => ({ listarCompras: vi.fn(), registrarCompra: vi.fn(), validarCompra: vi.fn() }))

const compra = (id: number, extra: Partial<Compra> = {}): Compra => ({
  id, clienteId: 4, periodo: '2026-11-01', proveedorRuc: '20100070970', proveedorRazonSocial: 'Proveedor SAC', comprobante: `F001-${id}`,
  fechaComprobante: '2026-11-05', importe: 1180, estado: 'SIN_CLASIFICAR', tipo: null, descripcion: null, clasificadaEn: null,
  validadaPor: null, validadaEn: null, alertaCierre: false, ...extra,
})
const propuesta = (id: number, extra: Partial<Compra> = {}) =>
  compra(id, { estado: 'PROPUESTA', tipo: 'ACTIVO_FIJO', descripcion: 'Laptop del área contable', ...extra })

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Compras en la ficha del cliente (HU-25 · T211)', () => {
  it('T211: el contador ve la propuesta del cliente por validar primero, luego las sin clasificar y las validadas', async () => {
    vi.mocked(listarCompras).mockResolvedValue([
      compra(1, { estado: 'VALIDADA', tipo: 'MERCADERIA_O_GASTO', descripcion: 'Útiles' }),
      compra(2),
      propuesta(3),
    ])
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={vi.fn()} />)

    await screen.findByText('F001-3')
    const filas = screen.getAllByRole('row').slice(1)
    expect(listarCompras).toHaveBeenCalledWith(4)
    expect(filas[0].textContent).toContain('Propuesta por validar')
    expect(filas[0].textContent).toContain('Activo fijo')
    expect(filas[0].textContent).toContain('Laptop del área contable')
    expect(filas[0].textContent).toContain(soles(1180))
    expect(filas[1].textContent).toContain('Sin clasificar')
    expect(filas[2].textContent).toContain('Validada')
    expect(filas[2].textContent).toContain('Mercadería o gasto')
  })

  it('solo la propuesta tiene botón «Validar»; el contador no puede clasificar', async () => {
    vi.mocked(listarCompras).mockResolvedValue([compra(2), propuesta(3), compra(4, { estado: 'VALIDADA', tipo: 'ACTIVO_FIJO', descripcion: 'x' })])
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={vi.fn()} />)
    await screen.findByText('F001-3')
    expect(screen.getAllByRole('button', { name: 'Validar' })).toHaveLength(1)
    expect(screen.queryByRole('button', { name: /clasificar/i })).toBeNull()
  })

  it('valida la propuesta, avisa cómo quedó y recarga', async () => {
    vi.mocked(listarCompras).mockResolvedValue([propuesta(3)])
    vi.mocked(validarCompra).mockResolvedValue(propuesta(3, { estado: 'VALIDADA' }))
    const setOk = vi.fn()
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={setOk} />)
    await screen.findByText('F001-3')

    fireEvent.click(screen.getByRole('button', { name: 'Validar' }))

    await waitFor(() => expect(validarCompra).toHaveBeenCalledWith(4, 3))
    await waitFor(() => expect(setOk).toHaveBeenCalledWith('Compra F001-3 validada como Activo fijo'))
    expect(listarCompras).toHaveBeenCalledTimes(2)
  })

  it('si el servidor rechaza la validación muestra su mensaje', async () => {
    vi.mocked(listarCompras).mockResolvedValue([propuesta(3)])
    vi.mocked(validarCompra).mockRejectedValue(new Error('x'))
    const setError = vi.fn()
    render(<ComprasTab clienteId={4} setError={setError} setOk={vi.fn()} />)
    await screen.findByText('F001-3')
    fireEvent.click(screen.getByRole('button', { name: 'Validar' }))
    await waitFor(() => expect(setError).toHaveBeenCalledWith('Error inesperado'))
  })

  it('la alerta de fin de mes es del contador: marca las compras sin validar y avisa cuántas son', async () => {
    vi.mocked(listarCompras).mockResolvedValue([
      compra(1, { alertaCierre: true }), propuesta(2, { alertaCierre: true }), compra(3, { estado: 'VALIDADA', tipo: 'ACTIVO_FIJO', descripcion: 'x' }),
    ])
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={vi.fn()} />)
    await screen.findByText('F001-2')
    expect(screen.getAllByText('Fin de mes cerca')).toHaveLength(2)
    expect(screen.getByText(/2 compras sin validar y falta poco para el último día del mes/)).toBeTruthy()
  })

  it('lejos del fin de mes no hay alerta', async () => {
    vi.mocked(listarCompras).mockResolvedValue([compra(1)])
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={vi.fn()} />)
    await screen.findByText('F001-1')
    expect(screen.queryByText('Fin de mes cerca')).toBeNull()
    expect(screen.queryByText(/falta poco/)).toBeNull()
  })

  it('registra una compra con el mes como primer día, recarga y avisa que queda sin clasificar', async () => {
    vi.mocked(listarCompras).mockResolvedValue([])
    vi.mocked(registrarCompra).mockResolvedValue(compra(5))
    const setOk = vi.fn()
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={setOk} />)
    await screen.findByText('No hay compras registradas para este cliente.')

    fireEvent.change(screen.getByLabelText('Mes tributario'), { target: { value: '2026-11' } })
    fireEvent.change(screen.getByLabelText('RUC del proveedor'), { target: { value: ' 20100070970 ' } })
    fireEvent.change(screen.getByLabelText('Comprobante'), { target: { value: 'F001-5' } })
    fireEvent.change(screen.getByLabelText('Fecha del comprobante'), { target: { value: '2026-11-05' } })
    fireEvent.change(screen.getByLabelText('Importe (S/)'), { target: { value: '1180.50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Registrar compra' }))

    await waitFor(() => expect(registrarCompra).toHaveBeenCalledWith(4, {
      periodo: '2026-11-01', proveedorRuc: '20100070970', comprobante: 'F001-5', fechaComprobante: '2026-11-05', importe: 1180.5 }))
    await waitFor(() => expect(setOk).toHaveBeenCalledWith(expect.stringContaining('queda sin clasificar')))
    expect(listarCompras).toHaveBeenCalledTimes(2)
  })

  it('con datos incompletos no envía', async () => {
    vi.mocked(listarCompras).mockResolvedValue([])
    const setError = vi.fn()
    render(<ComprasTab clienteId={4} setError={setError} setOk={vi.fn()} />)
    await screen.findByText('No hay compras registradas para este cliente.')
    fireEvent.click(screen.getByRole('button', { name: 'Registrar compra' }))
    expect(setError).toHaveBeenCalledWith(expect.stringContaining('Complete'))
    expect(registrarCompra).not.toHaveBeenCalled()
  })
})
