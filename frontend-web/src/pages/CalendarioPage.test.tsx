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
import type { EstadoObligacion } from '../services/obligacionService'

vi.mock('../services/clienteService', () => ({ listarClientes: vi.fn() }))
vi.mock('../services/calendarioService', () => ({ consultarCalendario: vi.fn() }))
vi.mock('../services/fraccionamientoService', () => ({ consultarCuotas: vi.fn() }))

const HOY = new Date(2026, 10, 4, 12) // miércoles 4 de noviembre de 2026

const clientes = [
  { id: 1, ruc: '20000000001', razonSocial: 'Zeta SAC', regimenTributario: 'MYPE', condicionHabido: 'HABIDO' },
  { id: 2, ruc: '20000000002', razonSocial: 'Alfa SAC', regimenTributario: 'MYPE', condicionHabido: 'HABIDO' },
] as never

function it_(id: number, clienteId: number, fecha: string, over: Partial<CalendarioItem> = {},
             estado: EstadoObligacion = 'PENDIENTE', tipo = 'IGV'): CalendarioItem {
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

  it('las obligaciones declaradas o con pago parcial ofrecen «Registrar pago» (HU-13); las pendientes y las pagadas no', async () => {
    consulta.mockImplementation(async (clienteId) => clienteId === 1 ? [
      it_(31, 1, '2026-11-12', {}, 'DECLARADO', 'IGV'),
      it_(32, 1, '2026-11-12', {}, 'PAGO_PARCIAL', 'RENTA'),
      it_(33, 1, '2026-11-12', {}, 'PENDIENTE', 'ESSALUD'),
      it_(34, 1, '2026-11-12', {}, 'PAGADO', 'AFP'),
    ] : [])
    pantalla()

    const dia = await screen.findByRole('region', { name: '2026-11-12' })
    const enlaces = within(dia).getAllByRole('link', { name: 'Registrar pago' })
    expect(enlaces.map((l) => l.getAttribute('href')).sort()).toEqual(['/obligaciones/31/pago', '/obligaciones/32/pago'])
  })

  it('una obligación con pago parcial lo muestra con su etiqueta y las demás no', async () => {
    consulta.mockImplementation(async (clienteId) => clienteId === 1 ? [
      it_(31, 1, '2026-11-12', {}, 'DECLARADO', 'IGV'),
      it_(32, 1, '2026-11-12', {}, 'PAGO_PARCIAL', 'RENTA'),
    ] : [])
    pantalla()

    const dia = await screen.findByRole('region', { name: '2026-11-12' })
    expect(within(dia).getAllByText('Pago parcial')).toHaveLength(1)
  })

  it('una obligación PAGADA con la fecha pasada sale «Pagada», no «Vencida», y no ofrece pagar (corrección de #43)', async () => {
    consulta.mockImplementation(async (clienteId) => clienteId === 1
      ? [it_(34, 1, '2026-11-02', { diasRestantes: -2, vencimientoPasado: true }, 'PAGADO', 'AFP')] : [])
    pantalla()

    const dia = await screen.findByRole('region', { name: '2026-11-02' })
    expect(within(dia).getByText('Pagada')).toBeTruthy()
    expect(within(dia).queryByText(/Vencida/)).toBeNull()
    expect(within(dia).queryByRole('link', { name: 'Registrar pago' })).toBeNull()
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

  it('una cuota pendiente y vencida se resalta como riesgo de perder el fraccionamiento (T22, criterio 4)', async () => {
    consulta.mockResolvedValue([])
    cuotasDe.mockImplementation(async (clienteId) => clienteId !== 1 ? [] : [
      cuota(200, 4, '2026-11-02', { enRiesgo: true }, -2),
      cuota(201, 5, '2026-11-20', {}, 16),
    ])
    pantalla()
    const riesgo = (await screen.findByRole('region', { name: '2026-11-02' })).querySelector('li') as HTMLElement
    expect(riesgo.getAttribute('data-riesgo')).toBe('true')
    expect(within(riesgo).getByText('En riesgo: vencida hace 2 días')).toBeTruthy()
    expect(within(riesgo).getByText(/Riesgo de perder el fraccionamiento/)).toBeTruthy()
    const alDia = screen.getByRole('region', { name: '2026-11-20' }).querySelector('li') as HTMLElement
    expect(alDia.getAttribute('data-riesgo')).toBe('false')
    expect(within(alDia).queryByText(/Riesgo de perder/)).toBeNull()
    expect(within(alDia).getByText('Vence en 16 días')).toBeTruthy()
  })

  it('una cuota pagada con fecha pasada sale pagada, sin riesgo ni vencida', async () => {
    consulta.mockResolvedValue([])
    cuotasDe.mockImplementation(async (clienteId) => clienteId !== 1 ? [] :
      [cuota(210, 1, '2026-10-15', { estado: 'PAGADA', fechaPago: '2026-10-14' }, -20)])
    pantalla()
    const fila = (await screen.findByRole('region', { name: '2026-10-15' })).querySelector('li') as HTMLElement
    expect(within(fila).getByText('Pagada')).toBeTruthy()
    expect(fila.getAttribute('data-riesgo')).toBe('false')
    expect(within(fila).queryByText(/vencida|riesgo/i)).toBeNull()
  })

  it('una obligación pagada con fecha pasada sale pagada, no vencida', async () => {
    consulta.mockImplementation(async (clienteId) => clienteId !== 1 ? [] : [
      it_(40, 1, '2026-11-02', { diasRestantes: -2, vencimientoPasado: true }, 'PAGADO'),
      it_(41, 1, '2026-11-02', { diasRestantes: -2, vencimientoPasado: true }, 'PENDIENTE', 'RENTA'),
    ])
    pantalla()
    const dia = await screen.findByRole('region', { name: '2026-11-02' })
    expect(within(dia).getAllByText('Pagada')).toHaveLength(1)
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
