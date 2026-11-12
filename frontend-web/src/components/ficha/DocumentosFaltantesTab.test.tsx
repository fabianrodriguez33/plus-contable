// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosResponse } from 'axios'
import DocumentosFaltantesTab from './DocumentosFaltantesTab'
import {
  listarDocumentosFaltantes, registrarDocumentoComoIncidencia, registrarDocumentoFaltante, registrarEntregaDocumento,
  type DocumentoFaltante,
} from '../../services/documentoFaltanteService'

vi.mock('../../services/documentoFaltanteService', () => ({
  listarDocumentosFaltantes: vi.fn(), registrarDocumentoFaltante: vi.fn(), registrarEntregaDocumento: vi.fn(),
  registrarDocumentoComoIncidencia: vi.fn(),
}))

const doc = (id: number, documento: string, estado: DocumentoFaltante['estado'], extra: Partial<DocumentoFaltante> = {}): DocumentoFaltante => ({
  id, clienteId: 4, documento, fechaSolicitud: '2026-11-01', fechaLimite: '2026-11-20', estado, fechaEntrega: null, incidenciaId: null, ...extra,
})

function montar() {
  const props = { setError: vi.fn(), setOk: vi.fn() }
  render(<DocumentosFaltantesTab clienteId={4} {...props} />)
  return props
}

beforeEach(() => { vi.mocked(listarDocumentosFaltantes).mockResolvedValue([]) })
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Documentos faltantes en la ficha (HU-24)', () => {
  it('criterios 1 y 2: registra el documento con su fecha límite y la fecha en que se pidió (hoy por defecto)', async () => {
    vi.mocked(registrarDocumentoFaltante).mockResolvedValue(doc(1, 'Libro de compras', 'PENDIENTE'))
    const { setOk } = montar()
    await screen.findByText('El cliente no tiene documentos pendientes de entregar.')

    const solicitado = screen.getByLabelText('Solicitado el') as HTMLInputElement
    expect(solicitado.value).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    await userEvent.type(screen.getByLabelText('Documento que falta'), '  Libro de compras ')
    await userEvent.type(screen.getByLabelText('Fecha límite'), '2099-12-01')
    await userEvent.click(screen.getByRole('button', { name: /Registrar documento faltante/ }))

    expect(registrarDocumentoFaltante).toHaveBeenCalledWith(4, { documento: 'Libro de compras', fechaLimite: '2099-12-01', fechaSolicitud: solicitado.value })
    await waitFor(() => expect(setOk).toHaveBeenCalledWith('Documento registrado como pendiente hasta el 01/12/2099'))
  })

  it('sin documento o sin fecha límite avisa y no consulta al servidor', async () => {
    const { setError } = montar()
    await screen.findByText('El cliente no tiene documentos pendientes de entregar.')

    await userEvent.click(screen.getByRole('button', { name: /Registrar documento faltante/ }))
    expect(setError).toHaveBeenCalledWith('Indique qué documento falta')
    await userEvent.type(screen.getByLabelText('Documento que falta'), 'Planilla')
    await userEvent.click(screen.getByRole('button', { name: /Registrar documento faltante/ }))
    expect(setError).toHaveBeenCalledWith('Indique la fecha límite de entrega')
    expect(registrarDocumentoFaltante).not.toHaveBeenCalled()
  })

  it('criterio 5: muestra qué documentos faltan con su estado y cuántos son', async () => {
    vi.mocked(listarDocumentosFaltantes).mockResolvedValue([
      doc(1, 'Planilla de octubre', 'SIN_RESPUESTA', { fechaLimite: '2026-11-05' }),
      doc(2, 'Libro de compras', 'PENDIENTE'),
      doc(3, 'Constancia de pago', 'ENTREGADO', { fechaEntrega: '2026-11-03' }),
    ])
    montar()

    const lista = await screen.findByLabelText('Documentos del cliente')
    expect(await within(lista).findByText('Faltan 2 documentos')).toBeTruthy()
    expect(within(lista).getByText('Sin respuesta')).toBeTruthy()
    expect(within(lista).getByText('Pendiente')).toBeTruthy()
    expect(within(lista).getByText('Entregado')).toBeTruthy()
    expect(within(lista).getByText('el 03/11/2026')).toBeTruthy()
    // registrar entrega en los que faltan; como incidencia solo el sin respuesta
    expect(within(lista).getAllByRole('button', { name: /Registrar entrega/ })).toHaveLength(2)
    expect(within(lista).getAllByRole('button', { name: /Registrar como incidencia/ })).toHaveLength(1)
  })

  it('criterio 3: registrar la entrega llama al servidor y recarga la lista', async () => {
    vi.mocked(listarDocumentosFaltantes)
      .mockResolvedValueOnce([doc(2, 'Libro de compras', 'PENDIENTE')])
      .mockResolvedValueOnce([doc(2, 'Libro de compras', 'ENTREGADO', { fechaEntrega: '2026-11-11' })])
    vi.mocked(registrarEntregaDocumento).mockResolvedValue(doc(2, 'Libro de compras', 'ENTREGADO', { fechaEntrega: '2026-11-11' }))
    const { setOk } = montar()

    await userEvent.click(await screen.findByRole('button', { name: /Registrar entrega/ }))

    expect(registrarEntregaDocumento).toHaveBeenCalledWith(4, 2)
    await waitFor(() => expect(setOk).toHaveBeenCalledWith('Entrega de «Libro de compras» registrada'))
    expect(await screen.findByText('Entregado')).toBeTruthy()
    expect(screen.getByText('No falta ningún documento')).toBeTruthy()
  })

  it('criterio 4: un sin respuesta se registra como incidencia y después ya no se ofrece', async () => {
    vi.mocked(listarDocumentosFaltantes)
      .mockResolvedValueOnce([doc(1, 'Planilla', 'SIN_RESPUESTA')])
      .mockResolvedValueOnce([doc(1, 'Planilla', 'SIN_RESPUESTA', { incidenciaId: 40 })])
    vi.mocked(registrarDocumentoComoIncidencia).mockResolvedValue(doc(1, 'Planilla', 'SIN_RESPUESTA', { incidenciaId: 40 }))
    const { setOk } = montar()

    await userEvent.click(await screen.findByRole('button', { name: /Registrar como incidencia/ }))

    expect(registrarDocumentoComoIncidencia).toHaveBeenCalledWith(4, 1)
    await waitFor(() => expect(setOk).toHaveBeenCalledWith('«Planilla» quedó registrado como incidencia del cliente'))
    expect(await screen.findByText('Incidencia registrada')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Registrar como incidencia/ })).toBeNull()
  })

  it('un rechazo del servidor se muestra como error de la ficha', async () => {
    vi.mocked(registrarDocumentoFaltante).mockRejectedValue(new AxiosError('400', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 400, data: { mensaje: 'Faltan o son inválidos: fechaSolicitud (no puede ser futura)' } } as AxiosResponse))
    const { setError } = montar()
    await screen.findByText('El cliente no tiene documentos pendientes de entregar.')

    await userEvent.type(screen.getByLabelText('Documento que falta'), 'Planilla')
    await userEvent.type(screen.getByLabelText('Fecha límite'), '2099-12-01')
    await userEvent.click(screen.getByRole('button', { name: /Registrar documento faltante/ }))

    await waitFor(() => expect(setError).toHaveBeenCalledWith('Faltan o son inválidos: fechaSolicitud (no puede ser futura)'))
  })
})
