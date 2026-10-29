import { NavLink } from 'react-router-dom'
import { Building2, LayoutDashboard, UserPlus } from 'lucide-react'

const enlaces = [
  { to: '/', etiqueta: 'Clientes', icono: LayoutDashboard },
  { to: '/clientes/nuevo', etiqueta: 'Nuevo cliente', icono: UserPlus },
]

export default function Sidebar() {
  return (
    <aside className="hidden w-60 shrink-0 flex-col bg-brand-900 text-slate-200 md:flex">
      <div className="flex items-center gap-2 px-6 py-5 text-lg font-bold text-white">
        <Building2 size={22} /> Plus Contable
      </div>
      <nav className="flex flex-col gap-1 px-3">
        {enlaces.map(({ to, etiqueta, icono: Icono }) => (
          <NavLink key={to} to={to} end
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                isActive ? 'bg-white/15 text-white' : 'hover:bg-white/10'}`}>
            <Icono size={18} /> {etiqueta}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
