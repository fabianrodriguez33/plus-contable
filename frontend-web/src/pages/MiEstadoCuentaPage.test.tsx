// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import MiEstadoCuentaPage, { ETIQUETA_ATENCION, ETIQUETA_VENCIDA, VISTA_ATENCION } from './MiEstadoCuentaPage'
import { clienteIdActual } from '../services/authService'
import { listarObligaciones, type Obligacion } from '../services/obligacionService'
import { soles } from '../utils/societario'

vi.mock('../services/authService', () => ({ clienteIdActual: vi.fn() }))
vi.mock('../services/obligacionService', () => ({ listarObligaciones: vi.fn() }))

// «Hoy» en estas pruebas es el 18/11/2026 (Lima).
const obligaciones: Obligacion[] = [
  { id: 1, clienteId: 4, tipoObligacion: 'IGV', periodo: '2026-10-01', fechaVencimiento: '2026-11-13', estado: 'DECLARADO', montoDeterminado: 1500.5 },
  { id: 2, clienteId: 4, tipoObligacion: 'RENTA', periodo: '2026-10-01', fechaVencimiento: '2026-11-13', estado: 'PAGO_PARCIAL', montoDeterminado: 800, montoAcumuladoPagado: 300, saldo: 500 },
  { id: 3, clienteId: 4, tipoObligacion: 'PLAME', periodo: '2026-10-01', fechaVencimiento: '2026-11-12', estado: 'PENDIENTE' },
  { id: 4, clienteId: 4, tipoObligacion: 'AFP', periodo: '2026-10-01', fechaVencimiento: '2026-11-10', estado: 'PAGADO', montoDeterminado: 700 },
  { id: 5, clienteId: 4, tipoObligacion: 'ESSALUD', periodo: '2026-10-01', fechaVencimiento: '2026-11-25', estado: 'DECLARADO', montoDeterminado: 200 },
  { id: 6, clienteId: 4, tipoObligacion: 'IGV', periodo: '2026-11-01', fechaVencimiento: '2026-12-18', estado: 'DECLARADO', montoDeterminado: 900 },
  // un periodo anterior: vencida desde hace más de un mes, que antes quedaba oculta al abrir en el mes actual
  { id: 7, clienteId: 4, tipoObligacion: 'AFP', periodo: '2026-09-01', fechaVencimiento: '2026-10-15', estado: 'DECLARADO', montoDeterminado: 400 },
]
/** Sin nada vencido ni sin declarar atrasado: solo lo que no necesita atención. */
const alDia = obligaciones.filter((o) => [4, 5, 6].includes(o.id))

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-18T17:00:00Z')) // 12:00 en Lima: mes actual 2026-11
  localStorage.clear()
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.useRealTimers() })

async function abrir(lista: Obligacion[] = obligaciones) {
  vi.mocked(clienteIdActual).mockReturnValue(4)
  vi.mocked(listarObligaciones).mockResolvedValue(lista)
  render(<MiEstadoCuentaPage />)
  await screen.findByLabelText('Ver')
}

const filas = (testid: string) => within(screen.getByTestId(testid)).getAllByRole('row').slice(1) as HTMLTableRowElement[]
const vista = () => (screen.getByLabelText('Ver') as HTMLSelectElement).value

