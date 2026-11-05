// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SociosTab from './SociosTab'
import { actualizarAporte, guardarSocios, obtenerResumenSocietario } from '../../services/clienteService'
import type { ResumenSocietario } from '../../types'

vi.mock('../../services/clienteService', () => ({
  obtenerResumenSocietario: vi.fn(), guardarSocios: vi.fn(), actualizarAporte: vi.fn(),
}))

const resumen = (socios: ResumenSocietario['socios'], extra: Partial<ResumenSocietario> = {}): ResumenSocietario => ({
  socios, capitalSocialTotal: socios.reduce((a, s) => a + s.aporte, 0), porcentajeTotal: 100, diferencia: 0,
  completo: true, sociosSinAporte: 0, ...extra,
})

const dosSocios = resumen([
  { id: 1, nombreCompleto: 'Ana', tipoDocumento: 'DNI', numeroDocumento: '1', aporte: 60000, porcentaje: 60 },
  { id: 2, nombreCompleto: 'Luis', tipoDocumento: 'DNI', numeroDocumento: '2', aporte: 40000, porcentaje: 40 },
])

function montar() {
  const setError = vi.fn()
  const setOk = vi.fn()
  render(<SociosTab clienteId={4} setError={setError} setOk={setOk} />)
  return { setError, setOk }
}

beforeEach(() => { vi.mocked(obtenerResumenSocietario).mockResolvedValue(dosSocios) })
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Socios · el porcentaje lo calcula el sistema (HU-02)', () => {
  it('el formulario no pide porcentaje y avisa que se calcula según el aporte', async () => {
    montar()
    await screen.findByText('Ana')

    expect(screen.queryByLabelText(/% participación/i)).toBeNull()
    expect(screen.getByText(/lo calcula el sistema según el aporte/i)).toBeTruthy()
  })

  it('muestra el porcentaje guardado de cada socio y el total del 100 %', async () => {
    montar()
    await screen.findByText('Ana')

    expect(screen.getByText('60.00%')).toBeTruthy()
    expect(screen.getByText('40.00%')).toBeTruthy()
    expect(screen.getByText(/Total participación: 100.00%/)).toBeTruthy()
  })

  it('un socio agregado muestra «al guardar» y al guardar no se envía ningún porcentaje', async () => {
    vi.mocked(guardarSocios).mockResolvedValue(dosSocios)
    const { setOk } = montar()
    await screen.findByText('Ana')
    const usuario = userEvent.setup()

    await usuario.type(screen.getByLabelText('Nombre completo'), 'Marta')
    await usuario.type(screen.getByLabelText('N.º documento'), '3')
    await usuario.type(screen.getByLabelText('Aporte (S/)'), '10000')
    await usuario.click(screen.getByRole('button', { name: /Agregar socio/ }))

    expect(screen.getAllByText('al guardar').length).toBe(3)
    await usuario.click(screen.getByRole('button', { name: /Guardar estructura societaria/ }))

    await waitFor(() => expect(guardarSocios).toHaveBeenCalled())
    const enviados = vi.mocked(guardarSocios).mock.calls[0][1]
    expect(enviados).toHaveLength(3)
    expect(enviados[2]).toMatchObject({ nombreCompleto: 'Marta', aporte: 10000 })
    expect(enviados[2].porcentaje).toBeUndefined()
    await waitFor(() => expect(setOk).toHaveBeenCalled())
  })

  it('no deja agregar un socio con aporte 0', async () => {
    const { setError } = montar()
    await screen.findByText('Ana')
    const usuario = userEvent.setup()

    await usuario.type(screen.getByLabelText('Nombre completo'), 'Marta')
    await usuario.type(screen.getByLabelText('N.º documento'), '3')
    await usuario.type(screen.getByLabelText('Aporte (S/)'), '0')
    fireEvent.submit(screen.getByRole('button', { name: /Agregar socio/ }).closest('form') as HTMLFormElement)

    expect(setError).toHaveBeenCalledWith(expect.stringMatching(/mayor a 0/))
    expect(guardarSocios).not.toHaveBeenCalled()
  })

  it('al editar un aporte se muestran los porcentajes recalculados de todos los socios', async () => {
    vi.mocked(actualizarAporte).mockResolvedValue(resumen([
      { ...dosSocios.socios[0], aporte: 75000, porcentaje: 65.22 },
      { ...dosSocios.socios[1], porcentaje: 34.78 },
    ]))
    montar()
    await screen.findByText('Ana')
    const usuario = userEvent.setup()

    await usuario.click(screen.getByLabelText('Editar aporte de Ana'))
    const campo = screen.getByLabelText('Nuevo aporte')
    await usuario.clear(campo)
    await usuario.type(campo, '75000')
    await usuario.click(screen.getByLabelText('Guardar aporte'))

    expect(await screen.findByText('65.22%')).toBeTruthy()
    expect(screen.getByText('34.78%')).toBeTruthy()
    expect(actualizarAporte).toHaveBeenCalledWith(4, 1, 75000)
  })

  it('los socios anteriores sin aporte quedan marcados como incompletos y no se puede guardar', async () => {
    vi.mocked(obtenerResumenSocietario).mockResolvedValue(resumen([
      { id: 1, nombreCompleto: 'Ana', tipoDocumento: 'DNI', numeroDocumento: '1', aporte: 0, porcentaje: 60 },
      { id: 2, nombreCompleto: 'Luis', tipoDocumento: 'DNI', numeroDocumento: '2', aporte: 0, porcentaje: 40 },
    ], { completo: false, sociosSinAporte: 2 }))
    montar()
    await screen.findByText('Ana')

    expect(screen.getByText('Estructura incompleta')).toBeTruthy()
    expect(screen.getByTestId('diferencia').textContent).toMatch(/2 socios no tienen aporte/)
    expect((screen.getByRole('button', { name: /Guardar estructura societaria/ }) as HTMLButtonElement).disabled).toBe(true)
  })
})
