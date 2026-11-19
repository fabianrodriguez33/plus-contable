import { useEffect } from 'react'
import { NavLink } from 'react-router-dom'
import { esCliente } from '../services/authService'
import { AlertOctagon, Bell, Briefcase, CalendarDays, FileText, LayoutDashboard, LayoutGrid, ShoppingCart, TriangleAlert, UserPlus, X } from 'lucide-react'

export const ENLACES = [
  { to: '/', etiqueta: 'Clientes', icono: LayoutDashboard },
  { to: '/calendario', etiqueta: 'Calendario', icono: CalendarDays },
  { to: '/tablero', etiqueta: 'Tablero', icono: LayoutGrid },
  { to: '/alertas-plazo', etiqueta: 'Alertas de plazo', icono: Bell },
  { to: '/estado-cuenta', etiqueta: 'Estado de cuenta', icono: FileText },
  { to: '/morosos', etiqueta: 'Clientes morosos', icono: TriangleAlert },
  { to: '/vencidas', etiqueta: 'Obligaciones vencidas', icono: AlertOctagon },
  { to: '/clientes/nuevo', etiqueta: 'Nuevo cliente', icono: UserPlus },
]

/** HU-35: la cuenta de un cliente solo ve su empresa; el resto del menú es del estudio. */
const ENLACES_CLIENTE = [
  { to: '/mi-empresa', etiqueta: 'Mi empresa', icono: Briefcase },
  { to: '/mi-estado-cuenta', etiqueta: 'Mi estado de cuenta', icono: FileText },
  { to: '/mis-avisos', etiqueta: 'Avisos de pago', icono: Bell },
  { to: '/mis-compras', etiqueta: 'Mis compras', icono: ShoppingCart },
]

function Enlaces({ onNavegar }: { onNavegar?: () => void }) {
  return (
    <nav aria-label="Principal" className="flex flex-col gap-0.5 px-3">
      {(esCliente() ? ENLACES_CLIENTE : ENLACES).map(({ to, etiqueta, icono: Icono }) => (
        <NavLink key={to} to={to} end onClick={onNavegar}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-campo border-l-[3px] px-3 py-2 text-sm transition-colors ${
              isActive
                ? 'border-acento bg-brand-50 font-semibold text-acento-oscuro'
                : 'border-transparent text-slate-700 hover:bg-slate-100'}`}>
          <Icono size={17} aria-hidden="true" /> {etiqueta}
        </NavLink>
      ))}
    </nav>
  )
}

/** La marca: un sello de goma con las iniciales y el nombre en serif. */
const Marca = () => (
  <div className="flex items-center gap-3 px-5 py-5">
    <span aria-hidden="true" className="sello !min-w-0 bg-white px-1.5 py-1 font-serif text-sm font-bold text-acento">PC</span>
    <span className="font-serif text-lg font-semibold leading-tight text-tinta">Plus Contable</span>
  </div>
)

/** Barra lateral fija en pantallas medianas y grandes. */
export default function Sidebar() {
  return (
    <aside className="hidden w-60 shrink-0 flex-col border-r border-tinta/15 bg-white md:flex">
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
      <div className="absolute inset-0 bg-tinta/50" onClick={onCerrar} aria-hidden="true" />
      <div id="menu-movil" role="dialog" aria-modal="true" aria-label="Menú de navegación"
        className="absolute inset-y-0 left-0 flex w-64 flex-col bg-white shadow-xl">
        <div className="flex items-center justify-between pr-3">
          <Marca />
          <button type="button" aria-label="Cerrar menú" onClick={onCerrar}
            className="rounded-campo p-1 text-slate-600 hover:bg-slate-100"><X size={20} /></button>
        </div>
        <Enlaces onNavegar={onCerrar} />
      </div>
    </div>
  )
}
