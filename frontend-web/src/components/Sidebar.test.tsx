// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Sidebar from './Sidebar'

const menuDe = (rol: string) => {
  localStorage.setItem('pc_user', JSON.stringify({ id: 1, username: 'u', nombreCompleto: 'U', roles: [rol] }))
  render(<MemoryRouter><Sidebar /></MemoryRouter>)
  return screen.getAllByRole('link').map(a => a.textContent?.trim())
}

afterEach(() => { cleanup(); localStorage.clear() })

describe('Menú lateral por rol (HU-35)', () => {
  it('la cuenta de un cliente solo tiene «Mi empresa», «Mi estado de cuenta» y «Avisos de pago»', () => {
    expect(menuDe('CLIENTE_MYPE')).toEqual(['Mi empresa', 'Mi estado de cuenta', 'Avisos de pago'])
  })

  it('el estudio ve su menú completo y no «Mi empresa»', () => {
    const enlaces = menuDe('CONTADOR')

    expect(enlaces).toContain('Clientes')
    expect(enlaces).toContain('Calendario')
    expect(enlaces).toContain('Obligaciones vencidas')
    expect(enlaces).not.toContain('Mi empresa')
  })

  it('la cuenta de un cliente no ve «Obligaciones vencidas» (HU-16 es del estudio)', () => {
    expect(menuDe('CLIENTE_MYPE')).not.toContain('Obligaciones vencidas')
  })
})
