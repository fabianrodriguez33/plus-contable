// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import Aviso, { type TipoAviso } from './Aviso'

afterEach(cleanup)

describe('Aviso', () => {
  it('el peligro se anuncia como alerta', () => {
    render(<Aviso tipo="peligro">Credenciales inválidas</Aviso>)

    expect(screen.getByRole('alert').textContent).toContain('Credenciales inválidas')
  })

  it.each<TipoAviso>(['exito', 'advertencia', 'info'])('%s se anuncia como estado, no como alerta', (tipo) => {
    render(<Aviso tipo={tipo}>Mensaje</Aviso>)

    expect(screen.getByRole('status').textContent).toBe('Mensaje')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('el ícono es decorativo: el lector de pantalla lee solo el texto', () => {
    render(<Aviso tipo="exito">Guardado</Aviso>)

    const icono = screen.getByRole('status').querySelector('svg') as SVGElement
    expect(icono.getAttribute('aria-hidden')).toBe('true')
  })

  it('admite clases adicionales de quien lo usa', () => {
    render(<Aviso tipo="info" className="mb-4">Dato</Aviso>)

    expect(screen.getByRole('status').className).toContain('mb-4')
  })
})
