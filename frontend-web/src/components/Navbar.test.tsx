// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import Navbar from './Navbar'

/**
 * HU-01 · «Diseño responsivo»: la barra lateral solo existe desde el ancho mediano; en móvil hay un menú
 * desplegable. jsdom no calcula el diseño: se protegen las clases y el comportamiento del menú.
 */
function layout() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes><Route element={<Navbar />}><Route path="/" element={<p>contenido</p>} /></Route></Routes>
    </MemoryRouter>)
}

beforeEach(() => {
  localStorage.setItem('pc_user', JSON.stringify({ id: 1, username: 'a', nombreCompleto: 'Ana', roles: ['ADMIN'] }))
})
afterEach(() => { cleanup(); localStorage.clear() })

describe('Navbar · diseño responsivo (HU-01)', () => {
  it('la barra lateral fija se oculta en móvil y aparece desde el ancho mediano', () => {
    layout()

    const lateral = document.querySelector('aside') as HTMLElement
    expect(lateral.className).toContain('hidden')
    expect(lateral.className).toContain('md:flex')
  })

  it('el botón de menú solo se ve en móvil', () => {
    layout()

    expect(screen.getByRole('button', { name: 'Abrir menú' }).className).toContain('md:hidden')
  })

  it('el contenido puede encogerse y se centra con un ancho máximo', () => {
    layout()

    const principal = screen.getByRole('main')
    expect(principal.className).toContain('w-full')
    expect(principal.className).toContain('max-w-6xl')
    expect((principal.parentElement as HTMLElement).className).toContain('min-w-0')
  })

  it('el menú móvil se abre, navega y se cierra con el botón y con Escape', async () => {
    layout()
    expect(screen.queryByRole('dialog')).toBeNull()

    await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }))
    const menu = screen.getByRole('dialog', { name: 'Menú de navegación' })
    expect(menu).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Abrir menú' }).getAttribute('aria-expanded')).toBe('true')

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar menú' }))
    expect(screen.queryByRole('dialog')).toBeNull()

    await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('el nombre del usuario se oculta en pantallas pequeñas para que quepa la barra superior', () => {
    layout()

    expect((screen.getByText(/Ana/).closest('span') as HTMLElement).className).toContain('hidden')
  })
})
