// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError } from 'axios'
import CalendarioPage from './CalendarioPage'
import { listarClientes } from '../services/clienteService'
import { consultarCalendario, type CalendarioItem } from '../services/calendarioService'
import { consultarCuotas, type CuotaCalendario } from '../services/fraccionamientoService'

vi.mock('../services/clienteService', () => ({ listarClientes: vi.fn() }))
vi.mock('../services/calendarioService', () => ({ consultarCalendario: vi.fn() }))
vi.mock('../services/fraccionamientoService', () => ({ consultarCuotas: vi.fn() }))

const HOY = new Date(2026, 10, 4, 12) // miércoles 4 de noviembre de 2026

const clientes = [
  { id: 1, ruc: '20000000001', razonSocial: 'Zeta SAC', regimenTributario: 'MYPE', condicionHabido: 'HABIDO' },
  { id: 2, ruc: '20000000002', razonSocial: 'Alfa SAC', regimenTributario: 'MYPE', condicionHabido: 'HABIDO' },
] as never

function it_(id: number, clienteId: number, fecha: string, over: Partial<CalendarioItem> = {},
             estado: 'PENDIENTE' | 'DECLARADO' = 'PENDIENTE', tipo = 'IGV'): CalendarioItem {
  return {
    obligacion: { id, clienteId, tipoObligacion: tipo, periodo: '2026-10-01', fechaVencimiento: fecha, estado },
    diasRestantes: 1, vencimientoPasado: false, ...over,
  }
}

function pantalla() {
  return render(<MemoryRouter><CalendarioPage hoy={HOY} /></MemoryRouter>)
}

const consulta = vi.mocked(consultarCalendario)
const cuotasDe = vi.mocked(consultarCuotas)

function cuota(id: number, numero: number, fecha: string, over: Partial<CuotaCalendario['cuota']> = {},
               dias = 5): CuotaCalendario {
  return {
    cuota: { id, numero, fechaVencimiento: fecha, monto: 250, estado: 'PENDIENTE', fechaPago: null, enRiesgo: false, ...over },
    fraccionamientoId: 7, diasRestantes: dias, vencida: dias < 0,
  }
}

