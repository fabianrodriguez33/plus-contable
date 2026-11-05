import { ReactNode } from 'react'

interface Props {
  title?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
}

export default function Card({ title, actions, children, className = '' }: Props) {
  return (
    <section className={`rounded-xl border border-slate-200 bg-white p-6 shadow-sm ${className}`}>
      {(title || actions) && (
        <header className="mb-4 flex items-center justify-between gap-4">
          {title && <h2 className="text-lg font-semibold text-slate-900">{title}</h2>}
          {actions}
        </header>
      )}
      {children}
    </section>
  )
}
