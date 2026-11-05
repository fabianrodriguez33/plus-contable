// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AxiosError } from 'axios'
import ProgramacionPage from './ProgramacionPage'
import { programarMes, type ProgramacionMes } from '../services/programacionService'

vi.mock('../services/programacionService', () => ({ programarMes: vi.fn() }))

const HOY = new Date(2026, 10, 4, 12) // miércoles 4 de noviembre de 2026
const programar = vi.mocked(programarMes)

function pantalla() {
  return render(
    <MemoryRouter initialEntries={['/clientes/7']}>
      <Routes><Route path="/clientes/:clienteId" element={<ProgramacionPage hoy={HOY} />} /></Routes>
    </MemoryRouter>)
}

const respuesta = (over: Partial<ProgramacionMes> = {}): ProgramacionMes => ({
  periodo: '2026-10',
  fechaVencimiento: '2026-11-20',
  creadas: [
    { id: 1, tipoObligacion: 'IGV', periodo: '2026-10-01', fechaVencimiento: '2026-11-20', estado: 'PENDIENTE' },
    { id: 2, tipoObligacion: 'RENTA', periodo: '2026-10-01', fechaVencimiento: '2026-11-20', estado: 'PENDIENTE' },
    { id: 3, tipoObligacion: 'PLAME', periodo: '2026-10-01', fechaVencimiento: '2026-11-20', estado: 'PENDIENTE' },
  ],
  yaExistian: [],
  noGeneradas: [
    { tipo: 'ESSALUD', motivo: 'Pendiente de confirmar la regla del estudio' },
    { tipo: 'RETENCION_QUINTA', motivo: 'Pendiente de regla de proyección' },
    { tipo: 'AFP', motivo: 'AFP tiene su propio cronograma, que aún no está disponible' },
  ],
  ...over,
})

afterEach(() => { cleanup(); vi.clearAllMocks() })
beforeEach(() => { programar.mockResolvedValue(respuesta()) })

describe('ProgramacionPage', () => {
  it('propone el mes anterior al de hoy', () => {
    pantalla()
    expect((screen.getByLabelText('Mes a programar') as HTMLInputElement).value).toBe('2026-10')
  })

  it('programa el mes elegido del cliente de la ruta y muestra creadas, ya existentes y no generadas', async () => {
    programar.mockResolvedValue(respuesta({ yaExistian: ['PLAME'] }))
    pantalla()
    await userEvent.click(screen.getByRole('button', { name: 'Programar' }))

    expect(programar).toHaveBeenCalledWith(7, '2026-10')
    const creadas = await screen.findByRole('region', { name: 'Creadas' })
    expect(within(creadas).getByText('Creadas (3)')).toBeTruthy()
    expect(within(creadas).getByText(/IGV: vence el 20\/11\/2026/)).toBeTruthy()
    expect(within(creadas).getByText(/Renta: vence el 20\/11\/2026/)).toBeTruthy()
    expect(within(screen.getByRole('region', { name: 'Ya existentes' })).getByText('PLAME')).toBeTruthy()
    const no = screen.getByRole('region', { name: 'No generadas' })
    expect(within(no).getByText('No generadas (3)')).toBeTruthy()
    expect(within(no).getByText(/Pendiente de confirmar la regla del estudio/)).toBeTruthy()
    expect(within(no).getByText(/Pendiente de regla de proyección/)).toBeTruthy()
    expect(within(no).getByText(/AFP tiene su propio cronograma/)).toBeTruthy()
    expect(screen.getByText('20/11/2026', { selector: 'strong' })).toBeTruthy()
  })

  it('muestra exactamente el mensaje del servidor cuando falta el cronograma', async () => {
    programar.mockRejectedValue(new AxiosError('x', '400', undefined, undefined,
      { status: 400, data: { mensaje: 'Falta el cronograma de 2027-01' } } as never))
    pantalla()
    await userEvent.clear(screen.getByLabelText('Mes a programar'))
    await userEvent.type(screen.getByLabelText('Mes a programar'), '2027-01')
    await userEvent.click(screen.getByRole('button', { name: 'Programar' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta.textContent).toBe('Falta el cronograma de 2027-01')
    expect(programar).toHaveBeenCalledWith(7, '2027-01')
    expect(screen.queryByRole('region', { name: 'Creadas' })).toBeNull()
  })

  it('un servidor caído se informa sin mostrar resultados', async () => {
    programar.mockRejectedValue(new AxiosError('x'))
    pantalla()
    await userEvent.click(screen.getByRole('button', { name: 'Programar' }))

    expect((await screen.findByRole('alert')).textContent).toBe('No se pudo conectar con el servidor')
  })

  it('sin mes elegido el botón queda deshabilitado', async () => {
    pantalla()
    await userEvent.clear(screen.getByLabelText('Mes a programar'))

    expect((screen.getByRole('button', { name: 'Programar' }) as HTMLButtonElement).disabled).toBe(true)
    expect(programar).not.toHaveBeenCalled()
  })

  it('mientras programa deshabilita el botón y evita un doble envío', async () => {
    let resolver: (r: ProgramacionMes) => void = () => {}
    programar.mockReturnValue(new Promise((r) => { resolver = r }))
    pantalla()
    await userEvent.click(screen.getByRole('button', { name: 'Programar' }))

    const boton = await screen.findByRole('button', { name: 'Programando…' }) as HTMLButtonElement
    expect(boton.disabled).toBe(true)
    resolver(respuesta())
    await waitFor(() => expect(screen.getByRole('button', { name: 'Programar' })).toBeTruthy())
    expect(programar).toHaveBeenCalledTimes(1)
  })

  it('al cambiar de mes se limpia el resultado anterior', async () => {
    pantalla()
    await userEvent.click(screen.getByRole('button', { name: 'Programar' }))
    await screen.findByRole('region', { name: 'Creadas' })

    await userEvent.clear(screen.getByLabelText('Mes a programar'))
    await userEvent.type(screen.getByLabelText('Mes a programar'), '2026-09')

    expect(screen.queryByRole('region', { name: 'Creadas' })).toBeNull()
  })

  it('con todo ya programado dice que no se creó nada nuevo', async () => {
    programar.mockResolvedValue(respuesta({
      creadas: [], yaExistian: ['IGV', 'RENTA', 'PLAME'],
      noGeneradas: [{ tipo: 'AFP', motivo: 'AFP tiene su propio cronograma, que aún no está disponible' }] }))
    pantalla()
    await userEvent.click(screen.getByRole('button', { name: 'Programar' }))

    expect(await screen.findByText('No se creó ninguna obligación nueva.')).toBeTruthy()
    expect(within(screen.getByRole('region', { name: 'Ya existentes' })).getAllByRole('listitem')).toHaveLength(3)
  })
})
