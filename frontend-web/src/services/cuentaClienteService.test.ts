import { afterEach, describe, expect, it, vi } from 'vitest'
import { crearCuentaCliente } from './cuentaClienteService'
import { authApi } from './apiClient'

afterEach(() => vi.restoreAllMocks())

describe('cuentaClienteService (HU-37)', () => {
  it('crea la cuenta con el POST de ms-auth de esa empresa y devuelve lo que responde el servidor', async () => {
    const respuesta = { usuarioId: 9, correo: 'contacto@acme.pe', clienteId: 4, passwordTemporal: 'valor-ficticio' }
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({ data: respuesta })

    expect(await crearCuentaCliente(4, 'contacto@acme.pe')).toEqual(respuesta)
    expect(post).toHaveBeenCalledWith('/api/auth/clientes/4/cuenta', { correo: 'contacto@acme.pe' })
  })
})
