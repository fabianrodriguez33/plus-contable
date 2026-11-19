// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import VencidasSinPagoPage, { ETIQUETA_VENCIDA } from './VencidasSinPagoPage'
import { listarVencidasSinPago, type ObligacionVencidaSinPago } from '../services/vencidasSinPagoService'
import { soles } from '../utils/societario'

vi.mock('../services/vencidasSinPagoService', () => ({ listarVencidasSinPago: vi.fn() }))

const o = (id: number, extra: Partial<ObligacionVencidaSinPago> = {}): ObligacionVencidaSinPago => ({
  obligacionId: id, clienteId: 4, ruc: '20123456786', razonSocial: 'Acme SAC', tipoObligacion: 'IGV', periodo: '2026-10-01',
  fechaVencimiento: '2026-11-13', diasVencida: 5, estado: 'DECLARADO', montoDeterminado: 1500.5, ...extra,
})

afterEach(() => { cleanup(); vi.clearAllMocks() })
const abrir = () => render(<MemoryRouter><VencidasSinPagoPage /></MemoryRouter>)
const filas = () => screen.getAllByRole('row').slice(1)

describe('Vencidas sin pago (HU-16 · T43)', () => {
  it('cada obligación aparece en rojo como VENCIDA SIN PAGO, con su fecha límite, atraso y monto', async () => {
    vi.mocked(listarVencidasSinPago).mockResolvedValue([o(1), o(2, { tipoObligacion: 'RENTA', diasVencida: 1, montoDeterminado: null, razonSocial: 'Beta SAC' })])
    abrir()

    await screen.findByText('Acme SAC')
    const [primera, segunda] = filas()
    expect(primera.textContent).toBe(`Acme SAC20123456786IGV2026-1013/11/20265 días${soles(1500.5)}${ETIQUETA_VENCIDA}`)
    expect(segunda.textContent).toContain('1 día')
    expect(segunda.textContent).toContain('Aún sin monto')
    const marca = within(primera).getByText(ETIQUETA_VENCIDA)
    expect(marca.className).toContain('bg-red-600')
    expect(primera.className).toContain('bg-red-50')
    expect(screen.getByText('2 obligaciones vencidas sin pago')).toBeTruthy()
  })

  it('no recalcula nada: muestra exactamente lo que devuelve el servidor', async () => {
    vi.mocked(listarVencidasSinPago).mockResolvedValue([o(1)])
    abrir()
    await screen.findByText('Acme SAC')
    expect(filas()).toHaveLength(1)
    expect(listarVencidasSinPago).toHaveBeenCalledWith()
  })

  it('sin obligaciones lo dice y no pinta ninguna marca roja', async () => {
    vi.mocked(listarVencidasSinPago).mockResolvedValue([])
    abrir()
    await screen.findByText('No hay obligaciones vencidas sin constancia de pago.')
    expect(screen.queryByText(ETIQUETA_VENCIDA)).toBeNull()
  })

  it('si el servidor la rechaza (por ejemplo 403) muestra el mensaje y no una lista', async () => {
    vi.mocked(listarVencidasSinPago).mockRejectedValue(new Error('x'))
    abrir()
    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
    expect(screen.getByText('No se pudo cargar la lista.')).toBeTruthy()
  })
})
