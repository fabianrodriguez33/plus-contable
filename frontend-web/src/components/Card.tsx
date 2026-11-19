import { ReactNode } from 'react'

interface Props {
  title?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
  /** Se conserva por compatibilidad: ahora todas las pantallas son una misma hoja de papel. */
  variante?: 'vidrio' | 'solida'
}

/** Una hoja de papel contable: título en serif sobre una regla fina y, debajo, el contenido. */
export default function Card({ title, actions, children, className = '' }: Props) {
  return (
    <section className={`hoja rounded-panel p-5 md:p-7 ${className}`}>
      {(title || actions) && (
        <header className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-tinta/15 pb-3">
          {title && <h2 className="text-xl font-semibold text-tinta">{title}</h2>}
          {actions}
        </header>
      )}
      {children}
    </section>
  )
}
