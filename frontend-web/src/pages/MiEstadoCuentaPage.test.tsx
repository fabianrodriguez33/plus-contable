// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import MiEstadoCuentaPage, { ETIQUETA_VENCIDA } from './MiEstadoCuentaPage'
import { clienteIdActual } from '../services/authService'
import { listarObligaciones, type Obligacion } from '../services/obligacionService'
import { soles } from '../utils/societario'

vi.mock('../services/authService', () => ({ clienteIdActual: vi.fn() }))
vi.mock('../services/obligacionService', () => ({ listarObligaciones: vi.fn() }))

// «Hoy» en estas pruebas es el 18/11/2026 (Lima).
const obligaciones: Obligacion[] = [
  { id: 1, clienteId: 4, tipoObligacion: 'IGV', periodo: '2026-10-01', fechaVencimiento: '2026-11-13', estado: 'DECLARADO', montoDeterminado: 1500.5 },
  { id: 2, clienteId: 4, tipoObligacion: 'RENTA', periodo: '2026-10-01', fechaVencimiento: '2026-11-13', estado: 'PAGO_PARCIAL', montoDeterminado: 800, montoAcumuladoPagado: 300, saldo: 500 },
  { id: 3, clienteId: 4, tipoObligacion: 'PLAME', periodo: '2026-10-01', fechaVencimiento: '2026-11-12', estado: 'PENDIENTE' },
  { id: 4, clienteId: 4, tipoObligacion: 'AFP', periodo: '2026-10-01', fechaVencimiento: '2026-11-10', estado: 'PAGADO', montoDeterminado: 700 },
  { id: 5, clienteId: 4, tipoObligacion: 'ESSALUD', periodo: '2026-10-01', fechaVencimiento: '2026-11-25', estado: 'DECLARADO', montoDeterminado: 200 },
  { id: 6, clienteId: 4, tipoObligacion: 'IGV', periodo: '2026-11-01', fechaVencimiento: '2026-12-18', estado: 'DECLARADO', montoDeterminado: 900 },
]

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-18T17:00:00Z')) // 12:00 en Lima: mes actual 2026-11
  localStorage.clear()
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.useRealTimers() })

async function abrir() {
  vi.mocked(clienteIdActual).mockReturnValue(4)
  vi.mocked(listarObligaciones).mockResolvedValue(obligaciones)
  render(<MiEstadoCuentaPage />)
  await screen.findByLabelText('Mes')
}

const filasTabla = () => within(screen.getByTestId('vista-tabla')).getAllByRole('row').slice(1).map((f) => f.textContent)

describe('Mi estado de cuenta (HU-32)', () => {
  it('T197: cada tributo del mes con estado, monto, pagado, saldo y fecha límite; las vencidas marcadas', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Mes'), { target: { value: '2026-10' } })

    expect(filasTabla()).toEqual([
      'AFPPagada' + soles(700) + soles(700) + soles(0) + '10/11/2026',
      'PLAMEPendienteAún sin monto——12/11/2026',
      'IGVDeclaradaVencida' + soles(1500.5) + soles(0) + soles(1500.5) + '13/11/2026',
      'RentaPago parcialVencida' + soles(800) + soles(300) + soles(500) + '13/11/2026',
      'EsSalud' + 'Declarada' + soles(200) + soles(0) + soles(200) + '25/11/2026',
    ])
  })

  it('Beto: con pago parcial sigue vencida y muestra lo pagado y el saldo; la pagada y la sin declarar no se marcan', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Mes'), { target: { value: '2026-10' } })
    const filas = within(screen.getByTestId('vista-tabla')).getAllByRole('row').slice(1)
    const conMarca = filas.filter((f) => within(f).queryByText(ETIQUETA_VENCIDA)).map((f) => (f as HTMLTableRowElement).cells[0].textContent)
    expect(conMarca).toEqual(['IGV', 'Renta'])
    expect(within(screen.getByTestId('vista-tabla')).getAllByText(ETIQUETA_VENCIDA)[0].className).toContain('bg-red-600')
  })

  it('el mes de hoy en Lima viene elegido y muestra sus tributos', async () => {
    await abrir()
    expect((screen.getByLabelText('Mes') as HTMLSelectElement).value).toBe('2026-11')
    expect(screen.queryByText('No hay tributos registrados para noviembre 2026.')).toBeNull()
    expect(within(screen.getByTestId('vista-tabla')).getAllByRole('row')).toHaveLength(2) // encabezado + el IGV de noviembre
  })

  it('T198: en el celular cada tributo es una tarjeta con los mismos datos, también pagado y saldo', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Mes'), { target: { value: '2026-10' } })

    const tarjetas = within(screen.getByTestId('vista-tarjetas')).getAllByRole('listitem')
    expect(tarjetas).toHaveLength(5)
    expect(tarjetas[3].textContent).toBe(`RentaPago parcialVencidaMonto${soles(800)}Fecha límite13/11/2026Pagado${soles(300)}Saldo${soles(500)}`)
    expect(screen.getByTestId('vista-tabla').className).toContain('hidden md:block')
    expect(screen.getByTestId('vista-tarjetas').className).toContain('md:hidden')
  })

  it('T199: pide solo la empresa de su propio token', async () => {
    await abrir()
    expect(listarObligaciones).toHaveBeenCalledTimes(1)
    expect(listarObligaciones).toHaveBeenCalledWith(4)
  })

  it('es solo lectura: el único control es el selector de mes', async () => {
    await abrir()
    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(screen.getAllByRole('combobox')).toHaveLength(1)
  })

  it('una cuenta sin empresa no pide datos y lo dice', () => {
    vi.mocked(clienteIdActual).mockReturnValue(null)
    render(<MiEstadoCuentaPage />)
    expect(screen.getByRole('alert').textContent).toMatch(/no está vinculada a una empresa/)
    expect(listarObligaciones).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza la petición muestra el mensaje', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(listarObligaciones).mockRejectedValue(new Error('x'))
    render(<MiEstadoCuentaPage />)
    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
  })

  it('un mes sin tributos lo dice y no deja una tabla vacía', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(listarObligaciones).mockResolvedValue(obligaciones.filter((o) => o.periodo !== '2026-11-01'))
    render(<MiEstadoCuentaPage />)
    await screen.findByLabelText('Mes')
    expect(screen.getByText('No hay tributos registrados para noviembre 2026.')).toBeTruthy()
    expect(screen.queryByTestId('vista-tabla')).toBeNull()
    expect(screen.queryByTestId('vista-tarjetas')).toBeNull()
  })
})
