import { Navigate, Route, Routes } from 'react-router-dom'
import Navbar from './components/Navbar'
import PrivateRoute from './components/PrivateRoute'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import FichaMaestraPage from './pages/FichaMaestraPage'
import DeclaracionPage from './pages/DeclaracionPage'
import EstadoCuentaPage from './pages/EstadoCuentaPage'
import MorososPage from './pages/MorososPage'
import VencidasPage from './pages/VencidasPage'
import PagoPage from './pages/PagoPage'
import CalendarioPage from './pages/CalendarioPage'
import TableroPage from './pages/TableroPage'
import AlertasPlazoPage from './pages/AlertasPlazoPage'
import CambiarPasswordPage from './pages/CambiarPasswordPage'
import RestablecerPage from './pages/RestablecerPage'
import MiEmpresaPage from './pages/MiEmpresaPage'
import MiEstadoCuentaPage from './pages/MiEstadoCuentaPage'
import MisAvisosPage from './pages/MisAvisosPage'
import MisComprasPage from './pages/MisComprasPage'
import { esCliente } from './services/authService'

/** HU-35: se decide al renderizar la ruta, no al montar la app: así sirve también tras iniciar sesión sin recargar. */
const Inicio = () => (esCliente() ? <Navigate to="/mi-empresa" replace /> : <DashboardPage />)

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cambiar-password" element={<CambiarPasswordPage />} />
      <Route path="/restablecer" element={<RestablecerPage />} />
      <Route element={<PrivateRoute />}>
        <Route element={<Navbar />}>
          <Route path="/" element={<Inicio />} />
          <Route path="/mi-empresa" element={<MiEmpresaPage />} />
          <Route path="/mi-estado-cuenta" element={<MiEstadoCuentaPage />} />
          <Route path="/mis-avisos" element={<MisAvisosPage />} />
          <Route path="/mis-compras" element={<MisComprasPage />} />
          <Route path="/calendario" element={<CalendarioPage />} />
          <Route path="/tablero" element={<TableroPage />} />
          <Route path="/alertas-plazo" element={<AlertasPlazoPage />} />
          <Route path="/estado-cuenta" element={<EstadoCuentaPage />} />
          <Route path="/morosos" element={<MorososPage />} />
          <Route path="/vencidas" element={<VencidasPage />} />
          <Route path="/clientes/nuevo" element={<FichaMaestraPage />} />
          <Route path="/clientes/:clienteId" element={<FichaMaestraPage />} />
          <Route path="/obligaciones/:obligacionId/declarar" element={<DeclaracionPage />} />
          <Route path="/obligaciones/:obligacionId/pago" element={<PagoPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
