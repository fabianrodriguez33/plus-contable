// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DatosGeneralesTab from './DatosGeneralesTab'
import type { Cliente } from '../../types'

vi.mock('../../services/clienteService', () => ({ consultarRuc: vi.fn() }))

const cliente = (over: Partial<Cliente> = {}): Cliente => ({
  id: 5, ruc: '20100070970', razonSocial: 'Empresa SAC', regimenTributario: 'MYPE', condicionHabido: 'HABIDO', ...over,
})

function ficha(c: Cliente, onCambio = vi.fn()) {
  render(<DatosGeneralesTab cliente={c} esNuevo={false} onCambio={onCambio} onGuardar={vi.fn()} onRevelarSol={vi.fn()} />)
  return onCambio
}

afterEach(cleanup)

describe('DatosGeneralesTab · buen contribuyente (HU-07)', () => {
  it('sin valor guardado aparece sin marcar', () => {
    ficha(cliente())
    expect((screen.getByLabelText(/Buen contribuyente/) as HTMLInputElement).checked).toBe(false)
  })

  it('refleja el valor guardado', () => {
    ficha(cliente({ buenContribuyente: true }))
    expect((screen.getByLabelText(/Buen contribuyente/) as HTMLInputElement).checked).toBe(true)
  })

  it('al marcarla avisa el cambio con true y al desmarcarla con false', async () => {
    const onCambio = ficha(cliente())
    await userEvent.click(screen.getByLabelText(/Buen contribuyente/))
    expect(onCambio).toHaveBeenCalledWith('buenContribuyente', true)

    cleanup()
    const otro = ficha(cliente({ buenContribuyente: true }))
    await userEvent.click(screen.getByLabelText(/Buen contribuyente/))
    expect(otro).toHaveBeenCalledWith('buenContribuyente', false)
  })

  it('avisa que no recalcula lo ya programado', () => {
    ficha(cliente())
    expect(screen.getByText(/No recalcula lo ya programado/)).toBeTruthy()
  })
})

describe('DatosGeneralesTab · régimen y condición (RER, decisión de Beto del 10/11)', () => {
  const opciones = (etiqueta: string) =>
    [...(screen.getByLabelText(etiqueta) as HTMLSelectElement).options].map((o) => [o.value, o.textContent])

  it('el régimen tributario ofrece MYPE, RER y General, y REMIPE ya no aparece', () => {
    ficha(cliente())

    expect(opciones('Régimen tributario')).toEqual([
      ['MYPE', 'MYPE Tributario'],
      ['RER', 'Régimen Especial del Impuesto a la Renta (RER)'],
      ['GENERAL', 'Régimen General'],
    ])
    expect(screen.queryByText(/REMIPE/i)).toBeNull()
    expect(document.body.innerHTML).not.toMatch(/REMIPE/i)
  })

  it('la condición es otro campo con sus propios valores: habido y no habido', () => {
    ficha(cliente())

    expect(opciones('Condición')).toEqual([['HABIDO', 'Habido'], ['NO_HABIDO', 'No habido']])
    expect(screen.getByLabelText('Régimen tributario')).not.toBe(screen.getByLabelText('Condición'))
  })

  it('un cliente en RER y no habido muestra cada dato en su propio campo', () => {
    ficha(cliente({ regimenTributario: 'RER', condicionHabido: 'NO_HABIDO' }))

    expect((screen.getByLabelText('Régimen tributario') as HTMLSelectElement).value).toBe('RER')
    expect((screen.getByLabelText('Condición') as HTMLSelectElement).value).toBe('NO_HABIDO')
  })

  it('elegir el régimen avisa solo el régimen y elegir la condición avisa solo la condición', async () => {
    const onCambio = ficha(cliente())

    await userEvent.selectOptions(screen.getByLabelText('Régimen tributario'), 'RER')
    expect(onCambio).toHaveBeenCalledTimes(1)
    expect(onCambio).toHaveBeenLastCalledWith('regimenTributario', 'RER')

    await userEvent.selectOptions(screen.getByLabelText('Condición'), 'NO_HABIDO')
    expect(onCambio).toHaveBeenCalledTimes(2)
    expect(onCambio).toHaveBeenLastCalledWith('condicionHabido', 'NO_HABIDO')
  })
})
