import { ReactNode, useEffect } from 'react'
import { X } from 'lucide-react'

interface Props {
  abierto: boolean
  titulo: string
  onCerrar: () => void
  children: ReactNode
}

export default function Modal({ abierto, titulo, onCerrar, children }: Props) {
  useEffect(() => {
    if (!abierto) return
    const alTeclear = (e: KeyboardEvent) => e.key === 'Escape' && onCerrar()
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [abierto, onCerrar])

  if (!abierto) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-tinta/50 p-4" onClick={onCerrar}>
      <div role="dialog" aria-modal="true" aria-label={titulo}
        className="hoja max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-panel p-5 shadow-2xl md:p-7" onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between border-b border-tinta/15 pb-3">
          <h3 className="text-lg font-semibold text-tinta">{titulo}</h3>
          <button type="button" aria-label="Cerrar" onClick={onCerrar} className="rounded-campo p-1 text-slate-500 hover:bg-slate-100">
            <X size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
