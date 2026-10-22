/** Comprueba la caducidad del JWT en el cliente; la firma se valida en el backend. */
export function isJwtActive(token: string, now = Date.now()): boolean {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) return false

  try {
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims: unknown = JSON.parse(atob(payload))
    if (typeof claims !== 'object' || claims === null || !('exp' in claims)) return false
    const exp = claims.exp
    return typeof exp === 'number' && Number.isSafeInteger(exp) && exp > now / 1000
  } catch {
    return false
  }
}
