import { Outlet, useNavigate } from 'react-router-dom'
import { LogOut, UserCircle } from 'lucide-react'
import { logout, usuarioActual } from '../services/authService'
import Sidebar from './Sidebar'
import Button from './Button'

/** Layout autenticado: barra lateral + barra superior + contenido. */
export default function Navbar() {
  const navigate = useNavigate()
  const usuario = usuarioActual()

  const salir = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
          <span className="text-sm text-slate-500">V&amp;F Asesores Empresarial</span>
          <div className="flex items-center gap-3 text-sm">
            <UserCircle size={20} className="text-slate-400" />
            <span>{usuario?.nombreCompleto} <span className="text-slate-400">({usuario?.roles.join(', ')})</span></span>
            <Button variant="secondary" onClick={salir}><LogOut size={16} /> Salir</Button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
