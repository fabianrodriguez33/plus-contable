// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AxiosError } from 'axios'
import PagoPage from './PagoPage'
import { buscarObligacion, type Obligacion } from '../services/obligacionService'
import { listarPagos, registrarPago, type Pago } from '../services/pagoService'
import { soles } from '../utils/societario'

vi.mock('../services/obligacionService', () => ({ buscarObligacion: vi.fn() }))
vi.mock('../services/pagoService', () => ({ listarPagos: vi.fn(), registrarPago: vi.fn() }))

const obligacion = (over: Partial<Obligacion> = {}): Obligacion => ({
  id: 3, clienteId: 9, tipoObligacion: 'IGV', periodo: '2026-10-01', fechaVencimiento: '2026-11-20',
  estado: 'DECLARADO', fechaDeclaracion: '2026-11-05', numeroFormulario: 'F-1', montoDeterminado: 1000, ...over,
})

const pago = (id: number, constancia: string, monto: number, acumulado: number, saldo: number, fecha = '2026-11-08'): Pago =>
  ({ id, fechaPago: fecha, montoPagado: monto, numeroConstancia: constancia, acumuladoDespues: acumulado, saldoDespues: saldo })

/** Para getByText: el DOM normaliza el espacio duro que pone Intl entre «S/» y el número. */
const txt = (n: number) => soles(n).replace(/\u00a0/g, ' ')

const obtener = vi.mocked(buscarObligacion)
const listar = vi.mocked(listarPagos)
const registrar = vi.mocked(registrarPago)

function pantalla() {
  return render(
    <MemoryRouter initialEntries={['/obligaciones/3/pago']}>
      <Routes>
        <Route path="/obligaciones/:obligacionId/pago" element={<PagoPage />} />
      </Routes>
    </MemoryRouter>)
}

const fecha = () => screen.getByLabelText('Fecha del pago *') as HTMLInputElement
const monto = () => screen.getByLabelText('Monto pagado (S/) *') as HTMLInputElement
const constancia = () => screen.getByLabelText('N° de constancia *') as HTMLInputElement
const boton = () => screen.getByRole('button', { name: /Registrar pago/ }) as HTMLButtonElement

async function pagar(m: string, c: string) {
  await userEvent.clear(monto())
  if (m) await userEvent.type(monto(), m)
  await userEvent.clear(constancia())
  if (c) await userEvent.type(constancia(), c)
  await userEvent.click(boton())
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 10, 9, 12)) // lunes 9 de noviembre de 2026
  obtener.mockResolvedValue(obligacion())
  listar.mockResolvedValue([])
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.useRealTimers() })

