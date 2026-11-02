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

export function isJwtActive(token: string, now = Date.now()): boolean {
  const expiration = jwtExpirationMs(token)
  return expiration !== null && expiration > now
}
