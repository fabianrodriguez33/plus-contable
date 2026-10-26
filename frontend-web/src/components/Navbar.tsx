import { useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { LogOut, Menu, UserCircle } from 'lucide-react'
import { logout, usuarioActual } from '../services/authService'
import Sidebar, { MenuMovil } from './Sidebar'
import Button from './Button'

/** Layout autenticado: barra lateral (o menú desplegable en móvil) + barra superior + contenido. */
export default function Navbar() {
  const navigate = useNavigate()
  const usuario = usuarioActual()
  const [menuAbierto, setMenuAbierto] = useState(false)

  const salir = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <MenuMovil abierto={menuAbierto} onCerrar={() => setMenuAbierto(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between gap-2 border-b border-slate-200 bg-white px-4 py-3 md:px-6">
          <div className="flex items-center gap-2">
            <button type="button" aria-label="Abrir menú" aria-expanded={menuAbierto} aria-controls="menu-movil"
              onClick={() => setMenuAbierto(true)}
              className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 md:hidden">
              <Menu size={22} />
            </button>
            <span className="text-sm text-slate-500">V&amp;F Asesores Empresarial</span>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <UserCircle size={20} className="hidden text-slate-400 sm:block" />
            <span className="hidden sm:inline">
              {usuario?.nombreCompleto} <span className="text-slate-400">({usuario?.roles.join(', ')})</span>
            </span>
            <Button variant="secondary" onClick={salir} aria-label="Salir"><LogOut size={16} /><span className="hidden sm:inline"> Salir</span></Button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
