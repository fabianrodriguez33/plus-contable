// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosResponse } from 'axios'
import CobrosTab from './CobrosTab'
import { soles } from '../../utils/societario'
import { CobroHonorario, listarCobrosPendientes, programarCobros, registrarPagoHonorario } from '../../services/expedienteService'

vi.mock('../../services/expedienteService', () => ({
  listarCobrosPendientes: vi.fn(), programarCobros: vi.fn(), registrarPagoHonorario: vi.fn(),
}))

const cobro = (id: number, fechaCobro: string, monto: number): CobroHonorario =>
  ({ id, fechaCobro, monto, pagado: 0, saldo: monto, estado: 'PENDIENTE' })

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

describe('Pago de honorarios en la ficha (HU-29)', () => {
  it('registra un pago parcial con fecha, monto y medio; el cobro sigue en la lista con su saldo (criterios 1 y 3)', async () => {
    vi.mocked(listarCobrosPendientes)
      .mockResolvedValueOnce([cobro(1, '2026-11-30', 500)])
      .mockResolvedValueOnce([{ ...cobro(1, '2026-11-30', 500), pagado: 200, saldo: 300, estado: 'PAGO_PARCIAL' }])
    vi.mocked(registrarPagoHonorario).mockResolvedValue(
      { ...cobro(1, '2026-11-30', 500), pagado: 200, saldo: 300, estado: 'PAGO_PARCIAL' })
    const { setOk } = montar()

    await userEvent.click(await screen.findByRole('button', { name: /Registrar pago/ }))
    const dialogo = screen.getByRole('dialog')
    const monto = within(dialogo).getByLabelText('Monto pagado (S/)')
    expect((monto as HTMLInputElement).value).toBe('500')
    await userEvent.clear(monto)
    await userEvent.type(monto, '200')
    await userEvent.selectOptions(within(dialogo).getByLabelText('Medio de pago'), 'YAPE_PLIN')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))

    expect(registrarPagoHonorario).toHaveBeenCalledWith(4, 1, expect.objectContaining({ monto: 200, medioPago: 'YAPE_PLIN' }))
    expect(vi.mocked(registrarPagoHonorario).mock.calls[0][2].fechaPago).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    await waitFor(() => expect(setOk).toHaveBeenCalledWith(`Pago parcial registrado: queda un saldo de ${soles(300)}`))
    expect(await screen.findByText('Pago parcial')).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('con «Otro» pide especificar el medio y lo envía; con los demás no se muestra ni se envía', async () => {
    vi.mocked(listarCobrosPendientes).mockResolvedValue([cobro(1, '2026-11-30', 500)])
    vi.mocked(registrarPagoHonorario).mockResolvedValue({ ...cobro(1, '2026-11-30', 500), pagado: 500, saldo: 0, estado: 'PAGADO' })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Registrar pago/ }))
    const dialogo = screen.getByRole('dialog')
    expect(within(dialogo).getByRole('option', { name: 'Transferencia bancaria' })).toBeTruthy()
    expect(within(dialogo).getByRole('option', { name: 'Depósito bancario' })).toBeTruthy()
    expect(within(dialogo).queryByLabelText('Especifique el medio de pago')).toBeNull()

    await userEvent.selectOptions(within(dialogo).getByLabelText('Medio de pago'), 'OTRO')
    const detalle = within(dialogo).getByLabelText('Especifique el medio de pago') as HTMLInputElement
    expect(detalle.required).toBe(true)
    await userEvent.type(detalle, ' Tarjeta de crédito ')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))

    expect(registrarPagoHonorario).toHaveBeenCalledWith(4, 1,
      expect.objectContaining({ medioPago: 'OTRO', medioPagoDetalle: 'Tarjeta de crédito' }))
  })

  it('con un medio de la lista el detalle va vacío', async () => {
    vi.mocked(listarCobrosPendientes).mockResolvedValue([cobro(1, '2026-11-30', 500)])
    vi.mocked(registrarPagoHonorario).mockResolvedValue({ ...cobro(1, '2026-11-30', 500), pagado: 500, saldo: 0, estado: 'PAGADO' })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Registrar pago/ }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Registrar pago' }))

    expect(registrarPagoHonorario).toHaveBeenCalledWith(4, 1,
      expect.objectContaining({ medioPago: 'TRANSFERENCIA', medioPagoDetalle: null }))
  })

  it('un pago por el total avisa que el cobro quedó pagado (criterio 2)', async () => {
    vi.mocked(listarCobrosPendientes).mockResolvedValueOnce([cobro(1, '2026-11-30', 500)]).mockResolvedValueOnce([])
    vi.mocked(registrarPagoHonorario).mockResolvedValue({ ...cobro(1, '2026-11-30', 500), pagado: 500, saldo: 0, estado: 'PAGADO' })
    const { setOk } = montar()

    await userEvent.click(await screen.findByRole('button', { name: /Registrar pago/ }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Registrar pago' }))

    await waitFor(() => expect(setOk).toHaveBeenCalledWith('Pago registrado: el cobro del 30/11/2026 quedó pagado'))
    expect(await screen.findByText('El cliente no tiene cobros pendientes.')).toBeTruthy()
  })

  it('el rechazo del servidor se muestra dentro del formulario y no lo cierra (criterio 4)', async () => {
    vi.mocked(listarCobrosPendientes).mockResolvedValue([cobro(1, '2026-11-30', 500)])
    vi.mocked(registrarPagoHonorario).mockRejectedValue(
      error400('Solo se registran pagos sobre un cobro pendiente: este cobro ya está pagado'))
    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Registrar pago/ }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Registrar pago' }))

    expect(await within(screen.getByRole('dialog')).findByText(/este cobro ya está pagado/)).toBeTruthy()
  })
})
