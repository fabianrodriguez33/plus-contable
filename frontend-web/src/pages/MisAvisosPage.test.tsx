// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import MisAvisosPage from './MisAvisosPage'
import { clienteIdActual } from '../services/authService'
import { listarAvisosPago } from '../services/avisoClienteService'

vi.mock('../services/authService', () => ({ clienteIdActual: vi.fn() }))
vi.mock('../services/avisoClienteService', () => ({ listarAvisosPago: vi.fn() }))

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Avisos de pago en el portal del cliente (HU-33)', () => {
  it('el cliente ve los avisos de su empresa (la que sale de su token)', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(listarAvisosPago).mockResolvedValue([
      { id: 1, obligacionId: 9, tributo: 'IGV', monto: 1500.5, fechaVencimiento: '2026-11-20', fechaAviso: '2026-11-17' }])

    render(<MisAvisosPage />)

    await screen.findByText('IGV')
    expect(listarAvisosPago).toHaveBeenCalledWith(4)
    expect(screen.getByText('20/11/2026')).toBeTruthy()
    expect(screen.queryByRole('button')).toBeNull() // solo lectura
  })

  it('sin avisos lo dice', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(listarAvisosPago).mockResolvedValue([])

    render(<MisAvisosPage />)

    expect(await screen.findByText(/No hay avisos de pago/)).toBeTruthy()
  })

  it('una cuenta sin empresa no consulta nada y lo indica', () => {
    vi.mocked(clienteIdActual).mockReturnValue(null)

    render(<MisAvisosPage />)

    expect(screen.getByRole('alert').textContent).toContain('no está vinculada a una empresa')
    expect(listarAvisosPago).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza, muestra el error', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(listarAvisosPago).mockRejectedValue(new Error('x'))

    render(<MisAvisosPage />)

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
  })
})
