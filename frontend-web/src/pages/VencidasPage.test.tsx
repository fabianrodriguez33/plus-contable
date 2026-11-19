// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import VencidasPage, { ETIQUETA_CON_SALDO, ETIQUETA_SIN_PAGO } from './VencidasPage'
import { listarVencidas, type ObligacionVencida } from '../services/vencidasService'
import { soles } from '../utils/societario'

vi.mock('../services/vencidasService', () => ({ listarVencidas: vi.fn() }))

const o = (id: number, extra: Partial<ObligacionVencida> = {}): ObligacionVencida => ({
  obligacionId: id, clienteId: 4, ruc: '20123456786', razonSocial: 'Acme SAC', tipoObligacion: 'IGV', periodo: '2026-10-01',
  fechaVencimiento: '2026-11-13', diasVencida: 5, estado: 'DECLARADO', montoDeterminado: 1500.5, montoPagado: 0, saldo: 1500.5, ...extra,
})

afterEach(() => { cleanup(); vi.clearAllMocks() })
const abrir = () => render(<MemoryRouter><VencidasPage /></MemoryRouter>)
const filasDe = (nombre: string) => within(screen.getByRole('region', { name: nombre })).getAllByRole('row').slice(1)

describe('Obligaciones vencidas (HU-16 · T43, definición de Beto)', () => {
  it('la vencida sin pago sale en rojo con monto, pagado cero y saldo completo', async () => {
    vi.mocked(listarVencidas).mockResolvedValue({ vencidas: [o(1)], sinDeclarar: [] })
    abrir()

    await screen.findByText('Acme SAC')
    const [fila] = filasDe('Vencidas')
    expect(fila.textContent).toBe(`Acme SAC20123456786IGV2026-1013/11/20265 días${soles(1500.5)}${soles(0)}${soles(1500.5)}${ETIQUETA_SIN_PAGO}`)
    expect(within(fila).getByText(ETIQUETA_SIN_PAGO).className).toContain('bg-red-600')
    expect(fila.className).toContain('bg-red-50')
  })

  it('con pago parcial sigue vencida y muestra lo pagado y el saldo', async () => {
    vi.mocked(listarVencidas).mockResolvedValue({
      vencidas: [o(2, { estado: 'PAGO_PARCIAL', montoPagado: 400, saldo: 1100.5, diasVencida: 1 })], sinDeclarar: [] })
    abrir()

    await screen.findByText('Acme SAC')
    const [fila] = filasDe('Vencidas')
    expect(fila.textContent).toContain('1 día')
    expect(fila.textContent).toContain(soles(400))
    expect(fila.textContent).toContain(soles(1100.5))
    expect(within(fila).getByText(ETIQUETA_CON_SALDO).className).toContain('bg-red-600')
    expect(screen.getByText('1 obligación vencida')).toBeTruthy()
  })

  it('las sin declarar con fecha pasada van aparte, en ámbar, y no cuentan como vencidas ni llevan marca roja', async () => {
    vi.mocked(listarVencidas).mockResolvedValue({
      vencidas: [], sinDeclarar: [o(3, { estado: 'PENDIENTE', montoDeterminado: null, saldo: null, razonSocial: 'Beta SAC', diasVencida: 7 })] })
    abrir()

    await screen.findByText('Beta SAC')
    expect(screen.getByText('No hay obligaciones vencidas con saldo pendiente.')).toBeTruthy()
    const [fila] = filasDe('Sin declarar · pendientes de atención')
    expect(fila.textContent).toBe('Beta SAC20123456786IGV2026-1013/11/20267 días')
    expect(fila.className).toContain('bg-amber-50')
    expect(screen.queryByText(ETIQUETA_SIN_PAGO)).toBeNull()
  })

  it('una vencida sin monto determinado dice «Aún sin monto» en vez de cero', async () => {
    vi.mocked(listarVencidas).mockResolvedValue({ vencidas: [o(4, { montoDeterminado: null, saldo: null })], sinDeclarar: [] })
    abrir()
    await screen.findByText('Acme SAC')
    expect(filasDe('Vencidas')[0].textContent).toContain('Aún sin monto')
  })

  it('no recalcula nada: muestra exactamente lo que devuelve el servidor', async () => {
    vi.mocked(listarVencidas).mockResolvedValue({ vencidas: [o(1)], sinDeclarar: [] })
    abrir()
    await screen.findByText('Acme SAC')
    expect(filasDe('Vencidas')).toHaveLength(1)
    expect(listarVencidas).toHaveBeenCalledWith()
  })

  it('sin nada lo dice en las dos listas y no pinta marcas rojas', async () => {
    vi.mocked(listarVencidas).mockResolvedValue({ vencidas: [], sinDeclarar: [] })
    abrir()
    await screen.findByText('No hay obligaciones vencidas con saldo pendiente.')
    expect(screen.getByText('No hay obligaciones sin declarar con la fecha pasada.')).toBeTruthy()
    expect(screen.queryByText(ETIQUETA_SIN_PAGO)).toBeNull()
  })

  it('si el servidor la rechaza (por ejemplo 403) muestra el mensaje y no una lista', async () => {
    vi.mocked(listarVencidas).mockRejectedValue(new Error('x'))
    abrir()
    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
    expect(screen.getByText('No se pudo cargar la lista.')).toBeTruthy()
  })
})
