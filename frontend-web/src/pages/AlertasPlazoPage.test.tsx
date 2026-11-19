// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError, AxiosResponse } from 'axios'
import AlertasPlazoPage from './AlertasPlazoPage'
import { listarAlertasPlazo, type AlertaPlazo } from '../services/alertaPlazoService'

vi.mock('../services/alertaPlazoService', () => ({ listarAlertasPlazo: vi.fn() }))

const alerta = (cambios: Partial<AlertaPlazo>): AlertaPlazo => ({
  id: 1, notificacionId: 40, clienteId: 4, ruc: '20123456789', razonSocial: 'Acme SAC', tipo: 'CARTA_INDUCTIVA',
  plazoRespuesta: '2026-11-20', diasAntes: 3, fechaAlerta: '2026-11-17', diasRestantes: 3, ...cambios,
})

function montar() {
  render(<MemoryRouter><AlertasPlazoPage /></MemoryRouter>)
}

beforeEach(() => {
  vi.mocked(listarAlertasPlazo).mockResolvedValue([
    alerta({ id: 1, razonSocial: 'Acme SAC', diasRestantes: 3, diasAntes: 3 }),
    alerta({ id: 2, notificacionId: 41, clienteId: 5, ruc: '20123456780', razonSocial: 'Beta SAC', tipo: 'ESQUELA', plazoRespuesta: '2026-11-18', diasRestantes: 1, diasAntes: 1 }),
    alerta({ id: 3, notificacionId: 42, clienteId: 6, ruc: '20123456781', razonSocial: 'Gama SAC', tipo: 'COMUNICADO', plazoRespuesta: '2026-11-17', diasRestantes: 0, diasAntes: 0 }),
    alerta({ id: 4, notificacionId: 43, clienteId: 7, ruc: '20123456782', razonSocial: 'Delta SAC', plazoRespuesta: '2026-11-25', diasRestantes: 8, diasAntes: 5 }),
  ])
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Alertas de plazo de respuesta (HU-19)', () => {
  it('criterio 3: cada alerta muestra el cliente y los días que faltan', async () => {
    montar()

    const filas = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    expect(within(filas[0]).getByRole('link', { name: 'Acme SAC' })).toBeTruthy()
    expect(within(filas[0]).getByText('20123456789')).toBeTruthy()
    expect(within(filas[0]).getByText('Faltan 3 días')).toBeTruthy()
    expect(within(filas[1]).getByText('Falta 1 día')).toBeTruthy()
    expect(within(filas[2]).getByText('Vence hoy')).toBeTruthy()
    expect(within(filas[3]).getByText('Faltan 8 días')).toBeTruthy()
  })

  it('muestra el tipo de notificación, el plazo y el escalón de la alerta', async () => {
    montar()

    const filas = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    expect(within(filas[0]).getByText('Carta inductiva')).toBeTruthy()
    expect(within(filas[1]).getByText('Esquela')).toBeTruthy()
    expect(within(filas[2]).getByText('Comunicado')).toBeTruthy()
    expect(within(filas[0]).getByText('20/11/2026')).toBeTruthy()
    expect(filas[0].textContent).toContain('Alerta de 3 días antes · 17/11/2026')
    expect(filas[1].textContent).toContain('Alerta de 1 día antes')
    expect(filas[2].textContent).toContain('Alerta del día del plazo')
  })

  it('criterio 2: una notificación con varias alertas escalonadas las muestra todas', async () => {
    vi.mocked(listarAlertasPlazo).mockResolvedValue([
      alerta({ id: 10, diasAntes: 5, fechaAlerta: '2026-11-15' }),
      alerta({ id: 11, diasAntes: 3, fechaAlerta: '2026-11-17' }),
    ])
    montar()

    const filas = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    expect(filas).toHaveLength(2)
    expect(filas[0].textContent).toContain('Alerta de 5 días antes')
    expect(filas[1].textContent).toContain('Alerta de 3 días antes')
  })

  it('el nombre del cliente lleva a su ficha', async () => {
    montar()

    expect((await screen.findByRole('link', { name: 'Beta SAC' })).getAttribute('href')).toBe('/clientes/5')
  })

  it('lo más urgente se marca en rojo y lo lejano queda neutro', async () => {
    montar()

    await screen.findByRole('table')
    const tono = (texto: string) => screen.getByText(texto).closest('[data-tono]')?.getAttribute('data-tono')
    expect(tono('Vence hoy')).toBe('peligro')
    expect(tono('Falta 1 día')).toBe('peligro')
    expect(tono('Faltan 3 días')).toBe('aviso')
    expect(tono('Faltan 8 días')).toBe('neutro')
  })

  it('sin alertas lo dice', async () => {
    vi.mocked(listarAlertasPlazo).mockResolvedValue([])
    montar()

    expect(await screen.findByText('No hay alertas de plazo.')).toBeTruthy()
  })

  it('un error del servidor se muestra y no deja una lista vieja', async () => {
    vi.mocked(listarAlertasPlazo).mockRejectedValue(new AxiosError('403', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 403, data: { mensaje: 'Acceso denegado' } } as AxiosResponse))
    montar()

    expect((await screen.findByRole('alert')).textContent).toBe('Acceso denegado')
    expect(screen.getByText('No se pudieron cargar las alertas.')).toBeTruthy()
  })
})