beforeEach(() => {
  cuotasDe.mockResolvedValue([])
  vi.mocked(listarClientes).mockResolvedValue(clientes)
  consulta.mockImplementation(async (clienteId) =>
    clienteId === 1
      ? [it_(10, 1, '2026-11-12', { diasRestantes: 8 })]
      : [it_(20, 2, '2026-11-12', { diasRestantes: 8 }, 'PENDIENTE', 'RENTA'),
         it_(21, 2, '2026-11-05', { diasRestantes: 1 })])
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('CalendarioPage', () => {
  it('muestra el mes de todos los clientes agrupado por día, con cliente y tributo', async () => {
    pantalla()
    const dia12 = await screen.findByRole('region', { name: '2026-11-12' })
    expect(within(dia12).getByText('Zeta SAC')).toBeTruthy()
    expect(within(dia12).getByText('Alfa SAC')).toBeTruthy()
    expect(within(dia12).getByText(/RENTA/)).toBeTruthy()
    const orden = screen.getAllByRole('region').map((r) => r.getAttribute('aria-label'))
    expect(orden).toEqual(['2026-11-05', '2026-11-12'])
    expect(consulta).toHaveBeenCalledWith(1, { desde: '2026-11-01', hasta: '2026-11-30' })
    expect(consulta).toHaveBeenCalledWith(2, { desde: '2026-11-01', hasta: '2026-11-30' })
  })

  it('"Hoy" consulta solo el día de hoy y "Semana" de lunes a domingo', async () => {
    pantalla()
    await screen.findByRole('region', { name: '2026-11-05' })
    await userEvent.click(screen.getByRole('button', { name: 'Hoy' }))
    await waitFor(() => expect(consulta).toHaveBeenCalledWith(1, { desde: '2026-11-04', hasta: '2026-11-04' }))
    await userEvent.click(screen.getByRole('button', { name: 'Semana' }))
    await waitFor(() => expect(consulta).toHaveBeenCalledWith(1, { desde: '2026-11-02', hasta: '2026-11-08' }))
  })

  it('el filtro por cliente consulta solo a ese cliente', async () => {
    pantalla()
    await screen.findByRole('region', { name: '2026-11-05' })
    consulta.mockClear()
    await userEvent.selectOptions(screen.getByLabelText('Cliente'), '2')
    await waitFor(() => expect(consulta).toHaveBeenCalledTimes(1))
    expect(consulta).toHaveBeenCalledWith(2, expect.any(Object))
  })

  it('las obligaciones declaradas se muestran como declaradas, no como vencidas', async () => {
    consulta.mockImplementation(async (clienteId) => clienteId !== 1 ? [] : [
      it_(30, 1, '2026-11-02', { diasRestantes: -2, vencimientoPasado: true }, 'DECLARADO'),
      it_(31, 1, '2026-11-02', { diasRestantes: -2, vencimientoPasado: true }, 'PENDIENTE', 'RENTA'),
    ])
    pantalla()
    const dia = await screen.findByRole('region', { name: '2026-11-02' })
    expect(within(dia).getAllByText('Declarada')).toHaveLength(1)
    expect(within(dia).getAllByText('Vencida hace 2 días')).toHaveLength(1)
  })

  it('un error de la API muestra la alerta y conserva lo que sí llegó', async () => {
    consulta.mockImplementation(async (clienteId) => {
      if (clienteId === 2) {
        throw new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
          { status: 400, data: { mensaje: 'desde no puede superar hasta' } } as never)
      }
      return [it_(10, 1, '2026-11-12')]
    })
    pantalla()
    expect((await screen.findByRole('alert')).textContent).toContain('desde no puede superar hasta')
    expect(within(screen.getByRole('region', { name: '2026-11-12' })).getByText('Zeta SAC')).toBeTruthy()
  })

  it('un 403 muestra un mensaje de permisos', async () => {
    consulta.mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 403, data: {} } as never))
    pantalla()
    expect((await screen.findByRole('alert')).textContent).toContain('No tienes permiso')
  })

  it('cada cuota de fraccionamiento aparece en su día, junto a las obligaciones (T21, criterio 3)', async () => {
    cuotasDe.mockImplementation(async (clienteId) => clienteId === 1 ? [cuota(100, 3, '2026-11-12')] : [])
    pantalla()
    const dia = await screen.findByRole('region', { name: '2026-11-12' })
    expect(within(dia).getByText(/Cuota 3 del fraccionamiento/)).toBeTruthy()
    expect(within(dia).getByText(/S\/ 250\.00/)).toBeTruthy()
    expect(within(dia).getAllByRole('listitem')).toHaveLength(3) // obligación de Zeta, cuota de Zeta y RENTA de Alfa
    expect(cuotasDe).toHaveBeenCalledWith(1, { desde: '2026-11-01', hasta: '2026-11-30' })
    expect(cuotasDe).toHaveBeenCalledWith(2, { desde: '2026-11-01', hasta: '2026-11-30' })
  })

  it('un día con solo una cuota aparece aunque no haya obligaciones', async () => {
    consulta.mockResolvedValue([])
    cuotasDe.mockImplementation(async (clienteId) => clienteId === 2 ? [cuota(101, 1, '2026-11-20')] : [])
    pantalla()
    const dia = await screen.findByRole('region', { name: '2026-11-20' })
    expect(within(dia).getByText('Alfa SAC')).toBeTruthy()
  })

  it('los filtros de estado y situación van a la API de cuotas, y vacíos no se mandan', async () => {
    pantalla()
    await screen.findByRole('region', { name: '2026-11-05' })
    await userEvent.selectOptions(screen.getByLabelText('Estado de la cuota'), 'PAGADA')
    await waitFor(() => expect(cuotasDe).toHaveBeenCalledWith(1,
      { desde: '2026-11-01', hasta: '2026-11-30', estado: 'PAGADA' }))
    await userEvent.selectOptions(screen.getByLabelText('Situación de la cuota'), 'VENCIDO')
    await waitFor(() => expect(cuotasDe).toHaveBeenCalledWith(1,
      { desde: '2026-11-01', hasta: '2026-11-30', estado: 'PAGADA', situacion: 'VENCIDO' }))
    await userEvent.selectOptions(screen.getByLabelText('Estado de la cuota'), '')
    await userEvent.selectOptions(screen.getByLabelText('Situación de la cuota'), 'PROXIMO')
    await waitFor(() => expect(cuotasDe).toHaveBeenLastCalledWith(2,
      { desde: '2026-11-01', hasta: '2026-11-30', situacion: 'PROXIMO' }))
  })

  it('"Solo obligaciones" no consulta cuotas y "Solo cuotas" no consulta obligaciones', async () => {
    pantalla()
    await screen.findByRole('region', { name: '2026-11-05' })
    consulta.mockClear()
    cuotasDe.mockClear()
    await userEvent.selectOptions(screen.getByLabelText('Mostrar'), 'OBLIGACIONES')
    await waitFor(() => expect(consulta).toHaveBeenCalled())
    expect(cuotasDe).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('Estado de la cuota')).toBeNull()
    consulta.mockClear()
    await userEvent.selectOptions(screen.getByLabelText('Mostrar'), 'CUOTAS')
    await waitFor(() => expect(cuotasDe).toHaveBeenCalled())
    expect(consulta).not.toHaveBeenCalled()
  })

  it('si falla la consulta de cuotas, las obligaciones se siguen mostrando con la alerta', async () => {
    cuotasDe.mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 403, data: {} } as never))
    pantalla()
    expect((await screen.findByRole('alert')).textContent).toContain('No tienes permiso')
    expect(within(screen.getByRole('region', { name: '2026-11-12' })).getByText('Zeta SAC')).toBeTruthy()
  })

  it('sin vencimientos lo dice', async () => {
    consulta.mockResolvedValue([])
    pantalla()
    expect(await screen.findByText('No hay vencimientos en este periodo.')).toBeTruthy()
  })
})
