// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fechaHora, guardar, leer, limpiarCache } from './cacheLocal'

afterEach(() => { localStorage.clear(); vi.restoreAllMocks() })

describe('copia local (HU-34)', () => {
  it('guarda los datos con su fecha de actualización y los devuelve', () => {
    const cuando = guardar('datos-empresa:4', { razonSocial: 'Acme SAC' })
    expect(leer('datos-empresa:4')).toEqual({ datos: { razonSocial: 'Acme SAC' }, actualizadoEn: cuando })
    expect(new Date(cuando).toISOString()).toBe(cuando)
  })

  it('sin copia, o con una copia dañada, no devuelve nada', () => {
    expect(leer('nada')).toBeNull()
    localStorage.setItem('pc_cache:roto', '{no es json')
    localStorage.setItem('pc_cache:sinFecha', JSON.stringify({ datos: 1 }))
    expect(leer('roto')).toBeNull()
    expect(leer('sinFecha')).toBeNull()
  })

  it('si el navegador no deja guardar, no se cae', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('lleno') })
    expect(() => guardar('k', 1)).not.toThrow()
  })

  it('limpiarCache borra solo las copias, no la sesión', () => {
    localStorage.setItem('pc_token', 'abc')
    guardar('datos-empresa:4', 1)
    guardar('datos-empresa:5', 2)
    limpiarCache()
    expect(leer('datos-empresa:4')).toBeNull()
    expect(leer('datos-empresa:5')).toBeNull()
    expect(localStorage.getItem('pc_token')).toBe('abc')
  })

  it('la fecha de actualización se muestra en hora de Lima', () => {
    expect(fechaHora('2026-11-17T15:35:00Z')).toBe('17/11/2026 10:35')
    expect(fechaHora('2026-11-18T05:05:00Z')).toBe('18/11/2026 00:05')
  })
})
