/** Lee exp para gestionar la sesión local; la firma se valida en el backend. */
export function jwtExpirationMs(token: string): number | null {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) return null

  try {
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims: unknown = JSON.parse(atob(payload))
    if (typeof claims !== 'object' || claims === null || !('exp' in claims)) return null
    const exp = claims.exp
    return typeof exp === 'number' && Number.isSafeInteger(exp) ? exp * 1000 : null
  } catch {
    return null
  }
}

/**
 * HU-37: el token restringido del primer ingreso con la contraseña temporal lleva `cambiarPassword: true` y solo sirve para
 * cambiarla. Se lee del token (no de un aviso aparte) para que siga valiendo al recargar la página.
 */
export function jwtRequiereCambioPassword(token: string): boolean {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return false
  try {
    const claims: unknown = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof claims === 'object' && claims !== null && 'cambiarPassword' in claims && claims.cambiarPassword === true
  } catch {
    return false
  }
}

export function isJwtActive(token: string, now = Date.now()): boolean {
  const expiration = jwtExpirationMs(token)
  return expiration !== null && expiration > now
}
