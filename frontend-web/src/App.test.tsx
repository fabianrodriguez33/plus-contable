// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, useNavigate } from 'react-router-dom'
import App from './App'

vi.mock('./components/PrivateRoute', () => ({ default: () => <Outlet /> }))
vi.mock('./components/Navbar', () => ({ default: () => <Outlet /> }))
vi.mock('./pages/DashboardPage', () => ({ default: () => <p>panel del estudio</p> }))
vi.mock('./pages/MiEmpresaPage', () => ({ default: () => <p>mi empresa</p> }))
// el login simulado guarda la sesión y navega a «/» sin recargar la página
vi.mock('./pages/LoginPage', () => ({
  default: () => {
    const navigate = useNavigate()
    const entrar = (rol: string) => {
      localStorage.setItem('pc_user', JSON.stringify({ id: 1, username: 'u', nombreCompleto: 'U', roles: [rol] }))
      navigate('/')
    }
    return <><button onClick={() => entrar('CLIENTE_MYPE')}>entrar cliente</button><button onClick={() => entrar('CONTADOR')}>entrar contador</button></>
  },
}))

afterEach(() => { cleanup(); localStorage.clear() })

const abrirLogin = () => render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>)

describe('Redirección de inicio por rol (HU-35)', () => {
  it('tras iniciar sesión sin recargar, el cliente llega a «Mi empresa»', async () => {
    abrirLogin()

    await userEvent.click(screen.getByText('entrar cliente'))

    expect(await screen.findByText('mi empresa')).toBeTruthy()
  })

  it('tras iniciar sesión sin recargar, el contador llega al panel del estudio', async () => {
    abrirLogin()

    await userEvent.click(screen.getByText('entrar contador'))

    expect(await screen.findByText('panel del estudio')).toBeTruthy()
  })
})