describe('Registrar pago (HU-13, tarea 7)', () => {
  it('muestra la obligación con su monto determinado, el acumulado en cero y el saldo completo cuando aún no tiene pagos', async () => {
    pantalla()

    expect(await screen.findByText('2026-10')).toBeTruthy()
    expect(screen.getByText(/vence 2026-11-20/)).toBeTruthy()
    expect(screen.getByTestId('estado').textContent).toBe('Declarada')
    expect(screen.getByTestId('determinado').textContent).toBe(soles(1000))
    expect(screen.getByTestId('acumulado').textContent).toBe(soles(0))
    expect(screen.getByTestId('saldo').textContent).toBe(soles(1000))
    expect(obtener).toHaveBeenCalledWith(3)
    expect(listar).toHaveBeenCalledWith(3)
  })

  it('el comprobante es el número de constancia: hay fecha, monto y constancia, y no hay carga de archivos', async () => {
    const { container } = pantalla()
    await screen.findByTestId('estado')

    expect(fecha().value).toBe('2026-11-09')
    expect(fecha().max).toBe('2026-11-09')
    expect(constancia().maxLength).toBe(30)
    expect(container.querySelector('input[type="file"]')).toBeNull()
    expect(screen.queryByText(/PDF|archivo|adjunt/i)).toBeNull()
  })

  it('con pagos parciales previos muestra el acumulado y el saldo del último pago y la lista con sus constancias', async () => {
    obtener.mockResolvedValue(obligacion({ estado: 'PAGO_PARCIAL' }))
    listar.mockResolvedValue([pago(2, 'C-002', 100, 500, 500, '2026-11-08'), pago(1, 'C-001', 400, 400, 600, '2026-11-02')])
    pantalla()

    expect(await screen.findByTestId('saldo')).toBeTruthy()
    expect(screen.getByTestId('acumulado').textContent).toBe(soles(500))
    expect(screen.getByTestId('saldo').textContent).toBe(soles(500))
    const tabla = screen.getByRole('table')
    expect(within(tabla).getByText('C-001')).toBeTruthy()
    expect(within(tabla).getByText('C-002')).toBeTruthy()
    expect(within(tabla).getAllByRole('row')).toHaveLength(3)
  })

  it('un pago menor que el saldo se registra como pago parcial: envía fecha, monto y constancia sin espacios y muestra el saldo que queda', async () => {
    registrar.mockResolvedValue({ ...obligacion({ estado: 'PAGO_PARCIAL' }), montoAcumuladoPagado: 400, saldo: 600, numeroPagos: 1 })
    pantalla()
    await screen.findByTestId('estado')

    await pagar('400', '  C-001 ')

    expect(registrar).toHaveBeenCalledTimes(1)
    expect(registrar).toHaveBeenCalledWith(3, { fechaPago: '2026-11-09', montoPagado: 400, numeroConstancia: 'C-001' })
    expect((await screen.findByRole('status')).textContent).toContain(`queda pago parcial con saldo de ${soles(600)}`)
    expect(monto().value).toBe('')
    expect(constancia().value).toBe('')
    await waitFor(() => expect(obtener).toHaveBeenCalledTimes(2)) // se vuelve a leer la obligación y sus pagos
    expect(listar).toHaveBeenCalledTimes(2)
  })

  it('admite varios pagos parciales: tras el primero el formulario sigue y el siguiente se valida contra el nuevo saldo', async () => {
    registrar.mockResolvedValueOnce({ ...obligacion({ estado: 'PAGO_PARCIAL' }), montoAcumuladoPagado: 400, saldo: 600, numeroPagos: 1 })
    pantalla()
    await screen.findByTestId('estado')
    obtener.mockResolvedValue(obligacion({ estado: 'PAGO_PARCIAL' }))
    listar.mockResolvedValue([pago(1, 'C-001', 400, 400, 600)])

    await pagar('400', 'C-001')
    await waitFor(() => expect(screen.getByTestId('saldo').textContent).toBe(soles(600)))
    expect(boton()).toBeTruthy()

    await pagar('600.01', 'C-002')
    expect(screen.getByText(`El monto supera el saldo pendiente de ${txt(600)}`)).toBeTruthy()
    expect(registrar).toHaveBeenCalledTimes(1)
  })

  it('el pago que salda la obligación la deja pagada: el aviso lo dice y el formulario desaparece', async () => {
    registrar.mockResolvedValue({ ...obligacion({ estado: 'PAGADO' }), montoAcumuladoPagado: 1000, saldo: 0, numeroPagos: 1 })
    pantalla()
    await screen.findByTestId('estado')
    obtener.mockResolvedValue(obligacion({ estado: 'PAGADO' }))
    listar.mockResolvedValue([pago(1, 'C-001', 1000, 1000, 0)])

    await pagar('1000', 'C-001')

    expect(await screen.findByText('Pago registrado: la obligación quedó pagada.')).toBeTruthy()
    await waitFor(() => expect(screen.queryByRole('button', { name: /Registrar pago/ })).toBeNull())
    expect(screen.getByText('La obligación ya está pagada: no admite más pagos.')).toBeTruthy()
    expect(screen.getByTestId('estado').textContent).toBe('Pagada')
  })

  it('sin datos no se envía y cada campo avisa lo suyo', async () => {
    pantalla()
    await screen.findByTestId('estado')

    await userEvent.clear(constancia())
    await userEvent.click(boton())

    expect(screen.getByText('Indique el monto pagado')).toBeTruthy()
    expect(screen.getByText('Indique el número de constancia')).toBeTruthy()
    expect(registrar).not.toHaveBeenCalled()
  })

  it('rechaza un monto en cero, negativo, con más de dos decimales o mayor que el saldo; el saldo exacto sí se admite', async () => {
    registrar.mockResolvedValue({ ...obligacion({ estado: 'PAGADO' }), montoAcumuladoPagado: 1000, saldo: 0, numeroPagos: 1 })
    pantalla()
    await screen.findByTestId('estado')

    await pagar('0', 'C-1')
    expect(screen.getByText('El monto debe ser mayor que cero')).toBeTruthy()
    await pagar('-5', 'C-1')
    expect(screen.getByText('El monto debe ser mayor que cero')).toBeTruthy()
    await pagar('10.005', 'C-1')
    expect(screen.getByText('Use como máximo dos decimales')).toBeTruthy()
    await pagar('1000.01', 'C-1')
    expect(screen.getByText(`El monto supera el saldo pendiente de ${txt(1000)}`)).toBeTruthy()
    expect(registrar).not.toHaveBeenCalled()

    await pagar('1000', 'C-1')
    expect(registrar).toHaveBeenCalledTimes(1)
  })

  it('rechaza una fecha futura y un número de constancia ya registrado en la obligación', async () => {
    obtener.mockResolvedValue(obligacion({ estado: 'PAGO_PARCIAL' }))
    listar.mockResolvedValue([pago(1, 'C-001', 400, 400, 600)])
    pantalla()
    await screen.findByTestId('saldo')

    await act(async () => {
      const { fireEvent } = await import('@testing-library/react')
      fireEvent.change(fecha(), { target: { value: '2026-11-10' } })
    })
    await pagar('100', 'C-001')

    expect(screen.getByText('La fecha del pago no puede ser futura')).toBeTruthy()
    expect(screen.getByText('Ese número de constancia ya está registrado en esta obligación')).toBeTruthy()
    expect(registrar).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza el pago muestra su mensaje, conserva lo escrito y no anuncia el registro', async () => {
    registrar.mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 400, data: { mensaje: 'Faltan o son inválidos: numeroConstancia (ya registrado en esta obligación)' } } as never))
    pantalla()
    await screen.findByTestId('estado')

    await pagar('100', 'C-9')

    expect((await screen.findByRole('alert')).textContent).toContain('ya registrado en esta obligación')
    expect(monto().value).toBe('100')
    expect(constancia().value).toBe('C-9')
    expect(screen.queryByText(/Pago registrado/)).toBeNull()
    expect(boton().disabled).toBe(false)
  })

  it('mientras se registra el botón queda deshabilitado', async () => {
    let terminar: (v: Awaited<ReturnType<typeof registrarPago>>) => void = () => {}
    registrar.mockReturnValue(new Promise((ok) => { terminar = ok }))
    pantalla()
    await screen.findByTestId('estado')

    await pagar('100', 'C-1')

    expect((screen.getByRole('button', { name: /Registrando/ }) as HTMLButtonElement).disabled).toBe(true)
    await act(async () => { terminar({ ...obligacion({ estado: 'PAGO_PARCIAL' }), montoAcumuladoPagado: 100, saldo: 900, numeroPagos: 1 }) })
    expect((await screen.findByRole('status')).textContent).toContain(soles(900))
  })

  it('una obligación sin declarar no admite pagos: no hay formulario y lleva a registrar la declaración', async () => {
    obtener.mockResolvedValue(obligacion({ estado: 'PENDIENTE', montoDeterminado: undefined, fechaDeclaracion: undefined }))
    pantalla()

    expect(await screen.findByText(/Solo se registra pago sobre una obligación ya declarada/)).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Registrar la declaración' }).getAttribute('href')).toBe('/obligaciones/3/declarar')
    expect(screen.queryByLabelText('Monto pagado (S/) *')).toBeNull()
    expect(screen.getByTestId('estado').textContent).toBe('Pendiente de declarar')
  })

  it('una obligación PAGADA con la fecha ya pasada sale «Pagada», nunca «Vencida», y no admite más pagos', async () => {
    obtener.mockResolvedValue(obligacion({ estado: 'PAGADO', fechaVencimiento: '2026-10-20' }))
    listar.mockResolvedValue([pago(1, 'C-001', 1000, 1000, 0, '2026-10-15')])
    pantalla()

    expect((await screen.findByTestId('estado')).textContent).toBe('Pagada')
    expect(screen.queryByText(/Vencida/)).toBeNull()
    expect(screen.queryByLabelText('Monto pagado (S/) *')).toBeNull()
  })

  it('una obligación DECLARADA con la fecha pasada tampoco sale «Vencida»', async () => {
    obtener.mockResolvedValue(obligacion({ estado: 'DECLARADO', fechaVencimiento: '2026-11-03' }))
    pantalla()

    expect((await screen.findByTestId('estado')).textContent).toBe('Declarada')
    expect(screen.queryByText(/Vencida/)).toBeNull()
  })

  it('con pago parcial y la fecha pasada todavía se debe saldo: dice «Pago parcial · Vencida hace…» y sigue admitiendo pagos', async () => {
    obtener.mockResolvedValue(obligacion({ estado: 'PAGO_PARCIAL', fechaVencimiento: '2026-11-07' }))
    listar.mockResolvedValue([pago(1, 'C-001', 400, 400, 600)])
    pantalla()

    expect((await screen.findByTestId('estado')).textContent).toBe('Pago parcial · Vencida hace 2 días')
    expect(boton()).toBeTruthy()
  })

  it('con pago parcial y la fecha por venir dice cuándo vence', async () => {
    obtener.mockResolvedValue(obligacion({ estado: 'PAGO_PARCIAL', fechaVencimiento: '2026-11-12' }))
    listar.mockResolvedValue([pago(1, 'C-001', 400, 400, 600)])
    pantalla()

    expect((await screen.findByTestId('estado')).textContent).toBe('Pago parcial · Vence en 3 días')
  })

  it('si no se puede cargar la obligación muestra el error y no hay formulario', async () => {
    obtener.mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 404, data: { mensaje: 'Obligación no encontrada' } } as never))
    pantalla()

    expect((await screen.findByRole('alert')).textContent).toBe('Obligación no encontrada')
    expect(screen.queryByLabelText('Monto pagado (S/) *')).toBeNull()
  })
})
