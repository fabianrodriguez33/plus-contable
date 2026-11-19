// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { AxiosError, AxiosResponse } from 'axios'
import HistorialTab from './HistorialTab'
import { listarHistorial, type HistorialItem } from '../../services/incidenciaService'

vi.mock('../../services/incidenciaService', () => ({ listarHistorial: vi.fn() }))

const item = (h: Partial<HistorialItem> & Pick<HistorialItem, 'origen' | 'id' | 'tipo' | 'fecha'>): HistorialItem => ({
  descripcion: null, general: false, estado: null, fechaEntrega: null, registradaEn: `${h.fecha}T09:00:00`, ...h,
})

function montar() {
  const setError = vi.fn()
  render(<HistorialTab clienteId={4} setError={setError} />)
  return setError
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Historial del cliente (HU-27)', () => {
  it('criterios 1 y 2: muestra incidencias y solicitudes en el orden del servidor, con tipo, fecha y descripción', async () => {
    vi.mocked(listarHistorial).mockResolvedValue([
      item({ origen: 'SOLICITUD', id: 6, tipo: 'TRAMITE', fecha: '2026-11-11', descripcion: 'Baja de RUC', estado: 'PENDIENTE', fechaEntrega: '2026-11-20' }),
      item({ origen: 'INCIDENCIA', id: 1, tipo: 'PROBLEMA_SIRE', fecha: '2026-11-08', descripcion: 'SIRE no cargaba' }),
      item({ origen: 'INCIDENCIA', id: 2, tipo: 'CAIDA_PORTAL_SOL', fecha: '2026-11-01', descripcion: 'SOL caído', general: true }),
      item({ origen: 'SOLICITUD', id: 5, tipo: 'CONSTANCIA', fecha: '2026-10-30', estado: 'ATENDIDA', fechaEntrega: '2026-11-02' }),
    ])
    montar()

    await screen.findByText('Baja de RUC')
    const filas = screen.getAllByRole('row').slice(1).map((f) => f.textContent)
    expect(filas).toEqual([
      '11/11/2026SolicitudTrámiteBaja de RUCPendiente · entrega 20/11/2026',
      '08/11/2026IncidenciaProblemas con SIRESIRE no cargabaDe este cliente',
      '01/11/2026IncidenciaCaída o indisponibilidad del portal SOLSOL caídoGeneral (toda la cartera)',
      '30/10/2026SolicitudConstancia—Atendida · entrega 02/11/2026',
    ])
    expect(listarHistorial).toHaveBeenCalledWith(4)
  })

  it('criterio 3: sin registros lo dice', async () => {
    vi.mocked(listarHistorial).mockResolvedValue([])
    montar()

    expect(await screen.findByText('Este cliente no tiene incidencias ni solicitudes registradas.')).toBeTruthy()
  })

  it('criterio 4: si el servidor niega el acceso, se muestra su mensaje como error de la ficha', async () => {
    vi.mocked(listarHistorial).mockRejectedValue(new AxiosError('403', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 403, data: { mensaje: 'Acceso denegado' } } as AxiosResponse))
    const setError = montar()

    await waitFor(() => expect(setError).toHaveBeenCalledWith('Acceso denegado'))
  })
})
