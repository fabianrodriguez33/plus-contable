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

  it('la caída del portal SOL se llama «Caída o indisponibilidad del portal SOL» en el selector y en el historial, con el mismo valor interno', async () => {
    vi.mocked(listarIncidencias).mockResolvedValue([
      incidencia({ id: 2, tipo: 'CAIDA_PORTAL_SOL', descripcion: 'Portal SOL caído', clientesAsociados: 37 }),
    ])
    montar()

    const fila = (await screen.findByText('Portal SOL caído')).closest('tr') as HTMLElement
    expect(fila.textContent).toContain('Caída o indisponibilidad del portal SOL')
    const opcion = screen.getByRole('option', { name: 'Caída o indisponibilidad del portal SOL' }) as HTMLOptionElement
    expect(opcion.value).toBe('CAIDA_PORTAL_SOL')
    expect(screen.queryByText(/Caída del portal SOL/)).toBeNull()
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
  it('la lista de tipos conserva los que ya existían con su nombre y suma los de la lista de Beto (HU-23)', async () => {
    montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    const opciones = [...(screen.getByLabelText('Tipo de incidencia') as HTMLSelectElement).options].map((o) => [o.value, o.textContent])
    expect(opciones).toEqual([
      ['CLIENTE_NO_ENVIA_DOCUMENTOS', 'Cliente no envía documentos'],
      ['RECHAZO_FRACCIONAMIENTO', 'Rechazo de fraccionamiento'],
      ['CAIDA_PORTAL_SOL', 'Caída o indisponibilidad del portal SOL'],
      ['PROBLEMA_SIRE', 'Problemas con SIRE'],
      ['PROBLEMA_SERVICIOS_EXTERNOS', 'Problemas con SUNAT u otros servicios externos'],
      ['FALLA_SISTEMA_INTERNO', 'Fallas del sistema interno'],
      ['PROBLEMA_CONEXION_INTERNET', 'Problemas de conexión a internet'],
      ['ERROR_PROCESO_CONTABLE', 'Errores en procesos contables o en la presentación de declaraciones'],
      ['OTRO', 'Otro'],
    ])
  })

  it.each([
    'PROBLEMA_SIRE', 'PROBLEMA_SERVICIOS_EXTERNOS', 'FALLA_SISTEMA_INTERNO', 'PROBLEMA_CONEXION_INTERNET', 'ERROR_PROCESO_CONTABLE',
  ] as const)('el tipo nuevo %s se registra como incidencia del cliente: con su empresa y sin pedir confirmación de general', async (tipo) => {
    vi.mocked(registrarIncidencia).mockResolvedValue(incidencia({ tipo }))
    const { setOk } = montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await userEvent.selectOptions(screen.getByLabelText('Tipo de incidencia'), tipo)
    expect(screen.queryByText(/incidencia general/i)).toBeNull()
    await escribir('Descripción del problema')
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))

    await waitFor(() => expect(registrarIncidencia).toHaveBeenCalledWith(
      expect.objectContaining({ tipo, clienteId: 4, descripcion: 'Descripción del problema' })))
    expect(window.confirm).not.toHaveBeenCalled()
    expect(setOk).toHaveBeenCalledWith('Incidencia registrada en el historial del cliente')
  })

  it('las observaciones son opcionales: se envían sin espacios sobrantes y se limpian al registrar', async () => {
    vi.mocked(registrarIncidencia).mockResolvedValue(incidencia())
    montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await escribir('Descripción')
    fireEvent.change(screen.getByLabelText('Observaciones (opcional)'), { target: { value: '  Se llamó al cliente  ' } })
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))

    await waitFor(() => expect(registrarIncidencia).toHaveBeenCalledWith(
      expect.objectContaining({ descripcion: 'Descripción', observaciones: 'Se llamó al cliente' })))
    expect((screen.getByLabelText('Observaciones (opcional)') as HTMLTextAreaElement).value).toBe('')
  })

  it('sin observaciones o en blanco no se envía el campo', async () => {
    vi.mocked(registrarIncidencia).mockResolvedValue(incidencia())
    montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await escribir('Descripción')
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))
    await waitFor(() => expect(registrarIncidencia).toHaveBeenCalledTimes(1))
    expect('observaciones' in vi.mocked(registrarIncidencia).mock.calls[0][0]).toBe(false)

    fireEvent.change(screen.getByLabelText('Observaciones (opcional)'), { target: { value: '    ' } })
    await escribir('Otra')
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))
    await waitFor(() => expect(registrarIncidencia).toHaveBeenCalledTimes(2))
    expect('observaciones' in vi.mocked(registrarIncidencia).mock.calls[1][0]).toBe(false)
  })

  it('si el servidor rechaza, las observaciones escritas no se pierden', async () => {
    vi.mocked(registrarIncidencia).mockRejectedValue(new Error('x'))
    montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')

    await escribir('Descripción')
    fireEvent.change(screen.getByLabelText('Observaciones (opcional)'), { target: { value: 'No debe perderse' } })
    await userEvent.click(screen.getByRole('button', { name: /Registrar incidencia/ }))

    await waitFor(() => expect(registrarIncidencia).toHaveBeenCalled())
    expect((screen.getByLabelText('Observaciones (opcional)') as HTMLTextAreaElement).value).toBe('No debe perderse')
  })

  it('las observaciones admiten hasta 1000 caracteres y muestran cuántos lleva', async () => {
    montar()
    await screen.findByText('Este cliente no tiene incidencias registradas.')
    const campo = screen.getByLabelText('Observaciones (opcional)') as HTMLTextAreaElement

    expect(campo.maxLength).toBe(1000)
    fireEvent.change(campo, { target: { value: 'abcde' } })
    expect(screen.getByText('5/1000')).toBeTruthy()
  })

  it('el historial muestra las observaciones de cada incidencia y un guion cuando no tiene', async () => {
    vi.mocked(listarIncidencias).mockResolvedValue([
      incidencia({ id: 3, observaciones: 'Se reintentó y siguió igual', descripcion: 'SIRE no cargó', tipo: 'PROBLEMA_SIRE' }),
      incidencia({ id: 2, observaciones: null, descripcion: 'Sin notas' }),
    ])
    montar()

    expect(await screen.findByText('Se reintentó y siguió igual')).toBeTruthy()
    const conNotas = screen.getByText('SIRE no cargó').closest('tr') as HTMLElement
    expect(conNotas.textContent).toContain('Problemas con SIRE')
    expect(screen.getByRole('columnheader', { name: 'Observaciones' })).toBeTruthy()
    const fila = screen.getByText('Sin notas').closest('tr') as HTMLElement
    expect(fila.textContent).toContain('—')
  })
})
