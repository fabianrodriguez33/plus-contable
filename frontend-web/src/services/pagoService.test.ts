import { afterEach, describe, expect, it, vi } from 'vitest'
import { listarPagos, registrarPago } from './pagoService'
import { tributarioApi } from './apiClient'

afterEach(() => vi.restoreAllMocks())

describe('pagoService (HU-13)', () => {
  it('lista los pagos con el GET de esa obligación y devuelve lo que responde el servidor', async () => {
    const respuesta = [{ id: 1, fechaPago: '2026-11-08', montoPagado: 400, numeroConstancia: 'C-001', acumuladoDespues: 400, saldoDespues: 600 }]
    const get = vi.spyOn(tributarioApi, 'get').mockResolvedValue({ data: respuesta })

    expect(await listarPagos(3)).toEqual(respuesta)
    expect(get).toHaveBeenCalledWith('/api/tributario/obligaciones/3/pagos')
  })

  it('registra el pago con un formulario de fecha, monto y constancia, sin archivo', async () => {
    const respuesta = { id: 3, estado: 'PAGO_PARCIAL', montoAcumuladoPagado: 400, saldo: 600, numeroPagos: 1 }
    const post = vi.spyOn(tributarioApi, 'post').mockResolvedValue({ data: respuesta })

    expect(await registrarPago(3, { fechaPago: '2026-11-08', montoPagado: 400.5, numeroConstancia: 'C-001' })).toEqual(respuesta)

    expect(post).toHaveBeenCalledTimes(1)
    const [ruta, cuerpo] = post.mock.calls[0] as [string, FormData]
    expect(ruta).toBe('/api/tributario/obligaciones/3/pagos')
    expect(cuerpo).toBeInstanceOf(FormData)
    expect(Object.fromEntries(cuerpo.entries())).toEqual({ fechaPago: '2026-11-08', montoPagado: '400.5', numeroConstancia: 'C-001' })
    expect(cuerpo.has('archivo')).toBe(false)
  })
})
