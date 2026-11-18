// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { tributarioApi, TOKEN_KEY, USER_KEY } from './apiClient'
import { guardar, leer } from './cacheLocal'

const responder = (status: number): AxiosAdapter => (config) =>
  Promise.reject({ config, response: { status, data: {} }, isAxiosError: true })

afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); delete tributarioApi.defaults.adapter })

describe('Sesión vencida (HU-34)', () => {
  it('el 401 borra la sesión y la copia local de la empresa y vuelve al login', async () => {
    vi.stubGlobal('location', { href: '/' })
    localStorage.setItem(TOKEN_KEY, 't'); localStorage.setItem(USER_KEY, '{}')
    guardar('datos-empresa:4', { ruc: '20123456789' })
    tributarioApi.defaults.adapter = responder(401)

    await expect(tributarioApi.get('/api/tributario/clientes/4/datos-empresa')).rejects.toBeTruthy()

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(leer('datos-empresa:4')).toBeNull()
    expect(window.location.href).toBe('/login')
  })

  it('un 500 no toca la sesión ni la copia local', async () => {
    guardar('datos-empresa:4', { ruc: '20123456789' })
    localStorage.setItem(TOKEN_KEY, 't')
    tributarioApi.defaults.adapter = responder(500)

    await expect(tributarioApi.get('/api/x')).rejects.toBeTruthy()

    expect(localStorage.getItem(TOKEN_KEY)).toBe('t')
    expect(leer('datos-empresa:4')).not.toBeNull()
  })
})
