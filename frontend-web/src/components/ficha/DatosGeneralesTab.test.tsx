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
