// @vitest-environment jsdom
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AccesoClienteTab, { SEGUNDOS_VISIBLE } from './AccesoClienteTab'
import { crearCuentaCliente } from '../../services/cuentaClienteService'

vi.mock('../../services/cuentaClienteService', () => ({ crearCuentaCliente: vi.fn() }))

// valor ficticio de prueba (no es una contraseña real)
const TEMPORAL = 'Xx-prueba-0000'

const props = () => ({ setError: vi.fn(), setOk: vi.fn() })

function montar(clienteId = 4) {
  const p = props()
  const r = render(<AccesoClienteTab clienteId={clienteId} {...p} />)
  return { ...p, ...r }
}

const correo = () => screen.getByLabelText('Correo del cliente') as HTMLInputElement
const crear = () => screen.getByRole('button', { name: /Crear acceso/ })

async function crearCon(texto: string, user = userEvent.setup()) {
  await user.type(correo(), texto)
  await user.click(crear())
}

beforeEach(() => {
  vi.mocked(crearCuentaCliente).mockResolvedValue({ usuarioId: 9, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: TEMPORAL })
})
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.useRealTimers()
  localStorage.clear()
  sessionStorage.clear()
})

describe('Acceso del cliente en la ficha (HU-37, T13)', () => {
  it('sin correo o con un correo inválido no se envía y se avisa', async () => {
    montar()
    await userEvent.click(crear())
    expect(screen.getByText('Escriba el correo con el que entrará el cliente')).toBeTruthy()

    for (const malo of ['sin-arroba', 'a@b', 'a b@acme.pe', 'x@acme.p']) {
      await userEvent.clear(correo())
      await userEvent.type(correo(), malo)
      await userEvent.click(crear())
      expect(screen.getByText('El correo no tiene un formato válido')).toBeTruthy()
    }
    expect(crearCuentaCliente).not.toHaveBeenCalled()
  })

  it('el correo admite hasta 60 caracteres y rechaza 61', async () => {
    montar()
    await userEvent.type(correo(), 'a'.repeat(53) + '@acme.pe') // 61
    await userEvent.click(crear())
    expect(screen.getByText('El correo puede tener hasta 60 caracteres')).toBeTruthy()
    expect(crearCuentaCliente).not.toHaveBeenCalled()

    await userEvent.clear(correo())
    await userEvent.type(correo(), 'a'.repeat(52) + '@acme.pe') // 60
    await userEvent.click(crear())
    expect(crearCuentaCliente).toHaveBeenCalledTimes(1)
  })

  it('crea el acceso con el correo sin espacios y muestra la contraseña temporal con el aviso de una sola vez', async () => {
    const { setOk } = montar()
    await crearCon('  contacto@acme.pe ')

    expect(crearCuentaCliente).toHaveBeenCalledWith(4, 'contacto@acme.pe')
    const panel = await screen.findByRole('region', { name: 'Contraseña temporal' })
    expect(panel.textContent).toContain('se muestra una sola vez y no se podrá volver a ver')
    expect(panel.textContent).toContain('contacto@acme.pe')
    expect(screen.getByTestId('password-temporal').textContent).toBe(TEMPORAL)
    expect(setOk).toHaveBeenCalledWith('Acceso creado para contacto@acme.pe')
    expect(screen.queryByLabelText('Correo del cliente')).toBeNull() // una sola cuenta por empresa: no se ofrece otra
  })

  it('la contraseña no va a los avisos de la ficha ni al almacenamiento del navegador', async () => {
    const { setOk, setError } = montar()
    await crearCon('contacto@acme.pe')
    await screen.findByTestId('password-temporal')

    for (const llamada of [...setOk.mock.calls, ...setError.mock.calls]) {
      expect(JSON.stringify(llamada)).not.toContain(TEMPORAL)
    }
    expect(JSON.stringify({ ...localStorage })).not.toContain(TEMPORAL)
    expect(JSON.stringify({ ...sessionStorage })).not.toContain(TEMPORAL)
  })

  it('«Ya la anoté» la oculta y no se puede volver a ver', async () => {
    montar()
    await crearCon('contacto@acme.pe')
    await userEvent.click(await screen.findByRole('button', { name: /Ya la anoté/ }))

    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(screen.queryByText(TEMPORAL)).toBeNull()
    expect(screen.getByRole('status').textContent).toContain('contacto@acme.pe')
    expect(screen.getByRole('status').textContent).toContain('ya no se puede volver a ver')
    expect(screen.queryByLabelText('Correo del cliente')).toBeNull()
    expect(crearCuentaCliente).toHaveBeenCalledTimes(1)
  })

  it('al salir de la pestaña y volver ya no está la contraseña', async () => {
    const { unmount } = montar()
    await crearCon('contacto@acme.pe')
    await screen.findByTestId('password-temporal')
    unmount()

    montar()
    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(screen.queryByText(TEMPORAL)).toBeNull()
    expect(screen.getByLabelText('Correo del cliente')).toBeTruthy()
  })

  it('al pasar a otro cliente se borra la contraseña del anterior', async () => {
    const { rerender, setError, setOk } = montar(4)
    await crearCon('contacto@acme.pe')
    await screen.findByTestId('password-temporal')

    rerender(<AccesoClienteTab clienteId={5} setError={setError} setOk={setOk} />)

    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(screen.getByLabelText('Correo del cliente')).toBeTruthy()
  })

  it('la contraseña se oculta sola a los dos minutos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    await crearCon('contacto@acme.pe', user)
    await screen.findByTestId('password-temporal')

    await act(async () => { vi.advanceTimersByTime((SEGUNDOS_VISIBLE - 1) * 1000) })
    expect(screen.queryByTestId('password-temporal')).not.toBeNull()
    await act(async () => { vi.advanceTimersByTime(2000) })
    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(SEGUNDOS_VISIBLE).toBe(120)
  })

  it('si el servidor rechaza (la empresa ya tiene cuenta) se muestra el error, no hay contraseña y se puede corregir', async () => {
    vi.mocked(crearCuentaCliente).mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 409, data: { mensaje: 'La empresa ya tiene una cuenta de acceso' } } as never))
    const { setError, setOk } = montar()
    await crearCon('contacto@acme.pe')

    await vi.waitFor(() => expect(setError).toHaveBeenLastCalledWith('La empresa ya tiene una cuenta de acceso'))
    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(setOk).not.toHaveBeenCalledWith(expect.stringContaining('Acceso creado'))
    expect(correo().value).toBe('contacto@acme.pe')
  })

  it('«Copiar» pone la contraseña en el portapapeles y avisa; si falla lo dice', async () => {
    const user = userEvent.setup()
    const escribir = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined)
    const { setError } = montar()
    await crearCon('contacto@acme.pe', user)
    await user.click(await screen.findByRole('button', { name: /Copiar/ }))

    expect(escribir).toHaveBeenCalledWith(TEMPORAL)
    expect(screen.getByRole('button', { name: /Copiada/ })).toBeTruthy()

    escribir.mockRejectedValue(new Error('sin permiso'))
    await user.click(screen.getByRole('button', { name: /Copiada/ }))
    await vi.waitFor(() => expect(setError).toHaveBeenCalledWith('No se pudo copiar: anótela a mano antes de ocultarla'))
  })

  it('mientras se crea el botón queda deshabilitado', async () => {
    let terminar: (v: Awaited<ReturnType<typeof crearCuentaCliente>>) => void = () => {}
    vi.mocked(crearCuentaCliente).mockReturnValue(new Promise((ok) => { terminar = ok }))
    montar()
    await crearCon('contacto@acme.pe')

    const boton = screen.getByRole('button', { name: /Creando/ }) as HTMLButtonElement
    expect(boton.disabled).toBe(true)
    await act(async () => { terminar({ usuarioId: 1, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: TEMPORAL }) })
    expect(await screen.findByTestId('password-temporal')).toBeTruthy()
  })
})
