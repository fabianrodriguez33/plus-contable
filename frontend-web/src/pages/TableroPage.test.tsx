// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError, AxiosResponse } from 'axios'
import TableroPage from './TableroPage'
import { listarClientes } from '../services/clienteService'
import { consultarTablero, type FilaTablero, type FiltroTablero } from '../services/tableroService'
import { soles } from '../utils/societario'
import { mesActualLima } from '../utils/tablero'
import type { Cliente } from '../types'

vi.mock('../services/clienteService', () => ({ listarClientes: vi.fn() }))
vi.mock('../services/tableroService', () => ({ consultarTablero: vi.fn() }))

const ACME = { id: 4, ruc: '20123456789', razonSocial: 'Acme SAC' } as Cliente
const BETA = { id: 5, ruc: '20123456780', razonSocial: 'Beta SAC' } as Cliente

const fila = (cambios: Partial<FilaTablero>): FilaTablero => ({
  obligacionId: 1, clienteId: 4, ruc: '20123456789', razonSocial: 'Acme SAC', tipoObligacion: 'IGV', periodo: '2026-11-01',
  fechaVencimiento: '2026-11-20', estado: 'PENDIENTE', montoDeterminado: null, montoPagado: 0, saldo: null, vencida: false, sinDeclarar: false, ...cambios,
})

const CUATRO: FilaTablero[] = [
  fila({ obligacionId: 1, estado: 'PENDIENTE', tipoObligacion: 'IGV' }),
  fila({ obligacionId: 2, estado: 'DECLARADO', tipoObligacion: 'RENTA', montoDeterminado: 1000, saldo: 1000 }),
  fila({ obligacionId: 3, estado: 'PAGO_PARCIAL', tipoObligacion: 'PLAME', montoDeterminado: 1000, montoPagado: 400, saldo: 600, fechaVencimiento: '2026-11-02', vencida: true }),
  fila({ obligacionId: 4, estado: 'PAGADO', tipoObligacion: 'ESSALUD', montoDeterminado: 800, montoPagado: 800, saldo: 0, fechaVencimiento: '2026-11-02', vencida: false }),
]

function montar() {
  render(<MemoryRouter><TableroPage /></MemoryRouter>)
}

const ultimaConsulta = (): FiltroTablero => {
  const llamadas = vi.mocked(consultarTablero).mock.calls
  return llamadas[llamadas.length - 1][0] ?? {}
}

