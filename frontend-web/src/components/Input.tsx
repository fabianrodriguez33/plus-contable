import { InputHTMLAttributes, SelectHTMLAttributes, ReactNode, useId } from 'react'

const base =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-100'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
}

export function Input({ label, className = '', id, ...rest }: InputProps) {
  const auto = useId()
  const inputId = id ?? auto
  return (
    <div>
      <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
      <input id={inputId} className={`${base} ${className}`} {...rest} />
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
