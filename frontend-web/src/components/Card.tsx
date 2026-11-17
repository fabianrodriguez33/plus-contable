import { ReactNode } from 'react'

interface Props {
  title?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
  /** «vidrio» (por defecto) para paneles; «solida» para listados largos donde el desenfoque resta legibilidad. */
  variante?: 'vidrio' | 'solida'
}

export default function Card({ title, actions, children, className = '', variante = 'vidrio' }: Props) {
  return (
    <section className={`${variante === 'vidrio' ? 'vidrio' : 'panel-solido'} rounded-panel p-6 md:p-8 ${className}`}>
      {(title || actions) && (
        <header className="mb-5 flex items-center justify-between gap-4">
          {title && <h2 className="text-lg font-semibold text-tinta">{title}</h2>}
          {actions}
        </header>
      )}
      {children}
    </section>
  )
}
