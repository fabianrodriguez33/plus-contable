import { describe, expect, it } from 'vitest'
import { ENLACES } from '../components/Sidebar'
import { ETIQUETA_TIPO, estiloDias, textoDiasRestantes, textoEscalon } from './alertas'

describe('alertas de plazo (HU-19)', () => {
  it('los días restantes se dicen en singular, plural y el día del plazo', () => {
    expect(textoDiasRestantes(0)).toBe('Vence hoy')
    expect(textoDiasRestantes(1)).toBe('Falta 1 día')
    expect(textoDiasRestantes(2)).toBe('Faltan 2 días')
    expect(textoDiasRestantes(5)).toBe('Faltan 5 días')
  })

  it('el color sube con la urgencia: hoy y mañana rojo, hasta 3 días ámbar, el resto gris', () => {
    expect(estiloDias(0)).toContain('red')
    expect(estiloDias(1)).toContain('red')
    expect(estiloDias(2)).toContain('amber')
    expect(estiloDias(3)).toContain('amber')
    expect(estiloDias(4)).toContain('slate')
  })

  it('el escalón de la alerta se dice en singular y plural', () => {
    expect(textoEscalon(5)).toBe('Alerta de 5 días antes')
    expect(textoEscalon(1)).toBe('Alerta de 1 día antes')
    expect(textoEscalon(0)).toBe('Alerta del día del plazo')
  })

  it('cada tipo de notificación tiene su nombre', () => {
    expect(ETIQUETA_TIPO).toEqual({ ESQUELA: 'Esquela', CARTA_INDUCTIVA: 'Carta inductiva', COMUNICADO: 'Comunicado' })
  })

  it('el menú tiene el enlace a las alertas de plazo', () => {
    expect(ENLACES).toContainEqual(expect.objectContaining({ to: '/alertas-plazo', etiqueta: 'Alertas de plazo' }))
  })
})
