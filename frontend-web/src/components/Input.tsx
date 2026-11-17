import { InputHTMLAttributes, SelectHTMLAttributes, ReactNode, useId } from 'react'

const base =
  'w-full rounded-campo border border-slate-300 bg-white/90 px-3 py-2 text-sm text-tinta placeholder:text-slate-500 focus:border-acento focus:outline-none'

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
      <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-tinta">{label}</label>
      <input id={inputId} className={`${base} ${error ? 'border-peligro focus:border-peligro' : ''} ${className}`}
        aria-invalid={error ? true : undefined} aria-describedby={error ? `${inputId}-error` : undefined} {...rest} />
      {error && <p id={`${inputId}-error`} role="alert" className="mt-1 text-xs text-peligro-texto">{error}</p>}
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
      <label htmlFor={selectId} className="mb-1 block text-sm font-medium text-tinta">{label}</label>
      <select id={selectId} className={`${base} ${className}`} {...rest}>{children}</select>
    </div>
  )
}

export default Input
