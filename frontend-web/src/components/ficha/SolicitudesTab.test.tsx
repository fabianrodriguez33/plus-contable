// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosResponse } from 'axios'
import SolicitudesTab from './SolicitudesTab'
import {
  listarSolicitudesPendientes, marcarSolicitudAtendida, registrarSolicitud, type SolicitudEspecial,
} from '../../services/solicitudService'

vi.mock('../../services/solicitudService', () => ({
  listarSolicitudesPendientes: vi.fn(), registrarSolicitud: vi.fn(), marcarSolicitudAtendida: vi.fn(),
}))

const pendiente = (id: number, tipo: SolicitudEspecial['tipo'], fechaEntrega: string, descripcion: string | null = null): SolicitudEspecial =>
  ({ id, clienteId: 4, tipo, descripcion, fechaEntrega, estado: 'PENDIENTE', fechaAtencion: null })

function montar() {
  const props = { setError: vi.fn(), setOk: vi.fn() }
  render(<SolicitudesTab clienteId={4} {...props} />)
  return props
}

beforeEach(() => { vi.mocked(listarSolicitudesPendientes).mockResolvedValue([]) })
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Solicitudes especiales en la ficha (HU-26)', () => {
  it('criterio 1: registra el pedido con su tipo, fecha de entrega y descripción y recarga los pendientes', async () => {
    vi.mocked(registrarSolicitud).mockResolvedValue(pendiente(1, 'CERTIFICACION', '2099-12-01'))
    const { setOk } = montar()
    await screen.findByText('El cliente no tiene pedidos pendientes.')

    await userEvent.selectOptions(screen.getByLabelText('Tipo de pedido'), 'CERTIFICACION')
    await userEvent.type(screen.getByLabelText('Fecha de entrega'), '2099-12-01')
    await userEvent.type(screen.getByLabelText('Descripción (opcional)'), '  Certificado de retenciones ')
    await userEvent.click(screen.getByRole('button', { name: /Registrar pedido/ }))

    expect(registrarSolicitud).toHaveBeenCalledWith(4, { tipo: 'CERTIFICACION', fechaEntrega: '2099-12-01', descripcion: 'Certificado de retenciones' })
    await waitFor(() => expect(setOk).toHaveBeenCalledWith('Certificación registrada: queda en seguimiento hasta el 01/12/2099'))
    expect(listarSolicitudesPendientes).toHaveBeenCalledTimes(2)
  })

  it('criterio 2: sin fecha de entrega no se envía y lo dice', async () => {
    const { setError } = montar()
    await screen.findByText('El cliente no tiene pedidos pendientes.')

    await userEvent.click(screen.getByRole('button', { name: /Registrar pedido/ }))

    expect(setError).toHaveBeenCalledWith('Indique la fecha de entrega: sin ella el pedido no se guarda')
    expect(registrarSolicitud).not.toHaveBeenCalled()
  })

  it('sin descripción no la envía', async () => {
    vi.mocked(registrarSolicitud).mockResolvedValue(pendiente(1, 'CONSTANCIA', '2099-12-01'))
    montar()
    await screen.findByText('El cliente no tiene pedidos pendientes.')

    await userEvent.type(screen.getByLabelText('Fecha de entrega'), '2099-12-01')
    await userEvent.click(screen.getByRole('button', { name: /Registrar pedido/ }))

    expect(registrarSolicitud).toHaveBeenCalledWith(4, { tipo: 'CONSTANCIA', fechaEntrega: '2099-12-01' })
  })

  it('criterio 3: los pendientes se ven en seguimiento y al marcar uno atendido se recarga la lista', async () => {
    vi.mocked(listarSolicitudesPendientes)
      .mockResolvedValueOnce([pendiente(1, 'CONSTANCIA', '2026-11-20', 'No adeudo'), pendiente(2, 'TRAMITE', '2026-11-30')])
      .mockResolvedValueOnce([pendiente(2, 'TRAMITE', '2026-11-30')])
    vi.mocked(marcarSolicitudAtendida).mockResolvedValue({ ...pendiente(1, 'CONSTANCIA', '2026-11-20'), estado: 'ATENDIDA', fechaAtencion: '2026-11-12' })
    const { setOk } = montar()

    const lista = await screen.findByLabelText('Pedidos en seguimiento')
    expect(await within(lista).findByText('20/11/2026')).toBeTruthy()
    expect(within(lista).getByText('No adeudo')).toBeTruthy()
    expect(within(lista).getByText('Trámite')).toBeTruthy()

    await userEvent.click(within(lista).getAllByRole('button', { name: /Marcar atendida/ })[0])

    expect(marcarSolicitudAtendida).toHaveBeenCalledWith(4, 1)
    await waitFor(() => expect(setOk).toHaveBeenCalledWith('Constancia marcada como atendida'))
    await waitFor(() => expect(within(lista).queryByText('20/11/2026')).toBeNull())
  })

  it('un rechazo del servidor se muestra como error de la ficha', async () => {
    vi.mocked(registrarSolicitud).mockRejectedValue(new AxiosError('400', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 400, data: { mensaje: 'Faltan o son inválidos: fechaEntrega (no puede ser anterior a hoy)' } } as AxiosResponse))
    const { setError } = montar()
    await screen.findByText('El cliente no tiene pedidos pendientes.')

    await userEvent.type(screen.getByLabelText('Fecha de entrega'), '2099-12-01')
    await userEvent.click(screen.getByRole('button', { name: /Registrar pedido/ }))

    await waitFor(() => expect(setError).toHaveBeenCalledWith('Faltan o son inválidos: fechaEntrega (no puede ser anterior a hoy)'))
  })
})
