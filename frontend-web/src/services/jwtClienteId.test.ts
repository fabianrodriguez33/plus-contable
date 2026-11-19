import { describe, expect, it } from 'vitest'
import { jwtClienteId } from './jwtSession'

const token = (claims: object) => `x.${btoa(JSON.stringify(claims))}.y`

describe('jwtClienteId (HU-35)', () => {
  it('lee la empresa de la cuenta de cliente', () => {
    expect(jwtClienteId(token({ clienteId: 7 }))).toBe(7)
  })

  it('una cuenta del estudio, un valor inválido o un token roto no tienen empresa', () => {
    expect(jwtClienteId(token({ roles: ['CONTADOR'] }))).toBeNull()
    expect(jwtClienteId(token({ clienteId: '7' }))).toBeNull()
    expect(jwtClienteId(token({ clienteId: 0 }))).toBeNull()
    expect(jwtClienteId('roto')).toBeNull()
    expect(jwtClienteId('a.%%%.c')).toBeNull()
  })
})
