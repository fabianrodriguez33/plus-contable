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

afterEach(() => { cleanup(); vi.clearAllMocks() })

/** Cartera en celular: la tabla se desplaza de lado y «Razón social» queda fija; jsdom no calcula el diseño, así que se protegen las clases. */
describe('Cartera de clientes · pantallas angostas', () => {
  it('la tabla se desplaza de lado, con teclado, y la primera columna queda fija', async () => {
    vi.mocked(listarClientes).mockResolvedValue([{ id: 1, ruc: '20000000001', razonSocial: 'Cliente de prueba 1 S.A.C.' } as Cliente])
    vi.mocked(listarSolicitudesRestablecimiento).mockResolvedValue([])
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)

    const region = await screen.findByRole('region', { name: 'Cartera de clientes' })
    expect(region.className).toContain('overflow-x-auto')
    expect(region.getAttribute('tabindex')).toBe('0')
    expect(screen.getByRole('columnheader', { name: 'Razón social' }).className).toContain('sticky')
    expect(screen.getByRole('cell', { name: 'Cliente de prueba 1 S.A.C.' }).className).toContain('sticky')
  })
})
