// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from './DashboardPage'
import { listarClientes } from '../services/clienteService'
import { listarSolicitudesRestablecimiento } from '../services/cuentaClienteService'
import type { Cliente } from '../types'

vi.mock('../services/clienteService', () => ({ listarClientes: vi.fn() }))
vi.mock('../services/cuentaClienteService', () => ({ listarSolicitudesRestablecimiento: vi.fn() }))

function montar() {
  render(<MemoryRouter><DashboardPage /></MemoryRouter>)
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Solicitudes de nueva contraseña en la cartera (HU-41)', () => {
  it('muestra a qué cliente restablecer el acceso, con enlace a su ficha', async () => {
    vi.mocked(listarClientes).mockResolvedValue([{ id: 4, ruc: '20123456789', razonSocial: 'Acme SAC' } as Cliente])
    vi.mocked(listarSolicitudesRestablecimiento).mockResolvedValue([
      { clienteId: 4, correo: 'contacto@acme.pe', solicitadaEn: '2026-11-11T09:30:00' },
    ])
    montar()

    const enlace = await screen.findByRole('link', { name: 'Acme SAC' })
    expect(enlace.getAttribute('href')).toBe('/clientes/4')
    expect(screen.getByText(/contacto@acme\.pe/)).toBeTruthy()
    expect(screen.getByText(/Restablecer acceso/)).toBeTruthy()
  })

  it('sin solicitudes, o si el servidor la niega, no muestra el aviso', async () => {
    vi.mocked(listarClientes).mockResolvedValue([])
    vi.mocked(listarSolicitudesRestablecimiento).mockRejectedValue(new Error('403'))
    montar()

    expect(await screen.findByText('Aún no hay clientes registrados.')).toBeTruthy()
    expect(screen.queryByLabelText('Solicitudes de nueva contraseña')).toBeNull()
  })
})
