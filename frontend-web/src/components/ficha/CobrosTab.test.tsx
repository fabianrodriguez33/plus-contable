// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosResponse } from 'axios'
import CobrosTab from './CobrosTab'
import { listarCobrosPendientes, programarCobros } from '../../services/expedienteService'

vi.mock('../../services/expedienteService', () => ({ listarCobrosPendientes: vi.fn(), programarCobros: vi.fn() }))

const cobro = (id: number, fechaCobro: string, monto: number) => ({ id, fechaCobro, monto, estado: 'PENDIENTE' as const })

function error400(mensaje: string) {
  return new AxiosError('400', 'ERR_BAD_REQUEST', undefined, undefined, { status: 400, data: { mensaje } } as AxiosResponse)
}

function montar() {
  const props = { setError: vi.fn(), setOk: vi.fn(), onIrAlAcuerdo: vi.fn() }
  render(<CobrosTab clienteId={4} {...props} />)
  return props
}

beforeEach(() => { vi.mocked(listarCobrosPendientes).mockResolvedValue([]) })
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Cobros de honorarios en la ficha (HU-28)', () => {
  it('muestra los cobros pendientes del cliente con fecha, monto y total (criterio 4)', async () => {
    vi.mocked(listarCobrosPendientes).mockResolvedValue([cobro(1, '2026-11-30', 500), cobro(2, '2026-12-31', 500)])
    montar()

    expect(await screen.findByText('30/11/2026')).toBeTruthy()
    expect(screen.getByText('31/12/2026')).toBeTruthy()
    expect(screen.getAllByText('Pendiente')).toHaveLength(2)
    expect(screen.getByText(/2 cobros pendientes/)).toBeTruthy()
    expect(listarCobrosPendientes).toHaveBeenCalledWith(4)
  })

  it('sin cobros avisa que no hay pendientes', async () => {
    montar()
    expect(await screen.findByText('El cliente no tiene cobros pendientes.')).toBeTruthy()
  })

  it('se elige quincenal, se programa y la tabla muestra los cobros generados (criterios 1 y 2)', async () => {
    vi.mocked(programarCobros).mockResolvedValue({
      periodicidad: 'QUINCENAL', cobrosGenerados: 2, cobrosYaExistentes: 0,
      cobros: [cobro(5, '2026-11-15', 250), cobro(6, '2026-11-30', 250)],
    })
    const { setOk } = montar()
    await screen.findByText('El cliente no tiene cobros pendientes.')

    await userEvent.selectOptions(screen.getByLabelText('Periodicidad del cobro'), 'QUINCENAL')
    await userEvent.click(screen.getByRole('button', { name: /Programar cobros/ }))

    expect(programarCobros).toHaveBeenCalledWith(4, 'QUINCENAL')
    expect(await screen.findByText('15/11/2026')).toBeTruthy()
    expect(setOk).toHaveBeenCalledWith('Cobro quincenal programado: 2 cobros nuevos')
  })

  it('sin acuerdo pide registrarlo primero y lleva a su pestaña (criterio 3)', async () => {
    vi.mocked(programarCobros).mockRejectedValue(error400('Registre primero el acuerdo de honorarios del cliente'))
    const { setError, onIrAlAcuerdo } = montar()
    await screen.findByText('El cliente no tiene cobros pendientes.')

    await userEvent.click(screen.getByRole('button', { name: /Programar cobros/ }))

    expect(await screen.findByText(/Registre primero el acuerdo de honorarios del cliente/)).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: /Ir al acuerdo de servicio/ }))
    expect(onIrAlAcuerdo).toHaveBeenCalled()
    expect(setError).not.toHaveBeenCalledWith(expect.stringContaining('acuerdo'))
  })

  it('otro error del servidor se muestra como error de la ficha', async () => {
    vi.mocked(programarCobros).mockRejectedValue(error400('Faltan o son inválidos: periodicidad'))
    const { setError } = montar()
    await screen.findByText('El cliente no tiene cobros pendientes.')

    await userEvent.click(screen.getByRole('button', { name: /Programar cobros/ }))

    await waitFor(() => expect(setError).toHaveBeenCalledWith('Faltan o son inválidos: periodicidad'))
    expect(screen.queryByRole('button', { name: /Ir al acuerdo de servicio/ })).toBeNull()
  })
})
