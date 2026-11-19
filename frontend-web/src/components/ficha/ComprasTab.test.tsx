// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import ComprasTab from './ComprasTab'
import { listarCompras, registrarCompra, type Compra } from '../../services/compraService'
import { soles } from '../../utils/societario'

vi.mock('../../services/compraService', () => ({ listarCompras: vi.fn(), registrarCompra: vi.fn() }))

const compra = (id: number, extra: Partial<Compra> = {}): Compra => ({
  id, clienteId: 4, periodo: '2026-11-01', proveedorRuc: '20100070970', proveedorRazonSocial: 'Proveedor SAC', comprobante: `F001-${id}`,
  fechaComprobante: '2026-11-05', importe: 1180, clasificada: false, tipo: null, descripcion: null, clasificadaEn: null, alertaCierre: false, ...extra,
})

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Compras en la ficha del cliente (HU-25 · T211)', () => {
  it('T211: el contador ve cada compra ya clasificada con su tipo y descripción, y las pendientes primero', async () => {
    vi.mocked(listarCompras).mockResolvedValue([
      compra(1, { clasificada: true, tipo: 'ACTIVO_FIJO', descripcion: 'Laptop del área contable' }),
      compra(2, { alertaCierre: true }),
    ])
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={vi.fn()} />)

    await screen.findByText('F001-2')
    const filas = screen.getAllByRole('row').slice(1)
    expect(listarCompras).toHaveBeenCalledWith(4)
    expect(filas[0].textContent).toContain('Sin clasificar')
    expect(filas[0].textContent).toContain('Cierre cerca')
    expect(filas[1].textContent).toContain('Activo fijo')
    expect(filas[1].textContent).toContain('Laptop del área contable')
    expect(filas[1].textContent).toContain(soles(1180))
  })

  it('el contador no tiene botón para clasificar (solo el cliente clasifica)', async () => {
    vi.mocked(listarCompras).mockResolvedValue([compra(2)])
    render(<ComprasTab clienteId={4} setError={vi.fn()} setOk={vi.fn()} />)
    await screen.findByText('F001-2')
    expect(screen.queryByRole('button', { name: /clasificar/i })).toBeNull()
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

  it('si el servidor rechaza la carga muestra su mensaje', async () => {
    vi.mocked(listarCompras).mockResolvedValue([])
    vi.mocked(registrarCompra).mockRejectedValue(new Error('x'))
    const setError = vi.fn()
    render(<ComprasTab clienteId={4} setError={setError} setOk={vi.fn()} />)
    await screen.findByText('No hay compras registradas para este cliente.')
    fireEvent.change(screen.getByLabelText('Mes tributario'), { target: { value: '2026-11' } })
    fireEvent.change(screen.getByLabelText('RUC del proveedor'), { target: { value: '20100070970' } })
    fireEvent.change(screen.getByLabelText('Comprobante'), { target: { value: 'F1' } })
    fireEvent.change(screen.getByLabelText('Fecha del comprobante'), { target: { value: '2026-11-05' } })
    fireEvent.change(screen.getByLabelText('Importe (S/)'), { target: { value: '10' } })
    fireEvent.click(screen.getByRole('button', { name: 'Registrar compra' }))
    await waitFor(() => expect(setError).toHaveBeenCalledWith('Error inesperado'))
  })
})
