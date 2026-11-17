// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AxiosError, type AxiosResponse } from 'axios'
import App from '../App'
import LoginPage from '../pages/LoginPage'
import CambiarPasswordPage from '../pages/CambiarPasswordPage'
import PrivateRoute from '../components/PrivateRoute'
import { authApi, TOKEN_KEY, USER_KEY } from './apiClient'
import { cambiarPassword, debeCambiarPassword } from './authService'
import { jwtRequiereCambioPassword } from './jwtSession'

// valores ficticios de prueba (no son contraseñas reales)
const NUEVA = 'Valor-ficticio-1'
const usuario = { id: 9, username: 'contacto@acme.pe', nombreCompleto: 'Acme SAC', roles: ['CLIENTE_MYPE'] }
const enUnaHora = () => Math.floor(Date.now() / 1000) + 3600

function jwt(claims: Record<string, unknown>): string {
  return `${btoa('{}')}.${btoa(JSON.stringify(claims))}.firma-de-prueba`
}
const restringido = () => jwt({ exp: enUnaHora(), roles: [], cambiarPassword: true })
const normal = () => jwt({ exp: enUnaHora(), roles: ['CLIENTE_MYPE'], clienteId: 4 })

function sesion(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(usuario))
}

function rutas(inicial: string) {
  return render(
    <MemoryRouter initialEntries={[inicial]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/cambiar-password" element={<CambiarPasswordPage />} />
        <Route element={<PrivateRoute />}>
          <Route path="/" element={<div>Área privada</div>} />
          <Route path="/calendario" element={<div>Calendario privado</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.useRealTimers()
  localStorage.clear()
})

describe('jwtRequiereCambioPassword (HU-37)', () => {
  it('solo el token con cambiarPassword en true es el restringido', () => {
    expect(jwtRequiereCambioPassword(restringido())).toBe(true)
    expect(jwtRequiereCambioPassword(normal())).toBe(false)
    expect(jwtRequiereCambioPassword(jwt({ exp: 1, cambiarPassword: false }))).toBe(false)
    expect(jwtRequiereCambioPassword(jwt({ exp: 1, cambiarPassword: 'true' }))).toBe(false)
  })

  it('un token mal formado no es el restringido y no rompe', () => {
    expect(jwtRequiereCambioPassword('')).toBe(false)
    expect(jwtRequiereCambioPassword('a.b')).toBe(false)
    expect(jwtRequiereCambioPassword('a.%%%.c')).toBe(false)
  })

  it('lee el payload en base64url (con - y _)', () => {
    const payload = btoa(JSON.stringify({ exp: 4102444800, cambiarPassword: true, nota: '???>>>' })).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
    expect(jwtRequiereCambioPassword(`${btoa('{}')}.${payload}.f`)).toBe(true)
  })
})

describe('debeCambiarPassword y cambiarPassword (HU-37)', () => {
  it('es verdadero solo con la sesión restringida vigente', () => {
    expect(debeCambiarPassword()).toBe(false)
    sesion(normal())
    expect(debeCambiarPassword()).toBe(false)
    sesion(restringido())
    expect(debeCambiarPassword()).toBe(true)
    sesion(jwt({ exp: Math.floor(Date.now() / 1000) - 10, cambiarPassword: true }))
    expect(debeCambiarPassword()).toBe(false) // vencido: ya no hay sesión
  })

  it('cambiar la contraseña reemplaza la sesión restringida por la normal', async () => {
    sesion(restringido())
    const token = normal()
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({
      data: { token, tipo: 'Bearer', expiraEnSegundos: 3600, usuario, debeCambiarPassword: false },
    } as AxiosResponse)

    expect(await cambiarPassword(NUEVA)).toEqual(usuario)

    expect(post).toHaveBeenCalledWith('/api/auth/cambiar-password', { passwordNueva: NUEVA })
    expect(localStorage.getItem(TOKEN_KEY)).toBe(token)
    expect(debeCambiarPassword()).toBe(false)
    expect(JSON.stringify({ ...localStorage })).not.toContain(NUEVA) // la contraseña no queda guardada
  })

  it('si el servidor lo rechaza la sesión restringida queda como estaba', async () => {
    const antes = restringido()
    sesion(antes)
    vi.spyOn(authApi, 'post').mockRejectedValue(new Error('rechazado'))

    await expect(cambiarPassword(NUEVA)).rejects.toThrow('rechazado')

    expect(localStorage.getItem(TOKEN_KEY)).toBe(antes)
    expect(debeCambiarPassword()).toBe(true)
  })
})

describe('el primer ingreso obliga a cambiar la contraseña (HU-37)', () => {
  it('con la sesión restringida cualquier ruta privada lleva a cambiar la contraseña y no muestra el sistema', async () => {
    sesion(restringido())

    rutas('/calendario')

    expect(await screen.findByRole('heading', { name: 'Cambie su contraseña' })).toBeTruthy()
    expect(screen.queryByText('Calendario privado')).toBeNull()
    expect(screen.queryByText('Área privada')).toBeNull()
  })

  it('con la sesión normal las rutas privadas se ven y cambiar la contraseña ya no aplica', async () => {
    sesion(normal())

    rutas('/calendario')
    expect(await screen.findByText('Calendario privado')).toBeTruthy()
    cleanup()

    rutas('/cambiar-password')
    expect(await screen.findByText('Área privada')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Cambie su contraseña' })).toBeNull()
  })

  it('sin sesión la pantalla de cambio lleva al login', async () => {
    rutas('/cambiar-password')

    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeTruthy()
  })

  it('un ingreso con la temporal lleva a cambiar la contraseña', async () => {
    const post = vi.spyOn(authApi, 'post').mockImplementation(async () => {
      return { data: { token: restringido(), tipo: 'Bearer', expiraEnSegundos: 3600, usuario, debeCambiarPassword: true } } as AxiosResponse
    })

    rutas('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Usuario'), 'contacto@acme.pe')
    await user.type(screen.getByLabelText('Contraseña'), 'valor-temporal-ficticio')
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('heading', { name: 'Cambie su contraseña' })).toBeTruthy()
    expect(screen.queryByText('Área privada')).toBeNull()
    expect(post).toHaveBeenCalledWith('/api/auth/login', { username: 'contacto@acme.pe', password: 'valor-temporal-ficticio' })
  })

  it('un ingreso normal sigue yendo al sistema', async () => {
    vi.spyOn(authApi, 'post').mockResolvedValue({
      data: { token: normal(), tipo: 'Bearer', expiraEnSegundos: 3600, usuario, debeCambiarPassword: false },
    } as AxiosResponse)

    rutas('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Usuario'), 'contacto@acme.pe')
    await user.type(screen.getByLabelText('Contraseña'), 'valor-ficticio')
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Área privada')).toBeTruthy()
  })

  it('quien ya tiene la sesión restringida y abre el login vuelve a la pantalla de cambio', async () => {
    sesion(restringido())

    rutas('/login')

    expect(await screen.findByRole('heading', { name: 'Cambie su contraseña' })).toBeTruthy()
  })

  it('la aplicación real lleva a la pantalla de cambio desde cualquier ruta y sin el menú del sistema', async () => {
    sesion(restringido())

    render(<MemoryRouter initialEntries={['/clientes/4']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Cambie su contraseña' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Abrir menú' })).toBeNull()
    expect(screen.queryByRole('navigation')).toBeNull()
  })
})

