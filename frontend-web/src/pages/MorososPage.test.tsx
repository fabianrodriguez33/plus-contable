// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError, AxiosResponse } from 'axios'
import MorososPage from './MorososPage'
import { listarMorosos, type Moroso } from '../services/expedienteService'
import { soles } from '../utils/societario'

vi.mock('../services/expedienteService', () => ({ listarMorosos: vi.fn() }))

const moroso = (clienteId: number, razonSocial: string, montoVencido: number, diasAtraso: number): Moroso => ({
  clienteId, ruc: `2012345678${clienteId}`, razonSocial, montoVencido, cobrosVencidos: 2, vencidoDesde: '2026-10-15', diasAtraso,
})

function montar() {
  render(<MemoryRouter><MorososPage /></MemoryRouter>)
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Clientes morosos (HU-31)', () => {
  it('criterio 1: cada cliente con cobros vencidos aparece con su monto y sus días de atraso, con enlace a su ficha', async () => {
    vi.mocked(listarMorosos).mockResolvedValue([moroso(4, 'Acme SAC', 750, 27), moroso(5, 'Beta SAC', 100, 1)])
    montar()

    const acme = await screen.findByRole('link', { name: 'Acme SAC' })
    expect(acme.getAttribute('href')).toBe('/clientes/4')
    expect(screen.getByText((_, el) => el?.tagName === 'TD' && el.textContent === soles(750))).toBeTruthy()
    expect(screen.getByText('27 días')).toBeTruthy()
    expect(screen.getByText('1 día')).toBeTruthy()
    expect(screen.getAllByText('15/10/2026')).toHaveLength(2)
    expect(screen.getByText((_, el) => el?.tagName === 'P' && el.textContent === `2 clientes morosos · total vencido ${soles(850)}`)).toBeTruthy()
  })

  it('con un solo moroso lo dice en singular', async () => {
    vi.mocked(listarMorosos).mockResolvedValue([moroso(4, 'Acme SAC', 750, 27)])
    montar()

    await screen.findByRole('link', { name: 'Acme SAC' })
    expect(screen.getByText((_, el) => el?.tagName === 'P' && el.textContent === `1 cliente moroso · total vencido ${soles(750)}`)).toBeTruthy()
  })

  it('criterio 2: si todos están al día lo dice y no muestra a nadie', async () => {
    vi.mocked(listarMorosos).mockResolvedValue([])
    montar()

    expect(await screen.findByText('No hay clientes morosos: todos están al día.')).toBeTruthy()
    expect(screen.queryByRole('link')).toBeNull()
  })

  it('criterio 3: si el servidor niega el acceso, se muestra su mensaje y no la lista', async () => {
    vi.mocked(listarMorosos).mockRejectedValue(new AxiosError('403', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 403, data: { mensaje: 'Acceso denegado' } } as AxiosResponse))
    montar()

    expect((await screen.findByRole('alert')).textContent).toBe('Acceso denegado')
    expect(screen.getByText('No se pudo cargar la lista.')).toBeTruthy()
    expect(screen.queryByText(/todos están al día/)).toBeNull()
  })
})
