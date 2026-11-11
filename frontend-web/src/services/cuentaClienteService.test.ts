import { afterEach, describe, expect, it, vi } from 'vitest'
import { crearCuentaCliente, restablecerAccesoCliente } from './cuentaClienteService'
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
