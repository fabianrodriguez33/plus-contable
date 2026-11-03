// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import FichaMaestraPage from './FichaMaestraPage'
import { obtenerCliente } from '../services/clienteService'

vi.mock('../services/clienteService', () => ({
  obtenerCliente: vi.fn(), crearCliente: vi.fn(), actualizarCliente: vi.fn(), consultarRuc: vi.fn(),
}))

/**
 * HU-01 · «Diseño responsivo». jsdom no calcula el diseño, así que estas pruebas protegen las clases que lo
 * hacen responsivo en la ficha (el desbordamiento real se mide en el navegador: docs/verificacion-responsiva-ficha.md).
 */
function ficha(ruta = '/clientes/4') {
  return render(
    <MemoryRouter initialEntries={[ruta]}>
      <Routes>
        <Route path="/clientes/nuevo" element={<FichaMaestraPage />} />
        <Route path="/clientes/:clienteId" element={<FichaMaestraPage />} />
      </Routes>
    </MemoryRouter>)
}

beforeEach(() => {
  vi.mocked(obtenerCliente).mockResolvedValue({
    id: 4, ruc: '20100070970', razonSocial: 'Empresa con una razón social bastante larga SAC',
    regimenTributario: 'MYPE', condicionHabido: 'HABIDO',
  })
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Ficha maestra · diseño responsivo (HU-01)', () => {
  it('el formulario va en una columna y pasa a dos desde el ancho mediano', async () => {
    ficha()
    await screen.findByDisplayValue('20100070970')

    const rejilla = document.querySelector('form .grid') as HTMLElement
    expect(rejilla.className).toMatch(/\bgrid\b/)
    expect(rejilla.className).toContain('md:grid-cols-2')
    // sin columnas fijas en el ancho base: en móvil la rejilla es de una sola columna
    expect(rejilla.className.split(/\s+/).filter((c) => /^grid-cols-\d/.test(c))).toEqual([])
  })

  it('la dirección fiscal y la casilla de buen contribuyente ocupan todo el ancho en dos columnas', async () => {
    ficha()
    await screen.findByDisplayValue('20100070970')

    for (const campo of [screen.getByLabelText('Dirección fiscal'), screen.getByLabelText(/Buen contribuyente/)]) {
      expect(campo.closest('.md\\:col-span-2')).not.toBeNull()
    }
  })

  it('los campos se ajustan al ancho de su columna y no llevan anchos fijos', async () => {
    ficha()
    await screen.findByDisplayValue('20100070970')

    const form = document.querySelector('form') as HTMLElement
    for (const campo of form.querySelectorAll<HTMLElement>('input[type=text], input:not([type]), select')) {
      expect(campo.className).toContain('w-full')
    }
    expect(form.querySelectorAll('[style*="width"]')).toHaveLength(0)
  })

  it('las pestañas se desplazan dentro de su barra en vez de ensanchar la página', async () => {
    ficha()
    await screen.findByDisplayValue('20100070970')

    const barra = screen.getByRole('tablist')
    expect(barra.className).toContain('overflow-x-auto')
    for (const pestaña of screen.getAllByRole('tab')) expect(pestaña.className).toContain('shrink-0')
  })

  it('el título largo y los botones del formulario no obligan a un ancho mínimo', async () => {
    ficha()
    await screen.findByDisplayValue('20100070970')

    const botones = screen.getByRole('button', { name: /Guardar cambios/ }).parentElement as HTMLElement
    expect(botones.className).toContain('flex-wrap')
  })

  it('el alta de un cliente nuevo usa el mismo formulario responsivo', async () => {
    ficha('/clientes/nuevo')
    await screen.findByLabelText('Razón social')

    const rejilla = document.querySelector('form .grid') as HTMLElement
    expect(rejilla.className).toContain('md:grid-cols-2')
  })
})
