/** HU-37: largo permitido de la contraseña nueva (el servidor lo valida igual; BCrypt solo usa 72 bytes). */
export const PASSWORD_MINIMO = 8
export const PASSWORD_MAXIMO = 72

/** Las mismas reglas del servidor para avisar antes de enviar; devuelve el motivo o null si está bien. */
export function validarPasswordNueva(nueva: string, repetida: string, correo: string): string | null {
  if (!nueva) return 'Escriba la contraseña nueva'
  if (nueva.length < PASSWORD_MINIMO || new TextEncoder().encode(nueva).length > PASSWORD_MAXIMO) {
    return `La contraseña nueva debe tener de ${PASSWORD_MINIMO} a ${PASSWORD_MAXIMO} caracteres`
  }
  if (correo && nueva.toLowerCase() === correo.toLowerCase()) return 'La contraseña nueva no puede ser igual al correo'
  if (nueva !== repetida) return 'Las dos contraseñas no coinciden'
  return null
}
