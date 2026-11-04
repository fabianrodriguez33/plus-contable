import { afterEach, describe, expect, it, vi } from 'vitest'
import { guardarSocios } from './clienteService'
import { tributarioApi } from './apiClient'

vi.mock('./apiClient', () => ({ tributarioApi: { put: vi.fn(), get: vi.fn(), patch: vi.fn() } }))

afterEach(() => vi.clearAllMocks())

describe('guardarSocios (HU-02)', () => {
  it('envía solo los datos del socio y su aporte: el porcentaje lo calcula el servidor', async () => {
    vi.mocked(tributarioApi.put).mockResolvedValue({ data: {} })

    await guardarSocios(4, [{
      id: 1, nombreCompleto: 'Ana', tipoDocumento: 'DNI', numeroDocumento: '1', aporte: 100, porcentaje: 60, cargo: 'Gerente',
    }])

    expect(tributarioApi.put).toHaveBeenCalledWith('/api/tributario/clientes/4/socios', [
      { nombreCompleto: 'Ana', tipoDocumento: 'DNI', numeroDocumento: '1', aporte: 100, cargo: 'Gerente' },
    ])
  })
})
