import { ReactNode } from 'react'
import { AlertCircle, AlertTriangle, CheckCircle2, Info } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export type TipoAviso = 'exito' | 'peligro' | 'advertencia' | 'info'

const ESTILOS: Record<TipoAviso, { caja: string; icono: LucideIcon; claseIcono: string }> = {
  exito: { caja: 'border-green-200 bg-green-50 text-exito-texto', icono: CheckCircle2, claseIcono: 'text-exito' },
  peligro: { caja: 'border-red-200 bg-red-50 text-peligro-texto', icono: AlertCircle, claseIcono: 'text-peligro' },
  // El ámbar solo es el filo de acento; el texto va en tinta
  advertencia: { caja: 'border-amber-200 border-l-4 border-l-ambar bg-amber-50 text-tinta', icono: AlertTriangle, claseIcono: 'text-amber-700' },
  info: { caja: 'border-slate-200 bg-white/80 text-acento-oscuro', icono: Info, claseIcono: 'text-acento' },
}

interface Props {
  tipo: TipoAviso
  children: ReactNode
  className?: string
}

/** Mensaje en línea. Los errores se anuncian como alerta; el resto, como estado. Los colores nunca son lo único que lo distingue: lleva ícono. */
export default function Aviso({ tipo, children, className = '' }: Props) {
  const { caja, icono: Icono, claseIcono } = ESTILOS[tipo]
  return (
    <div role={tipo === 'peligro' ? 'alert' : 'status'}
      className={`flex items-start gap-2 rounded-campo border px-3 py-2.5 text-sm ${caja} ${className}`}>
      <Icono size={16} aria-hidden="true" className={`mt-0.5 shrink-0 ${claseIcono}`} />
      <div className="min-w-0">{children}</div>
    </div>
  )
}
