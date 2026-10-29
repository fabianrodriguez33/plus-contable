import { useEffect } from 'react'
import { NavLink } from 'react-router-dom'
import { Building2, LayoutDashboard, UserPlus, X } from 'lucide-react'

export const ENLACES = [
  { to: '/', etiqueta: 'Clientes', icono: LayoutDashboard },
  { to: '/clientes/nuevo', etiqueta: 'Nuevo cliente', icono: UserPlus },
]

function Enlaces({ onNavegar }: { onNavegar?: () => void }) {
  return (
    <nav className="flex flex-col gap-1 px-3">
      {ENLACES.map(({ to, etiqueta, icono: Icono }) => (
        <NavLink key={to} to={to} end onClick={onNavegar}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
              isActive ? 'bg-white/15 text-white' : 'hover:bg-white/10'}`}>
          <Icono size={18} /> {etiqueta}
        </NavLink>
      ))}
    </nav>
  )
}

const Marca = () => (
  <div className="flex items-center gap-2 px-6 py-5 text-lg font-bold text-white">
    <Building2 size={22} /> Plus Contable
  </div>
)

/** Barra lateral fija en pantallas medianas y grandes. */
export default function Sidebar() {
  return (
    <aside className="hidden w-60 shrink-0 flex-col bg-brand-900 text-slate-200 md:flex">
      <Marca />
      <Enlaces />
    </aside>
  )
}

interface MenuMovilProps {
  abierto: boolean
  onCerrar: () => void
}

/** Menú lateral desplegable para pantallas pequeñas (donde la barra lateral fija se oculta). */
export function MenuMovil({ abierto, onCerrar }: MenuMovilProps) {
  useEffect(() => {
    if (!abierto) return
    const alTeclear = (e: KeyboardEvent) => e.key === 'Escape' && onCerrar()
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [abierto, onCerrar])

  if (!abierto) return null
  return (
    <div className="fixed inset-0 z-40 md:hidden">
      <div className="absolute inset-0 bg-slate-900/50" onClick={onCerrar} aria-hidden="true" />
      <div id="menu-movil" role="dialog" aria-modal="true" aria-label="Menú de navegación"
        className="absolute inset-y-0 left-0 flex w-64 flex-col bg-brand-900 text-slate-200 shadow-xl">
        <div className="flex items-center justify-between pr-3">
          <Marca />
          <button type="button" aria-label="Cerrar menú" onClick={onCerrar}
            className="rounded p-1 text-slate-300 hover:bg-white/10"><X size={20} /></button>
        </div>
        <Enlaces onNavegar={onCerrar} />
      </div>
    </div>
  )
}
