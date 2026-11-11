// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError, AxiosResponse } from 'axios'
import RestablecerPage from './RestablecerPage'
import { solicitarRestablecimiento } from '../services/cuentaClienteService'

vi.mock('../services/cuentaClienteService', () => ({ solicitarRestablecimiento: vi.fn() }))

const MENSAJE = 'Si el correo corresponde a una cuenta de cliente, el estudio recibirá tu solicitud y te entregará una nueva contraseña temporal.'

function montar() {
  render(<MemoryRouter><RestablecerPage /></MemoryRouter>)
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('¿Olvidaste tu contraseña? (HU-41)', () => {
  it('envía el correo sin espacios y muestra el mensaje del servidor en lugar del formulario (criterios 1 y 2)', async () => {
    vi.mocked(solicitarRestablecimiento).mockResolvedValue(MENSAJE)
    montar()

    await userEvent.type(screen.getByLabelText('Correo'), '  contacto@acme.pe ')
    await userEvent.click(screen.getByRole('button', { name: 'Pedir nueva contraseña' }))

    expect(solicitarRestablecimiento).toHaveBeenCalledWith('contacto@acme.pe')
    expect((await screen.findByRole('status')).textContent).toContain(MENSAJE)
    expect(screen.queryByLabelText('Correo')).toBeNull()
    expect(screen.getByRole('link', { name: 'Volver al ingreso' }).getAttribute('href')).toBe('/login')
  })

  it('un error del servidor se muestra y deja el formulario para reintentar', async () => {
    vi.mocked(solicitarRestablecimiento).mockRejectedValue(
      new AxiosError('400', 'ERR_BAD_REQUEST', undefined, undefined,
        { status: 400, data: { mensaje: 'Escribe el correo con el que ingresas' } } as AxiosResponse))
    montar()

    await userEvent.type(screen.getByLabelText('Correo'), 'x@acme.pe')
    await userEvent.click(screen.getByRole('button', { name: 'Pedir nueva contraseña' }))

    expect((await screen.findByRole('alert')).textContent).toContain('Escribe el correo con el que ingresas')
    expect(screen.getByLabelText('Correo')).toBeTruthy()
  })
})
