// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import AvisosPagoTab from './AvisosPagoTab'
import { listarAvisosPago, type AvisoPago } from '../../services/avisoClienteService'

vi.mock('../../services/avisoClienteService', () => ({ listarAvisosPago: vi.fn() }))

const aviso = (a: Partial<AvisoPago> & Pick<AvisoPago, 'id'>): AvisoPago => ({
  obligacionId: 9, tributo: 'IGV', monto: 1500.5, fechaVencimiento: '2026-11-20', fechaAviso: '2026-11-17', ...a,
})

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Avisos de pago al cliente (HU-33)', () => {
  it('criterio 2: cada aviso muestra el tributo, el monto y la fecha de vencimiento', async () => {
    vi.mocked(listarAvisosPago).mockResolvedValue([aviso({ id: 1 }), aviso({ id: 2, tributo: 'RENTA', monto: 300, fechaVencimiento: '2026-11-25' })])
    render(<AvisosPagoTab clienteId={4} setError={vi.fn()} />)

    await screen.findByText('Renta')
    expect(listarAvisosPago).toHaveBeenCalledWith(4)
    const filas = screen.getAllByRole('row').slice(1).map((f) => f.textContent?.replace(/\u00a0/g, ' '))
    expect(filas).toEqual(['17/11/2026IGVS/ 1,500.5020/11/2026', '17/11/2026RentaS/ 300.0025/11/2026'])
  })

  it('sin avisos lo indica', async () => {
    vi.mocked(listarAvisosPago).mockResolvedValue([])
    render(<AvisosPagoTab clienteId={4} setError={vi.fn()} />)
    expect(await screen.findByText(/No hay avisos de pago/)).toBeTruthy()
  })

  it('si falla la carga muestra el error', async () => {
    vi.mocked(listarAvisosPago).mockRejectedValue(new Error('x'))
    const setError = vi.fn()
    render(<AvisosPagoTab clienteId={4} setError={setError} />)
    await waitFor(() => expect(setError).toHaveBeenCalledWith('Error inesperado'))
  })
})
