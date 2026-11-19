// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import MiEstadoCuentaPage from './MiEstadoCuentaPage'
import { clienteIdActual } from '../services/authService'
import { listarObligaciones, type Obligacion } from '../services/obligacionService'
import { soles } from '../utils/societario'

vi.mock('../services/authService', () => ({ clienteIdActual: vi.fn() }))
vi.mock('../services/obligacionService', () => ({ listarObligaciones: vi.fn() }))

const obligaciones: Obligacion[] = [
  { id: 1, clienteId: 4, tipoObligacion: 'IGV', periodo: '2026-10-01', fechaVencimiento: '2026-11-18', estado: 'DECLARADO', montoDeterminado: 1500.5 },
  { id: 2, clienteId: 4, tipoObligacion: 'RENTA', periodo: '2026-10-01', fechaVencimiento: '2026-11-18', estado: 'PAGO_PARCIAL', montoDeterminado: 800 },
  { id: 3, clienteId: 4, tipoObligacion: 'PLAME', periodo: '2026-10-01', fechaVencimiento: '2026-11-12', estado: 'PENDIENTE' },
  { id: 4, clienteId: 4, tipoObligacion: 'IGV', periodo: '2026-09-01', fechaVencimiento: '2026-10-19', estado: 'PAGADO', montoDeterminado: 700 },
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
  it('T197: cada tributo del mes elegido con su estado, monto y fecha límite', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Mes'), { target: { value: '2026-10' } })

    expect(filasTabla()).toEqual([
      'PLAMEPendienteAún sin monto12/11/2026',
      `IGVDeclarada${soles(1500.5)}18/11/2026`,
      `RentaPago parcial${soles(800)}18/11/2026`,
    ])
  })

  it('el mes de hoy en Lima viene elegido; sin tributos lo dice y no deja una tabla vacía', async () => {
    await abrir()
    expect((screen.getByLabelText('Mes') as HTMLSelectElement).value).toBe('2026-11')
    expect(screen.getByText('No hay tributos registrados para noviembre 2026.')).toBeTruthy()
    expect(screen.queryByTestId('vista-tabla')).toBeNull()
  })

  it('T198: en el celular cada tributo es una tarjeta con los mismos datos', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Mes'), { target: { value: '2026-10' } })

    const tarjetas = within(screen.getByTestId('vista-tarjetas')).getAllByRole('listitem')
    expect(tarjetas).toHaveLength(3)
    expect(tarjetas[1].textContent).toBe(`IGVDeclaradaMonto${soles(1500.5)}Fecha límite18/11/2026`)
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
})
