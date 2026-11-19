// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import NotificacionesTab from './NotificacionesTab'
import {
  listarNotificaciones, marcarAtendida, Notificacion, registrarNotificacion,
} from '../../services/notificacionService'

vi.mock('../../services/notificacionService', () => ({
  registrarNotificacion: vi.fn(), listarNotificaciones: vi.fn(), marcarAtendida: vi.fn(),
}))

const pendiente = (id: number, plazo: string, tipo: Notificacion['tipo'] = 'ESQUELA'): Notificacion => ({
  id, clienteId: 4, tipo, fechaRecepcion: '2026-11-02', plazoRespuesta: plazo, estado: 'PENDIENTE', fechaAtencion: null,
})

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
  vi.mocked(listarNotificaciones).mockResolvedValue([])
  vi.stubGlobal('confirm', vi.fn(() => true))
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 10, 9, 12)) // lunes 9 de noviembre de 2026
  vi.mocked(registrarNotificacion).mockResolvedValue({
    id: 1, clienteId: 4, tipo: 'ESQUELA', fechaRecepcion: '2026-11-09', plazoRespuesta: '2026-11-20', estado: 'PENDIENTE',
  })
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals() })

describe('Notificaciones del Buzón SOL en la ficha (HU-18, T65, T66 y T67)', () => {
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
  it('lista las pendientes del cliente con su tipo, recepción y plazo, en el orden del servidor (T67, criterio 4)', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue([
      pendiente(2, '2026-11-12', 'COMUNICADO'), pendiente(1, '2026-11-30', 'CARTA_INDUCTIVA')])
    montar()

    expect(await screen.findByText('Comunicado')).toBeTruthy()
    const filas = screen.getAllByRole('row').slice(1)
    expect(filas).toHaveLength(2)
    expect(within(filas[0]).getByText('Comunicado')).toBeTruthy()
    expect(within(filas[1]).getByText('Carta inductiva')).toBeTruthy()
    expect(listarNotificaciones).toHaveBeenCalledWith(4, 'PENDIENTE')
  })

  it('sin pendientes lo dice', async () => {
    montar()
    expect(await screen.findByText('Este cliente no tiene notificaciones pendientes.')).toBeTruthy()
  })

  it('una notificación con el plazo ya vencido se marca como vencida y una al día no', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue([pendiente(1, '2026-11-05'), pendiente(2, '2026-11-09'), pendiente(3, '2026-11-20')])
    montar()
    await screen.findAllByText('Esquela')

    expect(screen.getAllByText('Plazo vencido')).toHaveLength(1) // solo la del 05/11: la del 09/11 vence hoy
  })

  it('al registrar una notificación la lista de pendientes se vuelve a pedir', async () => {
    montar()
    await screen.findByText('Este cliente no tiene notificaciones pendientes.')
    await userEvent.selectOptions(tipo(), 'ESQUELA')
    plazo('2026-11-20')
    vi.mocked(listarNotificaciones).mockResolvedValue([pendiente(9, '2026-11-20')])
    await registrar()

    await waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(2)) // encabezado y la nueva pendiente
    expect(within(screen.getAllByRole('row')[1]).getByText('Esquela')).toBeTruthy()
    expect(listarNotificaciones).toHaveBeenCalledTimes(2)
  })

  it('«Marcar atendida» pide confirmar, marca la notificación y la saca de la lista', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValueOnce([pendiente(7, '2026-11-20')]).mockResolvedValue([])
    vi.mocked(marcarAtendida).mockResolvedValue({ ...pendiente(7, '2026-11-20'), estado: 'ATENDIDA', fechaAtencion: '2026-11-09' })
    const { setOk, setError } = montar()
    await userEvent.click(await screen.findByRole('button', { name: /Marcar atendida/ }))

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('No se puede deshacer'))
    expect(marcarAtendida).toHaveBeenCalledWith(7)
    expect(setOk).toHaveBeenCalledWith('Notificación marcada como atendida')
    expect(setError).toHaveBeenLastCalledWith('')
    expect(await screen.findByText('Este cliente no tiene notificaciones pendientes.')).toBeTruthy()
  })

  it('si se cancela la confirmación no se marca como atendida', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false))
    vi.mocked(listarNotificaciones).mockResolvedValue([pendiente(7, '2026-11-20')])
    montar()
    await userEvent.click(await screen.findByRole('button', { name: /Marcar atendida/ }))

    expect(window.confirm).toHaveBeenCalled()
    expect(marcarAtendida).not.toHaveBeenCalled()
    expect(screen.getAllByRole('row')).toHaveLength(2) // la notificación sigue en la lista
  })

  it('si el servidor rechaza marcarla (ya estaba atendida) se muestra el error y se recarga la lista', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValueOnce([pendiente(7, '2026-11-20')]).mockResolvedValue([])
    vi.mocked(marcarAtendida).mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 400, data: { mensaje: 'La notificación ya fue atendida' } } as never))
    const { setError, setOk } = montar()
    await userEvent.click(await screen.findByRole('button', { name: /Marcar atendida/ }))

    await waitFor(() => expect(setError).toHaveBeenLastCalledWith('La notificación ya fue atendida'))
    expect(setOk).not.toHaveBeenCalledWith('Notificación marcada como atendida')
    expect(await screen.findByText('Este cliente no tiene notificaciones pendientes.')).toBeTruthy()
  })

  it('un error al listar se muestra', async () => {
    vi.mocked(listarNotificaciones).mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 404, data: { mensaje: 'Cliente no encontrado: 4' } } as never))
    const { setError } = montar()

    await waitFor(() => expect(setError).toHaveBeenCalledWith('Cliente no encontrado: 4'))
  })
})
