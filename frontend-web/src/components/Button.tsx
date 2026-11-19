import { ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'secondary' | 'danger'

const estilos: Record<Variant, string> = {
  primary: 'bg-acento text-white shadow-sm hover:bg-acento-oscuro disabled:bg-slate-300 disabled:shadow-none',
  secondary: 'border border-slate-300 bg-white/80 text-tinta hover:bg-white disabled:text-slate-400',
  danger: 'border border-red-200 bg-white/80 text-peligro-texto hover:bg-red-50 disabled:text-red-300',
}

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
}

export default function Button({ variant = 'primary', className = '', type = 'button', ...rest }: Props) {
  return (
    <button
      type={type}
      className={`inline-flex items-center justify-center gap-2 rounded-campo px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed ${estilos[variant]} ${className}`}
      {...rest}
    />
  )
}
