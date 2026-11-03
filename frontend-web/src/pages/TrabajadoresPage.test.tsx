// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AxiosError } from 'axios'
import TrabajadoresPage from './TrabajadoresPage'
import { listarTrabajadores, registrarTrabajador, type Trabajador } from '../services/expedienteService'

vi.mock('../services/expedienteService', () => ({ listarTrabajadores: vi.fn(), registrarTrabajador: vi.fn() }))

const listar = vi.mocked(listarTrabajadores)
const registrar = vi.mocked(registrarTrabajador)

const trabajador = (over: Partial<Trabajador>): Trabajador => ({
  id: 1, nombreCompleto: 'Ana Quispe', tipoDocumento: 'DNI', numeroDocumento: '70000001', extranjero: false,
  sistemaPensionario: 'ONP', fechaIngreso: '2025-03-01', ...over,
})

function pantalla() {
  return render(
    <MemoryRouter initialEntries={['/clientes/7']}>
      <Routes><Route path="/clientes/:clienteId" element={<TrabajadoresPage />} /></Routes>
    </MemoryRouter>)
}

/** Celda "Sistema pensionario" (4.ª columna) de la fila del trabajador. */
function sistemaDe(nombre: string): string {
  const fila = screen.getByText(nombre).closest('tr') as HTMLElement
  return within(fila).getAllByRole('cell')[3].textContent ?? ''
}

beforeEach(() => {
  listar.mockResolvedValue([
    trabajador({ id: 1, nombreCompleto: 'Ana Quispe', sistemaPensionario: 'ONP' }),
    trabajador({ id: 2, nombreCompleto: 'Luis Rojas', numeroDocumento: '70000002', sistemaPensionario: 'AFP', nombreAfp: 'Integra' }),
    trabajador({ id: 3, nombreCompleto: 'Mara Ortiz', numeroDocumento: '70000003', sistemaPensionario: 'AFP' }),
  ])
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('TrabajadoresPage · lista con sistema pensionario (HU-03)', () => {
  it('la tabla tiene la columna "Sistema pensionario" y pide los trabajadores del cliente de la ruta', async () => {
    pantalla()
    await screen.findByText('Ana Quispe')

    expect(screen.getByRole('columnheader', { name: 'Sistema pensionario' })).toBeTruthy()
    expect(listar).toHaveBeenCalledWith(7)
  })

  it('muestra ONP para quien está en la ONP', async () => {
    pantalla()
    await screen.findByText('Ana Quispe')

    expect(sistemaDe('Ana Quispe')).toBe('ONP')
  })

  it('muestra AFP con el nombre de la AFP, y solo AFP si no hay nombre', async () => {
    pantalla()
    await screen.findByText('Luis Rojas')

    expect(sistemaDe('Luis Rojas')).toBe('AFP · Integra')
    expect(sistemaDe('Mara Ortiz')).toBe('AFP')
  })

  it('cada trabajador aparece con su propio sistema, sin mezclar filas', async () => {
    pantalla()
    await screen.findByText('Ana Quispe')

    const filas = screen.getAllByRole('row').slice(1)
    expect(filas).toHaveLength(3)
    expect(filas.map((f) => within(f).getAllByRole('cell')[3].textContent)).toEqual(['ONP', 'AFP · Integra', 'AFP'])
  })

  it('un trabajador recién registrado entra a la lista con su sistema pensionario', async () => {
    listar.mockResolvedValue([])
    registrar.mockResolvedValue(trabajador({ id: 9, nombreCompleto: 'Rosa Vela', numeroDocumento: '70000009',
      sistemaPensionario: 'AFP', nombreAfp: 'Prima' }))
    pantalla()

    await userEvent.type(screen.getByLabelText('Nombre completo *'), 'Rosa Vela')
    await userEvent.type(screen.getByLabelText('Número de documento *'), '70000009')
    await userEvent.type(screen.getByLabelText('Fecha de ingreso *'), '2026-01-15')
    await userEvent.selectOptions(screen.getByLabelText('Sistema pensionario *'), 'AFP')
    await userEvent.type(screen.getByLabelText('Nombre de la AFP *'), 'Prima')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar trabajador' }))

    await screen.findByText('Rosa Vela')
    expect(sistemaDe('Rosa Vela')).toBe('AFP · Prima')
    expect(registrar).toHaveBeenCalledWith(7, expect.objectContaining({ sistemaPensionario: 'AFP', nombreAfp: 'Prima' }))
  })

  it('si la lista no carga se informa y no se muestra ninguna fila', async () => {
    listar.mockRejectedValue(new AxiosError('x'))
    pantalla()

    expect((await screen.findByRole('alert')).textContent).toBe('No se pudo conectar con el servidor')
    expect(screen.queryByText('Ana Quispe')).toBeNull()
  })
})