describe('pantalla de cambio de contraseña (HU-37)', () => {
  const escribir = async (user: ReturnType<typeof userEvent.setup>, nueva: string, repetida: string) => {
    await user.type(screen.getByLabelText('Contraseña nueva'), nueva)
    await user.type(screen.getByLabelText('Repita la contraseña nueva'), repetida)
    await user.click(screen.getByRole('button', { name: 'Cambiar contraseña' }))
  }

  beforeEach(() => sesion(restringido()))

  it('explica las reglas de 8 a 72 caracteres', () => {
    rutas('/cambiar-password')
    expect(screen.getByText(/de 8 a 72 caracteres/)).toBeTruthy()
  })

  it('con una contraseña que no cumple no envía nada y dice el motivo', async () => {
    const post = vi.spyOn(authApi, 'post')
    rutas('/cambiar-password')
    const user = userEvent.setup()

    await escribir(user, 'corta', 'corta')
    expect((await screen.findByRole('alert')).textContent).toContain('de 8 a 72 caracteres')

    await user.clear(screen.getByLabelText('Contraseña nueva'))
    await user.clear(screen.getByLabelText('Repita la contraseña nueva'))
    await escribir(user, 'contacto@acme.pe', 'contacto@acme.pe')
    expect((await screen.findByRole('alert')).textContent).toContain('no puede ser igual al correo')

    await user.clear(screen.getByLabelText('Contraseña nueva'))
    await user.clear(screen.getByLabelText('Repita la contraseña nueva'))
    await escribir(user, NUEVA, 'Valor-ficticio-2')
    expect((await screen.findByRole('alert')).textContent).toContain('no coinciden')

    expect(post).not.toHaveBeenCalled()
  })

  it('al cambiarla guarda la sesión normal y entra al sistema', async () => {
    const token = normal()
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({
      data: { token, tipo: 'Bearer', expiraEnSegundos: 3600, usuario, debeCambiarPassword: false },
    } as AxiosResponse)
    rutas('/cambiar-password')
    const user = userEvent.setup()

    await escribir(user, NUEVA, NUEVA)

    expect(await screen.findByText('Área privada')).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/api/auth/cambiar-password', { passwordNueva: NUEVA })
    expect(localStorage.getItem(TOKEN_KEY)).toBe(token)
  })

  it('si el servidor la rechaza (por ejemplo, es la temporal) lo muestra y se puede volver a intentar', async () => {
    vi.spyOn(authApi, 'post').mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined,
      { status: 400, data: { mensaje: 'La contraseña nueva no puede ser la temporal' } } as never))
    rutas('/cambiar-password')
    const user = userEvent.setup()

    await escribir(user, NUEVA, NUEVA)

    expect((await screen.findByRole('alert')).textContent).toContain('no puede ser la temporal')
    expect((screen.getByRole('button', { name: 'Cambiar contraseña' }) as HTMLButtonElement).disabled).toBe(false)
    expect(screen.queryByText('Área privada')).toBeNull()
    expect(debeCambiarPassword()).toBe(true)
  })

  it('«Salir» cierra la sesión y vuelve al login', async () => {
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({ data: undefined } as AxiosResponse)
    rutas('/cambiar-password')

    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))

    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/api/auth/logout')
    await waitFor(() => expect(localStorage.getItem(TOKEN_KEY)).toBeNull())
  })

  it('las contraseñas no se guardan en ningún almacenamiento del navegador', async () => {
    vi.spyOn(authApi, 'post').mockRejectedValue(new Error('x'))
    rutas('/cambiar-password')
    const user = userEvent.setup()

    await escribir(user, NUEVA, NUEVA)
    await screen.findByRole('alert')

    expect(JSON.stringify({ ...localStorage })).not.toContain(NUEVA)
    expect(JSON.stringify({ ...sessionStorage })).not.toContain(NUEVA)
  })
})
