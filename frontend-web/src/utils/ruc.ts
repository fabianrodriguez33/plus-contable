const FACTORES = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2]

/** Prefijos presentes en el padrón reducido de SUNAT: 10 (persona natural), 15, 17 y 20 (persona jurídica). */
export const PREFIJOS_RUC = ['10', '15', '17', '20']

/** Dígito verificador (módulo 11) que corresponde a los 10 primeros dígitos. */
function digitoVerificador(ruc: string): number {
  const suma = FACTORES.reduce((acc, f, i) => acc + Number(ruc[i]) * f, 0)
  const dv = 11 - (suma % 11)
  return dv === 10 ? 0 : dv === 11 ? 1 : dv
}

/** Devuelve el motivo por el que el RUC no es válido, o null si lo es. Mismas reglas que ms-tributario. */
export function motivoRucInvalido(ruc: string): string | null {
  if (!/^\d{11}$/.test(ruc)) return 'El RUC debe tener exactamente 11 dígitos numéricos'
  if (!PREFIJOS_RUC.includes(ruc.slice(0, 2))) return 'El RUC debe iniciar con 10, 15, 17 o 20'
  if (digitoVerificador(ruc) !== Number(ruc[10])) return 'El dígito verificador del RUC no es válido'
  return null
}
