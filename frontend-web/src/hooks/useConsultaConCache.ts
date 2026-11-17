import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { mensajeError } from '../services/apiClient'
import { guardar, leer } from '../services/cacheLocal'

/**
 * HU-34: en línea · sin conexión con copia local · sin conexión y sin copia (primera vez) · error del servidor.
 * Solo la falta de respuesta del servidor cuenta como «sin conexión»: un 401, 403 o 500 nunca se tapa con la copia local.
 */
export type EstadoConsulta = 'cargando' | 'en-linea' | 'sin-conexion' | 'primera-vez' | 'error'

const sinRespuesta = (e: unknown) => axios.isAxiosError(e) && !e.response

/** Consulta con copia local por `clave` (null = todavía no se sabe qué consultar). Al volver el internet se vuelve a consultar. */
export function useConsultaConCache<T>(clave: string | null, cargar: () => Promise<T>) {
  const [datos, setDatos] = useState<T | null>(null)
  const [estado, setEstado] = useState<EstadoConsulta>('cargando')
  const [actualizadoEn, setActualizadoEn] = useState<string | null>(null)
  const [error, setError] = useState('')
  const cargarActual = useRef(cargar)
  cargarActual.current = cargar

  const consultar = useCallback(() => {
    if (clave === null) return
    cargarActual.current().then((d) => {
      setDatos(d)
      setActualizadoEn(guardar(clave, d))
      setError('')
      setEstado('en-linea')
    }).catch((e) => {
      if (!sinRespuesta(e)) {
        setError(mensajeError(e))
        setEstado('error')
        return
      }
      const copia = leer<T>(clave)
      if (copia) {
        setDatos(copia.datos)
        setActualizadoEn(copia.actualizadoEn)
        setEstado('sin-conexion')
      } else {
        setEstado('primera-vez')
      }
    })
  }, [clave])

  useEffect(() => {
    consultar()
    const alPerder = () => setEstado((actual) => (actual === 'en-linea' ? 'sin-conexion' : actual))
    window.addEventListener('online', consultar)
    window.addEventListener('offline', alPerder)
    return () => {
      window.removeEventListener('online', consultar)
      window.removeEventListener('offline', alPerder)
    }
  }, [consultar])

  return { datos, estado, actualizadoEn, error }
}
