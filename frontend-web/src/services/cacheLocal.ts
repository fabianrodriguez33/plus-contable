const PREFIJO = 'pc_cache:'

export interface Guardado<T> {
  datos: T
  /** ISO-8601 del último momento en que el servidor respondió con estos datos. */
  actualizadoEn: string
}

/** HU-34: la copia local de lo último que el servidor entregó. Si el navegador no deja guardar, se sigue sin copia. */
export function guardar<T>(clave: string, datos: T): string {
  const actualizadoEn = new Date().toISOString()
  try {
    localStorage.setItem(PREFIJO + clave, JSON.stringify({ datos, actualizadoEn }))
  } catch {
    // almacenamiento lleno o bloqueado: la consulta en línea sigue funcionando
  }
  return actualizadoEn
}

export function leer<T>(clave: string): Guardado<T> | null {
  try {
    const raw = localStorage.getItem(PREFIJO + clave)
    if (!raw) return null
    const g: unknown = JSON.parse(raw)
    return typeof g === 'object' && g !== null && 'datos' in g && 'actualizadoEn' in g && typeof g.actualizadoEn === 'string'
      ? (g as Guardado<T>)
      : null
  } catch {
    return null
  }
}

/** Al cerrar sesión no queda en el equipo la información de la empresa: otra cuenta no debe verla. */
export function limpiarCache(): void {
  try {
    Object.keys(localStorage).filter((k) => k.startsWith(PREFIJO)).forEach((k) => localStorage.removeItem(k))
  } catch {
    // sin almacenamiento no hay nada que limpiar
  }
}

/** dd/MM/yyyy HH:mm en hora de Lima, para que la fecha de actualización no dependa de la zona del equipo. */
export function fechaHora(iso: string): string {
  const p = Object.fromEntries(new Intl.DateTimeFormat('es-PE', {
    timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(iso)).map((x) => [x.type, x.value]))
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}`
}
