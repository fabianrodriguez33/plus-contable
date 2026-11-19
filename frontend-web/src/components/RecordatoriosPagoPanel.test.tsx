// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import RecordatoriosPagoPanel from './RecordatoriosPagoPanel'
import { cancelarRecordatorio, listarRecordatorios, programarRecordatorio, type RecordatorioPago } from '../services/recordatorioPagoService'
import { soles } from '../utils/societario'

vi.mock('../services/recordatorioPagoService', () => ({
  listarRecordatorios: vi.fn(), programarRecordatorio: vi.fn(), cancelarRecordatorio: vi.fn(),
}))

const recordatorio = (id: number, extra: Partial<RecordatorioPago> = {}): RecordatorioPago => ({
  id, obligacionId: 9, fechaRecordatorio: '2026-11-20', estado: 'PROGRAMADO', programadoPor: 'contador1',
  programadoEn: '2026-11-18T12:00:00Z', resueltoEn: null, saldo: 600, ...extra,
})

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-18T17:00:00Z')) // 12:00 en Lima
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.useRealTimers() })

const abrir = async (lista: RecordatorioPago[] = []) => {
  vi.mocked(listarRecordatorios).mockResolvedValue(lista)
  render(<RecordatoriosPagoPanel obligacionId={9} fechaVencimiento="2026-11-30" />)
  await screen.findByLabelText('Fecha del recordatorio')
}

describe('Recordatorios de pago en la obligación (HU-15 · T35)', () => {
  it('el contador programa una fecha y se recarga la lista; avisa que el cliente recibirá el aviso ese día', async () => {
    vi.mocked(programarRecordatorio).mockResolvedValue(recordatorio(1))
    await abrir()
    fireEvent.change(screen.getByLabelText('Fecha del recordatorio'), { target: { value: '2026-11-20' } })
    fireEvent.click(screen.getByRole('button', { name: 'Programar recordatorio' }))

    await waitFor(() => expect(programarRecordatorio).toHaveBeenCalledWith(9, '2026-11-20'))
    expect((await screen.findByRole('status')).textContent).toContain('20/11/2026')
    expect(listarRecordatorios).toHaveBeenCalledTimes(2)
  })

  it('el selector no deja elegir antes de hoy (Lima) ni después del vencimiento', async () => {
    await abrir()
    const campo = screen.getByLabelText('Fecha del recordatorio') as HTMLInputElement
    expect(campo.min).toBe('2026-11-18')
    expect(campo.max).toBe('2026-11-30')
  })

  it('sin fecha, con fecha pasada o posterior al vencimiento no envía y lo dice', async () => {
    await abrir()
    const enviar = () => fireEvent.click(screen.getByRole('button', { name: 'Programar recordatorio' }))
    enviar()
    expect(screen.getByText('Elija la fecha del recordatorio')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Fecha del recordatorio'), { target: { value: '2026-11-10' } })
    enviar()
    expect(screen.getByText('La fecha no puede ser pasada')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Fecha del recordatorio'), { target: { value: '2026-12-05' } })
    enviar()
    expect(screen.getByText(/posterior al vencimiento/)).toBeTruthy()
    expect(programarRecordatorio).not.toHaveBeenCalled()
  })

  it('muestra cada recordatorio con su estado; el programado trae el saldo de hoy y se puede cancelar, los demás no', async () => {
    await abrir([
      recordatorio(1),
      recordatorio(2, { fechaRecordatorio: '2026-11-15', estado: 'ENVIADO', resueltoEn: '2026-11-15T12:00:00Z' }),
      recordatorio(3, { fechaRecordatorio: '2026-11-16', estado: 'OMITIDO', resueltoEn: '2026-11-16T12:00:00Z' }),
    ])
    const items = within(screen.getByRole('list', { name: 'Recordatorios programados' })).getAllByRole('listitem')
    expect(items[0].textContent).toContain('Programado')
    expect(items[0].textContent).toContain(`saldo hoy ${soles(600)}`)
    expect(within(items[0]).getByRole('button', { name: 'Cancelar' })).toBeTruthy()
    expect(items[1].textContent).toContain('Enviado al cliente')
    expect(items[2].textContent).toContain('Omitido')
    expect(within(items[1]).queryByRole('button', { name: 'Cancelar' })).toBeNull()
    expect(within(items[2]).queryByRole('button', { name: 'Cancelar' })).toBeNull()
  })

  it('cancela un recordatorio programado', async () => {
    vi.mocked(cancelarRecordatorio).mockResolvedValue(recordatorio(1, { estado: 'CANCELADO' }))
    await abrir([recordatorio(1)])
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(cancelarRecordatorio).toHaveBeenCalledWith(9, 1))
    expect((await screen.findByRole('status')).textContent).toContain('cancelado')
  })

  it('si el servidor rechaza al programar muestra su mensaje', async () => {
    vi.mocked(programarRecordatorio).mockRejectedValue(new Error('x'))
    await abrir()
    fireEvent.change(screen.getByLabelText('Fecha del recordatorio'), { target: { value: '2026-11-20' } })
    fireEvent.click(screen.getByRole('button', { name: 'Programar recordatorio' }))
    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
  })

  it('sin recordatorios lo dice', async () => {
    await abrir([])
    expect(screen.getByText('Todavía no hay recordatorios programados.')).toBeTruthy()
  })
})
