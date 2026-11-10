// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import IncidenciasTab from './IncidenciasTab'
import { Incidencia, listarIncidencias, registrarIncidencia } from '../../services/incidenciaService'

vi.mock('../../services/incidenciaService', () => ({ listarIncidencias: vi.fn(), registrarIncidencia: vi.fn() }))

const incidencia = (extra: Partial<Incidencia> = {}): Incidencia => ({
  id: 1, tipo: 'CLIENTE_NO_ENVIA_DOCUMENTOS', fecha: '2026-11-09', descripcion: 'No envió las facturas',
  registradaPor: 'contador1', registradaEn: '2026-11-09T10:00:00', clientesAsociados: 1, ...extra,
})

function montar() {
  const props = { setError: vi.fn(), setOk: vi.fn() }
  render(<IncidenciasTab clienteId={4} {...props} />)
  return props
}

async function escribir(texto: string) {
  fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: texto } })
}

beforeEach(() => {
  vi.mocked(listarIncidencias).mockResolvedValue([])
  vi.stubGlobal('confirm', vi.fn(() => true))
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.unstubAllGlobals() })

describe('Incidencias del servicio en la ficha (HU-23)', () => {
  it('muestra el historial del cliente con sus incidencias y las generales (criterio 2)', async () => {
    vi.mocked(listarIncidencias).mockResolvedValue([
      incidencia({ id: 2, tipo: 'CAIDA_PORTAL_SOL', descripcion: 'Portal SOL caído', clientesAsociados: 37, fecha: '2026-11-10' }),
      incidencia(),
    ])
    montar()

    expect(await screen.findByText('Portal SOL caído')).toBeTruthy()
    expect(screen.getByText('General · 37 clientes')).toBeTruthy()
    expect(screen.getByText('No envió las facturas')).toBeTruthy()
    expect(screen.getByText('Este cliente')).toBeTruthy()
    expect(screen.getByText('09/11/2026')).toBeTruthy()
    expect(screen.getByText('10/11/2026')).toBeTruthy()
    expect(listarIncidencias).toHaveBeenCalledWith(4)
  })

  it('sin incidencias lo indica', async () => {
    montar()
    expect(await screen.findByText('Este cliente no tiene incidencias registradas.')).toBeTruthy()
  })

  it('registra la incidencia del cliente con tipo, cliente, fecha y descripción (criterio 1)', async () => {
    vi.mocked(registrarIncidencia).mockResolvedValue(incidencia())
    const { setOk } = montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await userEvent.selectOptions(screen.getByLabelText('Tipo de incidencia'), 'RECHAZO_FRACCIONAMIENTO')
    fireEvent.change(screen.getByLabelText('Fecha'), { target: { value: '2026-11-08' } })
    await escribir('SUNAT rechazó la solicitud')
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))

    await waitFor(() => expect(registrarIncidencia).toHaveBeenCalledWith({
      tipo: 'RECHAZO_FRACCIONAMIENTO', clienteId: 4, fecha: '2026-11-08', descripcion: 'SUNAT rechazó la solicitud',
    }))
    expect(setOk).toHaveBeenCalledWith('Incidencia registrada en el historial del cliente')
    expect((screen.getByLabelText('Descripción') as HTMLTextAreaElement).value).toBe('')
    expect(listarIncidencias).toHaveBeenCalledTimes(2)
  })

  it('la caída del portal SOL es general: no envía cliente y avisa a cuántos clientes se asoció (criterio 3)', async () => {
    vi.mocked(registrarIncidencia).mockResolvedValue(incidencia({ tipo: 'CAIDA_PORTAL_SOL', clientesAsociados: 37 }))
    const { setOk } = montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await userEvent.selectOptions(screen.getByLabelText('Tipo de incidencia'), 'CAIDA_PORTAL_SOL')
    expect(screen.getByText(/incidencia general/i)).toBeTruthy()
    await escribir('El portal no cargaba')
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))

    await waitFor(() => expect(registrarIncidencia).toHaveBeenCalled())
    expect(vi.mocked(registrarIncidencia).mock.calls[0][0].clienteId).toBeUndefined()
    expect(setOk).toHaveBeenCalledWith('Incidencia general registrada: quedó asociada a 37 clientes activos')
  })

  it('la incidencia general pide confirmación y, si se cancela, no se registra', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false))
    montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await userEvent.selectOptions(screen.getByLabelText('Tipo de incidencia'), 'CAIDA_PORTAL_SOL')
    await escribir('El portal no cargaba')
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('TODOS los clientes'))
    expect(registrarIncidencia).not.toHaveBeenCalled()
  })

  it('un error del servidor se muestra y la descripción escrita no se pierde', async () => {
    vi.mocked(registrarIncidencia).mockRejectedValue(new Error('x'))
    const { setError } = montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await escribir('Texto que no debe perderse')
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))

    await waitFor(() => expect(setError).toHaveBeenCalledWith('Error inesperado'))
    expect((screen.getByLabelText('Descripción') as HTMLTextAreaElement).value).toBe('Texto que no debe perderse')
  })
})
