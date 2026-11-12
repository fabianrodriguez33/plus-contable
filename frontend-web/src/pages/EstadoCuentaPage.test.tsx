// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosResponse } from 'axios'
import EstadoCuentaPage from './EstadoCuentaPage'
import { listarClientes } from '../services/clienteService'
import { emitirEstadoCuenta, type EstadoCuenta } from '../services/expedienteService'
import { soles } from '../utils/societario'
import type { Cliente } from '../types'

vi.mock('../services/clienteService', () => ({ listarClientes: vi.fn() }))
vi.mock('../services/expedienteService', () => ({ emitirEstadoCuenta: vi.fn() }))

const ACME = { id: 4, ruc: '20123456789', razonSocial: 'Acme SAC' } as Cliente

const estado = (cambios: Partial<EstadoCuenta> = {}): EstadoCuenta => ({
  clienteId: 4, razonSocial: 'Acme SAC', desde: '2026-11-01', hasta: '2026-11-30', saldoAnterior: 300,
  cobros: [{ id: 1, fechaCobro: '2026-11-15', monto: 500, pagado: 100, saldo: 400, estado: 'PAGO_PARCIAL' }],
  pagos: [
    { id: 8, cobroId: 0, fechaCobro: '2026-10-15', fechaPago: '2026-11-05', monto: 300, medioPago: 'TRANSFERENCIA', medioPagoDetalle: null },
    { id: 9, cobroId: 1, fechaCobro: '2026-11-15', fechaPago: '2026-11-30', monto: 100, medioPago: 'OTRO', medioPagoDetalle: 'Tarjeta' },
  ],
  totalCobrado: 500, totalPagado: 400, saldo: 400, sinMovimientos: false, ...cambios,
})

async function elegirYEmitir(desde = '2026-11-01', hasta = '2026-11-30') {
  await userEvent.selectOptions(await screen.findByLabelText('Cliente'), '4')
  await userEvent.clear(screen.getByLabelText('Desde'))
  await userEvent.type(screen.getByLabelText('Desde'), desde)
  await userEvent.clear(screen.getByLabelText('Hasta'))
  await userEvent.type(screen.getByLabelText('Hasta'), hasta)
  await userEvent.click(screen.getByRole('button', { name: /Ver estado de cuenta/ }))
}

beforeEach(() => { vi.mocked(listarClientes).mockResolvedValue([ACME]) })
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Estado de cuenta de honorarios (HU-30)', () => {
  it('criterio 2: se elige el cliente y el periodo; por defecto, el mes en curso', async () => {
    vi.mocked(emitirEstadoCuenta).mockResolvedValue(estado())
    render(<EstadoCuentaPage />)

    expect(await screen.findByRole('option', { name: 'Acme SAC · 20123456789' })).toBeTruthy()
    const desde = (screen.getByLabelText('Desde') as HTMLInputElement).value
    const hasta = (screen.getByLabelText('Hasta') as HTMLInputElement).value
    expect(desde).toMatch(/^\d{4}-\d{2}-01$/)
    expect(hasta.slice(0, 7)).toBe(desde.slice(0, 7))

    await elegirYEmitir()
    expect(emitirEstadoCuenta).toHaveBeenCalledWith(4, '2026-11-01', '2026-11-30')
  })

  it('criterio 1: muestra los cobros, los pagos y el saldo del periodo', async () => {
    vi.mocked(emitirEstadoCuenta).mockResolvedValue(estado())
    render(<EstadoCuentaPage />)
    await elegirYEmitir()

    const resumen = await screen.findByLabelText('Resumen')
    expect(within(resumen).getByText('Saldo anterior').nextSibling?.textContent).toBe(soles(300))
    expect(within(resumen).getByText('Cobrado en el periodo').nextSibling?.textContent).toBe(soles(500))
    expect(within(resumen).getByText('Pagado en el periodo').nextSibling?.textContent).toBe(soles(400))
    expect(within(resumen).getByText('Saldo al cierre').nextSibling?.textContent).toBe(soles(400))

    const cobros = screen.getByLabelText('Cobros del periodo')
    expect(within(cobros).getByText('15/11/2026')).toBeTruthy()
    expect(within(cobros).getByText('Pago parcial')).toBeTruthy()
    const pagos = screen.getByLabelText('Pagos del periodo')
    expect(within(pagos).getByText('05/11/2026')).toBeTruthy()
    expect(within(pagos).getByText('Transferencia bancaria')).toBeTruthy()
    expect(within(pagos).getByText('Otro: Tarjeta')).toBeTruthy()
    expect(screen.getByText(/del 01\/11\/2026 al 30\/11\/2026/)).toBeTruthy()
  })

  it('criterio 3: sin movimientos en el periodo lo indica y conserva el saldo', async () => {
    vi.mocked(emitirEstadoCuenta).mockResolvedValue(estado({ cobros: [], pagos: [], totalCobrado: 0, totalPagado: 0, saldo: 300, sinMovimientos: true }))
    render(<EstadoCuentaPage />)
    await elegirYEmitir()

    expect((await screen.findByRole('status')).textContent).toContain('El cliente no tiene cobros ni pagos en este periodo.')
    expect(screen.queryByLabelText('Cobros del periodo')).toBeNull()
    expect(within(screen.getByLabelText('Resumen')).getByText('Saldo al cierre').nextSibling?.textContent).toBe(soles(300))
  })

  it('sin cliente o con el periodo al revés avisa sin consultar al servidor', async () => {
    render(<EstadoCuentaPage />)
    await screen.findByRole('option', { name: 'Acme SAC · 20123456789' })

    await userEvent.click(screen.getByRole('button', { name: /Ver estado de cuenta/ }))
    expect((await screen.findByRole('alert')).textContent).toBe('Elija un cliente')

    await elegirYEmitir('2026-11-30', '2026-11-01')
    expect((await screen.findByRole('alert')).textContent).toContain('no puede ser posterior')
    expect(emitirEstadoCuenta).not.toHaveBeenCalled()
  })

  it('un error del servidor se muestra', async () => {
    vi.mocked(emitirEstadoCuenta).mockRejectedValue(new AxiosError('404', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 404, data: { mensaje: 'Cliente no encontrado' } } as AxiosResponse))
    render(<EstadoCuentaPage />)
    await elegirYEmitir()

    expect((await screen.findByRole('alert')).textContent).toBe('Cliente no encontrado')
  })
})