describe('Mi estado de cuenta (HU-32)', () => {
  it('al abrir, con vencidas de meses anteriores, muestra todas las vencidas (no solo las del mes actual), la más antigua primero', async () => {
    await abrir()

    expect(vista()).toBe(VISTA_ATENCION)
    expect(filas('vencidas-tabla').map((f) => f.cells[0].textContent + '|' + f.cells[1].textContent)).toEqual([
      'AFP|setiembre 2026',     // de un mes anterior: antes quedaba oculta
      'IGV|octubre 2026',
      'Renta|octubre 2026',
    ])
    expect(screen.queryByText('No tiene obligaciones vencidas.')).toBeNull()
  })

  it('las vencidas llevan la marca, con su monto, pagado, saldo y fecha límite; con pago parcial sigue vencida', async () => {
    await abrir()
    const [afp, igv, renta] = filas('vencidas-tabla')

    expect(afp.textContent).toBe(`AFPsetiembre 2026DeclaradaVencida${soles(400)}${soles(0)}${soles(400)}15/10/2026`)
    expect(igv.textContent).toBe(`IGVoctubre 2026DeclaradaVencida${soles(1500.5)}${soles(0)}${soles(1500.5)}13/11/2026`)
    expect(renta.textContent).toBe(`Rentaoctubre 2026Pago parcialVencida${soles(800)}${soles(300)}${soles(500)}13/11/2026`)
    expect(within(igv).getByText(ETIQUETA_VENCIDA).className).toContain('bg-red-600')
  })

  it('las sin declarar atrasadas van aparte y diferenciadas: «Sin declarar» más «Pendiente de atención», sin marca de vencida', async () => {
    await abrir()
    const [plame] = filas('sin-declarar-tabla')

    expect(plame.textContent).toBe('PLAMEoctubre 2026Sin declararPendiente de atenciónAún sin monto——12/11/2026')
    expect(within(plame).getByText('Sin declarar').className).toContain('bg-slate-100')
    expect(within(plame).getByText(ETIQUETA_ATENCION).className).toContain('border-amber-400')
    expect(within(plame).queryByText(ETIQUETA_VENCIDA)).toBeNull()
    expect(screen.getByRole('region', { name: 'Sin declarar · pendientes de atención' })).toBeTruthy()
    // no se repite en la lista de vencidas
    expect(filas('vencidas-tabla').map((f) => f.cells[0].textContent)).not.toContain('PLAME')
  })

  it('la pagada y las que aún no vencen no aparecen en la vista de atención', async () => {
    await abrir()
    const todas = [...filas('vencidas-tabla'), ...filas('sin-declarar-tabla')].map((f) => f.cells[0].textContent)
    expect(todas).not.toContain('EsSalud')
    expect(todas.filter((t) => t === 'AFP')).toHaveLength(1)      // solo la declarada de setiembre, no la pagada de octubre
  })

  it('en el celular cada una es una tarjeta con mes, estado, pagado y saldo', async () => {
    await abrir()
    const tarjetas = within(screen.getByTestId('vencidas-tarjetas')).getAllByRole('listitem')

    expect(tarjetas).toHaveLength(3)
    expect(tarjetas[2].textContent).toBe(`RentaPago parcialVencidaMesoctubre 2026Fecha límite13/11/2026Monto${soles(800)}Pagado${soles(300)}Saldo${soles(500)}`)
    expect(screen.getByTestId('vencidas-tabla').className).toContain('hidden md:block')
    expect(screen.getByTestId('vencidas-tarjetas').className).toContain('md:hidden')
    const sinDeclarar = within(screen.getByTestId('sin-declarar-tarjetas')).getAllByRole('listitem')
    expect(sinDeclarar[0].textContent).toContain('Sin declarar')
    expect(sinDeclarar[0].textContent).toContain(ETIQUETA_ATENCION)
  })

  it('puede elegir un mes: ve todos sus tributos, con la sin declarar diferenciada también ahí', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Ver'), { target: { value: '2026-10' } })

    expect(filas('mes-tabla').map((f) => f.cells[0].textContent)).toEqual(['AFP', 'PLAME', 'IGV', 'Renta', 'EsSalud'])
    const plame = filas('mes-tabla')[1]
    expect(plame.textContent).toContain('Sin declarar')
    expect(plame.textContent).toContain(ETIQUETA_ATENCION)
    expect(within(plame).queryByText(ETIQUETA_VENCIDA)).toBeNull()
    expect(filas('mes-tabla')[0].textContent).toContain('Pagada')
    expect(within(filas('mes-tabla')[0]).queryByText(ETIQUETA_VENCIDA)).toBeNull()
  })

  it('y puede volver a la vista de vencidas de todos los meses', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Ver'), { target: { value: '2026-11' } })
    expect(vista()).toBe('2026-11')
    fireEvent.change(screen.getByLabelText('Ver'), { target: { value: VISTA_ATENCION } })
    expect(filas('vencidas-tabla')).toHaveLength(3)
  })

  it('sin nada vencido ni por declarar, abre en el mes actual (hoy en Lima) y no ofrece la vista de atención', async () => {
    await abrir(alDia)

    expect(vista()).toBe('2026-11')
    expect(within(screen.getByLabelText('Ver')).queryByText('Vencidas y por declarar (todos los meses)')).toBeNull()
    expect(filas('mes-tabla').map((f) => f.cells[0].textContent)).toEqual(['IGV'])
  })

  it('con solo sin declarar atrasadas también abre en la vista de atención y lo dice en vencidas', async () => {
    await abrir([obligaciones[2], obligaciones[5]])
    expect(vista()).toBe(VISTA_ATENCION)
    expect(screen.getByText('No tiene obligaciones vencidas.')).toBeTruthy()
    expect(filas('sin-declarar-tabla')).toHaveLength(1)
  })

  it('un mes sin tributos lo dice y no deja una tabla vacía', async () => {
    await abrir(alDia.filter((o) => o.periodo !== '2026-11-01'))
    expect(screen.getByText('No hay tributos registrados para noviembre 2026.')).toBeTruthy()
    expect(screen.queryByTestId('mes-tabla')).toBeNull()
    expect(screen.queryByTestId('mes-tarjetas')).toBeNull()
  })

  it('T199: pide solo la empresa de su propio token', async () => {
    await abrir()
    expect(listarObligaciones).toHaveBeenCalledTimes(1)
    expect(listarObligaciones).toHaveBeenCalledWith(4)
  })

  it('es solo lectura: el único control es el selector de vista', async () => {
    await abrir()
    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(screen.getAllByRole('combobox')).toHaveLength(1)
  })

  it('una cuenta sin empresa no pide datos y lo dice', () => {
    vi.mocked(clienteIdActual).mockReturnValue(null)
    render(<MiEstadoCuentaPage />)
    expect(screen.getByRole('alert').textContent).toMatch(/no está vinculada a una empresa/)
    expect(listarObligaciones).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza la petición muestra el mensaje', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(listarObligaciones).mockRejectedValue(new Error('x'))
    render(<MiEstadoCuentaPage />)
    expect((await screen.findByRole('alert')).textContent).toBe('Error inesperado')
  })
})
