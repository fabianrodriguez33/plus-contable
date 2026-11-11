// @vitest-environment jsdom
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AccesoClienteTab, { MENSAJE_CONFIRMAR_RESTABLECER, SEGUNDOS_VISIBLE } from './AccesoClienteTab'
import { crearCuentaCliente, restablecerAccesoCliente } from '../../services/cuentaClienteService'

vi.mock('../../services/cuentaClienteService', () => ({ crearCuentaCliente: vi.fn(), restablecerAccesoCliente: vi.fn() }))

// valores ficticios de prueba (no son contraseñas reales)
const TEMPORAL = 'Xx-prueba-0000'
const OTRA_TEMPORAL = 'Yy-prueba-1111'

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
  vi.mocked(restablecerAccesoCliente).mockResolvedValue({
    usuarioId: 9, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: OTRA_TEMPORAL, venceEn: '2026-11-13T08:30:00',
  })
  vi.stubGlobal('confirm', vi.fn(() => true))
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


describe('Restablecer acceso en la ficha (HU-37, decisión de Beto del 11/11)', () => {
  const boton = () => screen.getByRole('button', { name: /Restablecer acceso/ }) as HTMLButtonElement

  async function restablecer(user = userEvent.setup()) {
    await user.click(boton())
  }

  it('la pestaña ofrece el botón «Restablecer acceso» con su explicación, también antes de crear el acceso', () => {
    montar()

    const seccion = screen.getByRole('region', { name: 'Restablecer acceso' })
    expect(seccion.textContent).toContain('perdió la contraseña temporal')
    expect(seccion.textContent).toContain('El sistema no envía correos')
    expect(boton().disabled).toBe(false)
    expect(screen.getByRole('button', { name: /Crear acceso/ })).toBeTruthy()
  })

  it('pide confirmación antes de hacerlo y, si se cancela, no se restablece nada', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false))
    const { setOk, setError } = montar()

    await restablecer()

    expect(window.confirm).toHaveBeenCalledWith(MENSAJE_CONFIRMAR_RESTABLECER)
    expect(MENSAJE_CONFIRMAR_RESTABLECER).toContain('sesiones abiertas del cliente dejarán de valer')
    expect(MENSAJE_CONFIRMAR_RESTABLECER).toContain('se mostrará una sola vez')
    expect(restablecerAccesoCliente).not.toHaveBeenCalled()
    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(setOk).not.toHaveBeenCalledWith(expect.stringContaining('restablecido'))
    expect(setError).toHaveBeenLastCalledWith('')
  })

  it('confirmado, restablece la empresa del cliente y muestra la nueva contraseña una sola vez, con su vencimiento', async () => {
    const { setOk } = montar(4)

    await restablecer()

    expect(restablecerAccesoCliente).toHaveBeenCalledTimes(1)
    expect(restablecerAccesoCliente).toHaveBeenCalledWith(4)
    const panel = await screen.findByRole('region', { name: 'Contraseña temporal' })
    expect(screen.getByTestId('password-temporal').textContent).toBe(OTRA_TEMPORAL)
    expect(panel.textContent).toContain('Acceso restablecido: nueva contraseña temporal')
    expect(panel.textContent).toContain('se muestra una sola vez y no se podrá volver a ver')
    expect(panel.textContent).toContain('contacto@acme.pe')
    expect(panel.textContent).toContain('Vale hasta el 13/11/2026, 08:30 (48 horas)')
    expect(setOk).toHaveBeenCalledWith('Acceso restablecido para contacto@acme.pe')
    expect(crearCuentaCliente).not.toHaveBeenCalled()
  })

  it('si el servidor no manda el vencimiento dice «Vale 48 horas»', async () => {
    vi.mocked(restablecerAccesoCliente).mockResolvedValue({ usuarioId: 9, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: OTRA_TEMPORAL })
    montar()

    await restablecer()

    const panel = await screen.findByRole('region', { name: 'Contraseña temporal' })
    expect(panel.textContent).toContain('Vale 48 horas')
    expect(panel.textContent).not.toContain('Vale hasta')
  })

  it('explica que la temporal sigue valiendo aunque el cliente ingrese, hasta que la cambie (al crear y al restablecer)', async () => {
    montar()
    await crearCon('contacto@acme.pe')
    const alCrear = (await screen.findByRole('region', { name: 'Contraseña temporal' })).textContent
    expect(alCrear).toContain('sigue sirviendo aunque el cliente ingrese')
    expect(alCrear).toContain('después queda invalidada')
    expect(alCrear).not.toContain('solo sirve una vez')
    cleanup()

    montar()
    await restablecer()
    const alRestablecer = (await screen.findByRole('region', { name: 'Contraseña temporal' })).textContent
    expect(alRestablecer).toContain('sigue sirviendo aunque el cliente ingrese')
    expect(alRestablecer).not.toContain('solo sirve una vez')
  })

  it('la contraseña restablecida no va a los avisos de la ficha ni al almacenamiento del navegador', async () => {
    const { setOk, setError } = montar()
    await restablecer()
    await screen.findByTestId('password-temporal')

    for (const llamada of [...setOk.mock.calls, ...setError.mock.calls]) {
      expect(JSON.stringify(llamada)).not.toContain(OTRA_TEMPORAL)
    }
    expect(JSON.stringify({ ...localStorage })).not.toContain(OTRA_TEMPORAL)
    expect(JSON.stringify({ ...sessionStorage })).not.toContain(OTRA_TEMPORAL)
  })

  it('mientras se muestra la contraseña no se ofrece restablecer otra vez', async () => {
    montar()
    await restablecer()
    await screen.findByTestId('password-temporal')

    expect(screen.queryByRole('button', { name: /Restablecer acceso/ })).toBeNull()
    expect(screen.queryByRole('region', { name: 'Restablecer acceso' })).toBeNull()
  })

  it('«Ya la anoté» la oculta, no se puede volver a ver y el botón vuelve; no se ofrece crear otro acceso', async () => {
    montar()
    await restablecer()
    await userEvent.click(await screen.findByRole('button', { name: /Ya la anoté/ }))

    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(screen.queryByText(OTRA_TEMPORAL)).toBeNull()
    expect(screen.getByRole('status').textContent).toContain('contacto@acme.pe')
    expect(screen.getByRole('status').textContent).toContain('ya no se puede volver a ver')
    expect(screen.queryByLabelText('Correo del cliente')).toBeNull()
    expect(boton()).toBeTruthy()
    expect(restablecerAccesoCliente).toHaveBeenCalledTimes(1)
  })

  it('la contraseña restablecida se oculta sola a los dos minutos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    montar()
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    await restablecer(user)
    await screen.findByTestId('password-temporal')

    await act(async () => { vi.advanceTimersByTime((SEGUNDOS_VISIBLE - 1) * 1000) })
    expect(screen.queryByTestId('password-temporal')).not.toBeNull()
    await act(async () => { vi.advanceTimersByTime(2000) })
    expect(screen.queryByTestId('password-temporal')).toBeNull()
  })

  it('al pasar a otro cliente se borra la contraseña restablecida del anterior', async () => {
    const { rerender, setError, setOk } = montar(4)
    await restablecer()
    await screen.findByTestId('password-temporal')

    rerender(<AccesoClienteTab clienteId={5} setError={setError} setOk={setOk} />)

    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(screen.queryByText(OTRA_TEMPORAL)).toBeNull()
    expect(boton().disabled).toBe(false)
  })

  it('al salir de la pestaña y volver ya no está la contraseña restablecida', async () => {
    const { unmount } = montar()
    await restablecer()
    await screen.findByTestId('password-temporal')
    unmount()

    montar()
    expect(screen.queryByText(OTRA_TEMPORAL)).toBeNull()
    expect(screen.queryByTestId('password-temporal')).toBeNull()
  })

  it('si el cliente no tiene cuenta, el servidor responde 404: se muestra su mensaje y no hay contraseña', async () => {
    vi.mocked(restablecerAccesoCliente).mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 404, data: { mensaje: 'El cliente no tiene una cuenta de acceso' } } as never))
    const { setError, setOk } = montar()

    await restablecer()

    await vi.waitFor(() => expect(setError).toHaveBeenLastCalledWith('El cliente no tiene una cuenta de acceso'))
    expect(screen.queryByTestId('password-temporal')).toBeNull()
    expect(setOk).not.toHaveBeenCalledWith(expect.stringContaining('restablecido'))
    expect(boton().disabled).toBe(false) // se puede reintentar
  })

  it('mientras se restablece el botón queda deshabilitado y no se envía dos veces', async () => {
    let terminar: (v: Awaited<ReturnType<typeof restablecerAccesoCliente>>) => void = () => {}
    vi.mocked(restablecerAccesoCliente).mockReturnValue(new Promise((ok) => { terminar = ok }))
    montar()

    await restablecer()

    const enCurso = screen.getByRole('button', { name: /Restableciendo/ }) as HTMLButtonElement
    expect(enCurso.disabled).toBe(true)
    await userEvent.click(enCurso)
    expect(restablecerAccesoCliente).toHaveBeenCalledTimes(1)
    await act(async () => { terminar({ usuarioId: 1, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: OTRA_TEMPORAL }) })
    expect(await screen.findByTestId('password-temporal')).toBeTruthy()
  })

  it('limpia el aviso anterior antes de restablecer', async () => {
    const { setOk, setError } = montar()

    await restablecer()

    expect(setError.mock.calls[0]).toEqual([''])
    expect(setOk.mock.calls[0]).toEqual([''])
  })
})
