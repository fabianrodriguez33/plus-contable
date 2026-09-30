import { Link, Outlet, useNavigate } from 'react-router-dom'
import { logout, usuarioActual } from '../services/authService'

export default function Navbar() {
  const navigate = useNavigate()
  const usuario = usuarioActual()

  const salir = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <nav className="navbar">
        <div>
          <Link to="/" className="brand">Plus Contable</Link>
          <Link to="/">Clientes</Link>
          <Link to="/clientes/nuevo">Nuevo cliente</Link>
        </div>
        <div>
          <span style={{ marginRight: 12 }}>
            {usuario?.nombreCompleto} ({usuario?.roles.join(', ')})
          </span>
          <button className="secondary" onClick={salir}>Salir</button>
        </div>
      </nav>
      <div className="container">
        <Outlet />
      </div>
    </>
  )
}
