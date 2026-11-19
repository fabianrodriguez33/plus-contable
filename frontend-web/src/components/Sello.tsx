export type TonoSello = 'peligro' | 'aviso' | 'exito' | 'neutro'

const COLOR: Record<TonoSello, string> = {
  peligro: 'text-peligro-texto bg-red-50',
  aviso: 'text-amber-700 bg-amber-50',
  exito: 'text-exito-texto bg-green-50',
  neutro: 'text-slate-600 bg-white',
}

interface Props {
  tono: TonoSello
  /** Lo grande: normalmente el número de días. */
  cifra: string | number
  /** Lo pequeño bajo la cifra («días», «de atraso»). */
  texto: string
  /** Lo que lee un lector de pantalla, completo («Faltan 3 días»). */
  etiqueta: string
}

/** El sello de plazo: cuántos días quedan (o pasaron), en el color del estado y con un marco doble de sello de goma. */
export default function Sello({ tono, cifra, texto, etiqueta }: Props) {
  return (
    <span data-tono={tono} className={`sello ${COLOR[tono]}`}>
      <span aria-hidden="true" className="sello-cifra">{cifra}</span>
      <span aria-hidden="true" className="sello-texto">{texto}</span>
      <span className="sr-only">{etiqueta}</span>
    </span>
  )
}