beforeEach(() => {
  vi.mocked(listarClientes).mockResolvedValue([ACME, BETA])
  vi.mocked(consultarTablero).mockResolvedValue(CUATRO)
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Tablero Declarado vs. Pagado (HU-14)', () => {
  it('criterio 1: cada obligación se ve con su estado real: pendiente, declarada, con pago parcial o pagada', async () => {
    montar()

    const tabla = await screen.findByRole('table')
    const filas = within(tabla).getAllByRole('row').slice(1)
    expect(within(filas[0]).getByText('Pendiente')).toBeTruthy()
    expect(within(filas[1]).getByText('Declarada')).toBeTruthy()
    expect(within(filas[2]).getByText('Pago parcial')).toBeTruthy()
    expect(within(filas[3]).getByText('Pagada')).toBeTruthy()
    expect(screen.getByLabelText('Resumen').textContent).toContain('1 pendientes · 1 declaradas · 1 con pago parcial · 1 pagadas')
  })

  it('una sin declarar con fecha pasada sale «Sin declarar», aparte de las vencidas', async () => {
    vi.mocked(consultarTablero).mockResolvedValue([fila({ obligacionId: 9, estado: 'PENDIENTE', fechaVencimiento: '2026-11-02', sinDeclarar: true })])
    montar()

    const f = within(await screen.findByRole('table')).getAllByRole('row')[1]
    expect(within(f).getByText('Sin declarar')).toBeTruthy()
    expect(within(f).queryByText('Vencida')).toBeNull()
    expect(screen.getByLabelText('Resumen').textContent).toContain('1 sin declarar (pendientes de atención)')
  })

  it('«Vencida» solo sale en lo que el servidor marca vencido: nunca en una obligación pagada con fecha pasada', async () => {
    montar()

    const filas = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    expect(within(filas[2]).getByText('Vencida')).toBeTruthy()          // pago parcial con saldo y fecha pasada
    expect(within(filas[3]).queryByText('Vencida')).toBeNull()          // pagada, aunque su fecha ya pasó
    expect(within(filas[3]).getByText('02/11/2026')).toBeTruthy()
    expect(within(filas[0]).queryByText('Vencida')).toBeNull()
    expect(within(filas[1]).queryByText('Vencida')).toBeNull()
    expect(screen.queryAllByText('Vencida')).toHaveLength(1)
    expect(screen.getByLabelText('Resumen').textContent).toContain('1 vencidas')
  })

  it('criterio 2: por defecto muestra el mes de hoy en Lima de todos los clientes y se puede cambiar el cliente y el mes', async () => {
    montar()
    await screen.findByRole('table')

    expect((screen.getByLabelText('Mes') as HTMLInputElement).value).toBe(mesActualLima())
    expect(ultimaConsulta()).toEqual({ clienteId: undefined, periodo: `${mesActualLima()}-01`, soloDeclaradoSinPago: false })

    await userEvent.selectOptions(screen.getByLabelText('Cliente'), '5')
    expect(ultimaConsulta()).toMatchObject({ clienteId: 5, periodo: `${mesActualLima()}-01` })

    fireEvent.change(screen.getByLabelText('Mes'), { target: { value: '2026-10' } })
    expect(ultimaConsulta()).toMatchObject({ clienteId: 5, periodo: '2026-10-01' })

    fireEvent.change(screen.getByLabelText('Mes'), { target: { value: '' } })
    expect(ultimaConsulta().periodo).toBeUndefined() // vacío = todos los meses
    await userEvent.selectOptions(screen.getByLabelText('Cliente'), '')
    expect(ultimaConsulta().clienteId).toBeUndefined()
  })

  it('criterio 3: cada obligación muestra su monto, su fecha límite, lo pagado y el saldo', async () => {
    montar()

    const filas = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    const parcial = filas[2].textContent!
    expect(parcial).toContain('PLAME')
    expect(parcial).toContain('11/2026')
    expect(parcial).toContain('02/11/2026')
    expect(parcial).toContain(soles(1000))
    expect(parcial).toContain(soles(400))
    expect(parcial).toContain(soles(600))
    // sin monto determinado todavía: guion, no cero
    const pendiente = within(filas[0]).getAllByRole('cell')
    expect(pendiente[4].textContent).toBe('—')
    expect(pendiente[6].textContent).toBe('—')
    expect(pendiente[3].textContent).toBe('20/11/2026')
  })

  it('criterio 4: «solo declaradas sin pago» pide ese filtro al servidor y se puede quitar', async () => {
    montar()
    await screen.findByRole('table')

    await userEvent.click(screen.getByLabelText('Solo declaradas sin pago'))
    expect(ultimaConsulta().soloDeclaradoSinPago).toBe(true)

    await userEvent.click(screen.getByLabelText('Solo declaradas sin pago'))
    expect(ultimaConsulta().soloDeclaradoSinPago).toBe(false)
  })

  it('sin obligaciones para el filtro lo dice y no muestra el resumen', async () => {
    vi.mocked(consultarTablero).mockResolvedValue([])
    montar()

    expect(await screen.findByText('No hay obligaciones para este filtro.')).toBeTruthy()
    expect(screen.queryByLabelText('Resumen')).toBeNull()
  })

  it('un error del servidor se muestra y no deja una lista vieja', async () => {
    vi.mocked(consultarTablero).mockRejectedValue(new AxiosError('403', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 403, data: { mensaje: 'Acceso denegado' } } as AxiosResponse))
    montar()

    expect((await screen.findByRole('alert')).textContent).toBe('Acceso denegado')
    expect(screen.getByText('No se pudo cargar el tablero.')).toBeTruthy()
  })

  it('si el filtro cambia antes de que llegue la respuesta anterior, la vieja no pisa a la nueva', async () => {
    let resolverViejo: (f: FilaTablero[]) => void = () => {}
    vi.mocked(consultarTablero)
      .mockImplementationOnce(() => new Promise((r) => { resolverViejo = r }))
      .mockResolvedValue([fila({ obligacionId: 9, razonSocial: 'Beta SAC', estado: 'PAGADO' })])
    montar()
    await screen.findByText('Cargando...')

    await userEvent.selectOptions(await screen.findByLabelText('Cliente'), '5')
    expect(await screen.findByRole('link', { name: 'Beta SAC' })).toBeTruthy()
    resolverViejo(CUATRO)
    await new Promise((r) => setTimeout(r, 20))

    expect(screen.getAllByRole('row')).toHaveLength(2) // encabezado y una fila: la respuesta vieja no entró
    expect(screen.queryByText('Declarada')).toBeNull()
  })

  it('el nombre del cliente lleva a su ficha', async () => {
    montar()

    const enlaces = await screen.findAllByRole('link', { name: 'Acme SAC' })
    expect(enlaces[0].getAttribute('href')).toBe('/clientes/4')
  })
})
