import { InputHTMLAttributes, SelectHTMLAttributes, ReactNode, useId } from 'react'

const base =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-100'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** Mensaje de validación que se muestra bajo el campo. */
  error?: string | null
}

export function Input({ label, className = '', id, error, ...rest }: InputProps) {
  const auto = useId()
  const inputId = id ?? auto
  return (
    <div>
      <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
      <input id={inputId} className={`${base} ${error ? 'border-red-400 focus:border-red-500 focus:ring-red-100' : ''} ${className}`}
        aria-invalid={error ? true : undefined} aria-describedby={error ? `${inputId}-error` : undefined} {...rest} />
      {error && <p id={`${inputId}-error`} role="alert" className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  children: ReactNode
}

export function Select({ label, className = '', id, children, ...rest }: SelectProps) {
  const auto = useId()
  const selectId = id ?? auto
  return (
    <div>
      <label htmlFor={selectId} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
      <select id={selectId} className={`${base} ${className}`} {...rest}>{children}</select>
    </div>
  )
}

export default Input
