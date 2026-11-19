import { afterEach, describe, expect, it, vi } from 'vitest'
import { listarNotificaciones, marcarAtendida, registrarNotificacion } from './notificacionService'
import { tributarioApi } from './apiClient'

vi.mock('./apiClient', () => ({ tributarioApi: { get: vi.fn(), post: vi.fn(), patch: vi.fn() } }))

afterEach(() => vi.clearAllMocks())

describe('notificacionService (HU-18)', () => {
  it('lista las notificaciones del cliente con el filtro de estado solo si se indica', async () => {
    vi.mocked(tributarioApi.get).mockResolvedValue({ data: [{ id: 1 }] })

    expect(await listarNotificaciones(4, 'PENDIENTE')).toEqual([{ id: 1 }])
    expect(tributarioApi.get).toHaveBeenLastCalledWith('/api/tributario/clientes/4/notificaciones', { params: { estado: 'PENDIENTE' } })

    await listarNotificaciones(4, 'ATENDIDA')
    expect(tributarioApi.get).toHaveBeenLastCalledWith('/api/tributario/clientes/4/notificaciones', { params: { estado: 'ATENDIDA' } })

    await listarNotificaciones(4)
    expect(tributarioApi.get).toHaveBeenLastCalledWith('/api/tributario/clientes/4/notificaciones', { params: {} })
  })

  it('marca como atendida con el PATCH de atención de esa notificación', async () => {
    vi.mocked(tributarioApi.patch).mockResolvedValue({ data: { id: 9, estado: 'ATENDIDA' } })

    expect(await marcarAtendida(9)).toEqual({ id: 9, estado: 'ATENDIDA' })
    expect(tributarioApi.patch).toHaveBeenCalledWith('/api/tributario/notificaciones/9/atencion')
  })

  it('registra con el POST del cliente y el cuerpo tal cual', async () => {
    vi.mocked(tributarioApi.post).mockResolvedValue({ data: { id: 3 } })
    const entrada = { tipo: 'ESQUELA' as const, fechaRecepcion: '2026-11-05', plazoRespuesta: '2026-11-20' }

    expect(await registrarNotificacion(4, entrada)).toEqual({ id: 3 })
    expect(tributarioApi.post).toHaveBeenCalledWith('/api/tributario/clientes/4/notificaciones', entrada)
  })
})
