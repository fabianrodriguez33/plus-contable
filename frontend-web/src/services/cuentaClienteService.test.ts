import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  crearCuentaCliente, listarSolicitudesRestablecimiento, restablecerAccesoCliente, solicitarRestablecimiento,
} from './cuentaClienteService'
import { authApi } from './apiClient'

afterEach(() => vi.restoreAllMocks())

describe('cuentaClienteService (HU-37)', () => {
  it('crea la cuenta con el POST de ms-auth de esa empresa y devuelve lo que responde el servidor', async () => {
    const respuesta = { usuarioId: 9, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: 'valor-ficticio' }
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({ data: respuesta })

    expect(await crearCuentaCliente(4, 'contacto@acme.pe')).toEqual(respuesta)
    expect(post).toHaveBeenCalledWith('/api/auth/clientes/4/cuenta', { correo: 'contacto@acme.pe' })
  })

  it('restablece el acceso con el POST de ms-auth de esa empresa, sin cuerpo, y devuelve lo que responde el servidor', async () => {
    const respuesta = { usuarioId: 9, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: 'valor-ficticio', venceEn: '2026-11-13T08:30:00' }
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({ data: respuesta })

    expect(await restablecerAccesoCliente(4)).toEqual(respuesta)
    expect(post).toHaveBeenCalledTimes(1)
    expect(post).toHaveBeenCalledWith('/api/auth/clientes/4/cuenta/restablecer')
  })
})

describe('cuentaClienteService (HU-41)', () => {
  it('pide el restablecimiento con el correo y devuelve solo el mensaje del servidor', async () => {
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({ data: { mensaje: 'Si el correo corresponde…' } })

    expect(await solicitarRestablecimiento('contacto@acme.pe')).toBe('Si el correo corresponde…')
    expect(post).toHaveBeenCalledWith('/api/auth/restablecimiento/solicitar', { correo: 'contacto@acme.pe' })
  })

  it('lista las solicitudes pendientes con el GET de ms-auth', async () => {
    const lista = [{ clienteId: 4, correo: 'contacto@acme.pe', solicitadaEn: '2026-11-11T09:30:00' }]
    const get = vi.spyOn(authApi, 'get').mockResolvedValue({ data: lista })

    expect(await listarSolicitudesRestablecimiento()).toEqual(lista)
    expect(get).toHaveBeenCalledWith('/api/auth/restablecimiento/pendientes')
  })
})
