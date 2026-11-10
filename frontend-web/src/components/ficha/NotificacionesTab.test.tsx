// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import NotificacionesTab from './NotificacionesTab'
import { registrarNotificacion } from '../../services/notificacionService'

vi.mock('../../services/notificacionService', () => ({ registrarNotificacion: vi.fn() }))

function montar() {
  const props = { setError: vi.fn(), setOk: vi.fn() }
  render(<NotificacionesTab clienteId={4} {...props} />)
  return props
}

const tipo = () => screen.getByLabelText('Tipo de notificación') as HTMLSelectElement
const plazo = (valor: string) => fireEvent.change(screen.getByLabelText('Plazo de respuesta'), { target: { value: valor } })
const recepcion = (valor: string) => fireEvent.change(screen.getByLabelText('Fecha de recepción'), { target: { value: valor } })
const registrar = () => userEvent.click(screen.getByRole('button', { name: /Registrar notificación/ }))

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 10, 9, 12)) // lunes 9 de noviembre de 2026
  vi.mocked(registrarNotificacion).mockResolvedValue({
    id: 1, clienteId: 4, tipo: 'ESQUELA', fechaRecepcion: '2026-11-09', plazoRespuesta: '2026-11-20', estado: 'PENDIENTE',
  })
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.useRealTimers() })

describe('Registrar una notificación del Buzón SOL en la ficha (HU-18, T65 y T66)', () => {
  it('el tipo se elige entre esquela, carta inductiva o comunicado (criterio 2)', () => {
    montar()
    expect([...tipo().options].map((o) => o.textContent)).toEqual(['Seleccione el tipo', 'Esquela', 'Carta inductiva', 'Comunicado'])
    expect([...tipo().options].map((o) => o.value)).toEqual(['', 'ESQUELA', 'CARTA_INDUCTIVA', 'COMUNICADO'])
    expect(tipo().value).toBe('')
  })

  it('registra con tipo, fecha de recepción y plazo, avisa y deja el formulario listo para otra', async () => {
    const { setOk, setError } = montar()
    await userEvent.selectOptions(tipo(), 'CARTA_INDUCTIVA')
    plazo('2026-11-20')
    await registrar()

    expect(registrarNotificacion).toHaveBeenCalledWith(4,
      { tipo: 'CARTA_INDUCTIVA', fechaRecepcion: '2026-11-09', plazoRespuesta: '2026-11-20' })
    expect(setOk).toHaveBeenCalledWith('Notificación registrada: queda pendiente de atención')
    expect(setError).toHaveBeenLastCalledWith('')
    expect(tipo().value).toBe('')
    expect((screen.getByLabelText('Plazo de respuesta') as HTMLInputElement).value).toBe('')
  })

  it('si no se indica el plazo de respuesta, no se guarda (criterio 3)', async () => {
    montar()
    await userEvent.selectOptions(tipo(), 'ESQUELA')
    await registrar()

    expect(registrarNotificacion).not.toHaveBeenCalled()
    expect((await screen.findByRole('alert')).textContent).toContain('sin plazo la notificación no se guarda')
  })

  it('sin tipo no se guarda', async () => {
    montar()
    plazo('2026-11-20')
    await registrar()

    expect(registrarNotificacion).not.toHaveBeenCalled()
    expect((await screen.findByRole('alert')).textContent).toContain('Elija el tipo de notificación')
  })

  it('el plazo no puede ser anterior a la recepción, pero el mismo día sí vale', async () => {
    montar()
    await userEvent.selectOptions(tipo(), 'COMUNICADO')
    recepcion('2026-11-05')
    plazo('2026-11-04')
    await registrar()
    expect(registrarNotificacion).not.toHaveBeenCalled()
    expect((await screen.findByRole('alert')).textContent).toContain('no puede ser anterior a la fecha de recepción')

    plazo('2026-11-05')
    await registrar()
    expect(registrarNotificacion).toHaveBeenCalledWith(4,
      { tipo: 'COMUNICADO', fechaRecepcion: '2026-11-05', plazoRespuesta: '2026-11-05' })
  })

  it('la recepción no puede ser futura, pero hoy sí vale', async () => {
    montar()
    await userEvent.selectOptions(tipo(), 'ESQUELA')
    plazo('2026-11-30')
    recepcion('2026-11-10')
    await registrar()
    expect(registrarNotificacion).not.toHaveBeenCalled()
    expect((await screen.findByRole('alert')).textContent).toContain('no puede ser futura')

    recepcion('2026-11-09')
    await registrar()
    expect(registrarNotificacion).toHaveBeenCalledTimes(1)
  })

  it('un error del servidor se muestra y lo escrito no se pierde', async () => {
    vi.mocked(registrarNotificacion).mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 404, data: { mensaje: 'Cliente no encontrado' } } as never))
    const { setError, setOk } = montar()
    await userEvent.selectOptions(tipo(), 'ESQUELA')
    plazo('2026-11-20')
    await registrar()

    expect(setError).toHaveBeenLastCalledWith('Cliente no encontrado')
    expect(setOk).not.toHaveBeenCalledWith(expect.stringContaining('registrada'))
    expect(tipo().value).toBe('ESQUELA')
    expect((screen.getByLabelText('Plazo de respuesta') as HTMLInputElement).value).toBe('2026-11-20')
  })
})
