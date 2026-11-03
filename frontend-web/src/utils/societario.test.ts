import { describe, expect, it } from 'vitest'
import { estadoEstructura, mensajeDiferencia } from './societario'

/** HU-02: el porcentaje lo calcula el servidor; la pantalla solo sabe si la estructura se puede guardar. */
describe('estadoEstructura', () => {
  it('suma los aportes en centésimas sin errores de coma flotante', () => {
    const e = estadoEstructura([{ id: 1, aporte: 0.1 }, { id: 2, aporte: 0.2 }])
    expect(e.capitalSocialTotal).toBe(0.3)
  })

  it('no se puede guardar sin socios', () => {
    expect(estadoEstructura([]).puedeGuardar).toBe(false)
  })

  it('un socio sin aporte deja la estructura incompleta y sin poder guardarse', () => {
    const e = estadoEstructura([{ id: 1, aporte: 5000 }, { id: 2, aporte: 0 }])
    expect(e.sinAporte).toBe(1)
    expect(e.puedeGuardar).toBe(false)
  })

  it('cuenta los socios que aún no se guardaron', () => {
    const e = estadoEstructura([{ id: 1, aporte: 100 }, { aporte: 50 }])
    expect(e.sinGuardar).toBe(1)
    expect(e.puedeGuardar).toBe(true)
  })
})

describe('mensajeDiferencia', () => {
  it('describe lo que falta, lo que sobra y el 100 % exacto', () => {
    expect(mensajeDiferencia(10)).toBe('Faltan 10.00% para llegar a 100.00%')
    expect(mensajeDiferencia(-2.5)).toBe('Sobran 2.50% sobre 100.00%')
    expect(mensajeDiferencia(0)).toBe('La participación suma exactamente 100.00%')
  })
})
