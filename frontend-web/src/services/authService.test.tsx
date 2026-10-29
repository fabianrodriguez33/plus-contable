// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { AxiosResponse } from 'axios'
import LoginPage from '../pages/LoginPage'
import Navbar from '../components/Navbar'
import PrivateRoute from '../components/PrivateRoute'
import { authApi, TOKEN_KEY, USER_KEY } from './apiClient'
import { isAuthenticated, login, logout } from './authService'
import { isJwtActive } from './jwtSession'

const usuario = { id: 1, username: 'prueba', nombreCompleto: 'Usuario de prueba', roles: ['ADMIN'] }

function jwt(exp: number): string {
  return `${btoa('{}')}.${btoa(JSON.stringify({ exp }))}.firma-de-prueba`
}

function rutas(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<PrivateRoute />}>
          <Route element={<Navbar />}>
            <Route path="/" element={<div>Área privada</div>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
})

describe('HU-36: sesión y login', () => {
  it('inicia sesión y permite entrar a una ruta protegida', async () => {
    const token = jwt(Math.floor(Date.now() / 1000) + 3600)
    const post = vi.spyOn(authApi, 'post').mockResolvedValue({
      data: { token, tipo: 'Bearer', expiraEnSegundos: 3600, usuario },
    } as AxiosResponse)

    rutas('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Usuario'), 'prueba')
    await user.type(screen.getByLabelText('Contraseña'), 'clave-de-prueba')
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Área privada')).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/api/auth/login', {
      username: 'prueba', password: 'clave-de-prueba',
    })
    expect(localStorage.getItem(TOKEN_KEY)).toBe(token)
    expect(isAuthenticated()).toBe(true)
  })

  it('muestra el error de credenciales incorrectas sin guardar sesión', async () => {
    vi.spyOn(authApi, 'post').mockRejectedValue({
      isAxiosError: true,
      response: { status: 401, data: { mensaje: 'Usuario o contraseña incorrectos' } },
    })
    rutas('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Usuario'), 'prueba')
    await user.type(screen.getByLabelText('Contraseña'), 'incorrecta')
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Usuario o contraseña incorrectos')).toBeTruthy()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(screen.queryByText('Área privada')).toBeNull()
  })

  it('cierra sesión y vuelve a la pantalla de acceso', async () => {
    localStorage.setItem(TOKEN_KEY, jwt(Math.floor(Date.now() / 1000) + 3600))
    localStorage.setItem(USER_KEY, JSON.stringify(usuario))
    rutas('/')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Salir' }))

    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeTruthy()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBeNull()
  })

  it('redirige una ruta protegida cuando no hay sesión', () => {
    rutas('/')
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeTruthy()
    expect(screen.queryByText('Área privada')).toBeNull()
  })

  it('rechaza un JWT expirado y limpia la sesión', () => {
    localStorage.setItem(TOKEN_KEY, jwt(Math.floor(Date.now() / 1000) - 1))
    localStorage.setItem(USER_KEY, JSON.stringify(usuario))
    rutas('/')

    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeTruthy()
    expect(screen.queryByText('Área privada')).toBeNull()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBeNull()
  })

  it('rechaza tokens malformados y exige exp numérico futuro', () => {
    expect(isJwtActive('token-invalido')).toBe(false)
    expect(isJwtActive(`${btoa('{}')}.${btoa(JSON.stringify({ exp: '9999999999' }))}.firma`)).toBe(false)
    expect(isJwtActive(jwt(100), 100000)).toBe(false)
    expect(isJwtActive(jwt(101), 100000)).toBe(true)
  })

  it('borra token y usuario al cerrar sesión directamente', () => {
    localStorage.setItem(TOKEN_KEY, jwt(Math.floor(Date.now() / 1000) + 3600))
    localStorage.setItem(USER_KEY, JSON.stringify(usuario))
    logout()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBeNull()
  })

  it('conserva la respuesta del servicio de login', async () => {
    const token = jwt(Math.floor(Date.now() / 1000) + 3600)
    vi.spyOn(authApi, 'post').mockResolvedValue({
      data: { token, tipo: 'Bearer', expiraEnSegundos: 3600, usuario },
    } as AxiosResponse)
    expect(await login('prueba', 'clave-de-prueba')).toEqual(usuario)
  })
})
