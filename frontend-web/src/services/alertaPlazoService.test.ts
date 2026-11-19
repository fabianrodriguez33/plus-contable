import { afterEach, describe, expect, it, vi } from 'vitest'
import { listarAlertasPlazo } from './alertaPlazoService'
import { tributarioApi } from './apiClient'

afterEach(() => vi.restoreAllMocks())

describe('alertaPlazoService (HU-19)', () => {
  it('pide las alertas a su ruta y devuelve lo que responde el servidor', async () => {
    const respuesta = [{ id: 1, razonSocial: 'Acme SAC', diasRestantes: 3 }]
    const get = vi.spyOn(tributarioApi, 'get').mockResolvedValue({ data: respuesta })

    expect(await listarAlertasPlazo()).toEqual(respuesta)
    expect(get).toHaveBeenCalledWith('/api/tributario/alertas-plazo')
  })
})
