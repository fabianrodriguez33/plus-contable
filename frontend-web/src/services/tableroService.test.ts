import { afterEach, describe, expect, it, vi } from 'vitest'
import { consultarTablero } from './tableroService'
import { tributarioApi } from './apiClient'

afterEach(() => vi.restoreAllMocks())

describe('tableroService (HU-14)', () => {
  it('pide el tablero con el cliente, el mes y el filtro de declarado sin pago, y devuelve lo que responde el servidor', async () => {
    const respuesta = [{ obligacionId: 1 }]
    const get = vi.spyOn(tributarioApi, 'get').mockResolvedValue({ data: respuesta })

    expect(await consultarTablero({ clienteId: 4, periodo: '2026-11-01', soloDeclaradoSinPago: true })).toEqual(respuesta)

    expect(get).toHaveBeenCalledWith('/api/tributario/tablero', { params: { clienteId: 4, periodo: '2026-11-01', soloDeclaradoSinPago: true } })
  })

  it('sin filtros no manda nada: todos los clientes, todos los meses y todas las obligaciones', async () => {
    const get = vi.spyOn(tributarioApi, 'get').mockResolvedValue({ data: [] })

    await consultarTablero()
    await consultarTablero({ soloDeclaradoSinPago: false })

    for (const llamada of get.mock.calls) {
      expect(llamada[1]).toEqual({ params: { clienteId: undefined, periodo: undefined, soloDeclaradoSinPago: undefined } })
    }
  })
})
