import { Navigate, Route, Routes } from 'react-router-dom'
import Navbar from './components/Navbar'
import PrivateRoute from './components/PrivateRoute'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import FichaMaestraPage from './pages/FichaMaestraPage'
import DeclaracionPage from './pages/DeclaracionPage'
import PagoPage from './pages/PagoPage'
import CalendarioPage from './pages/CalendarioPage'
import CambiarPasswordPage from './pages/CambiarPasswordPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cambiar-password" element={<CambiarPasswordPage />} />
      <Route element={<PrivateRoute />}>
        <Route element={<Navbar />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/calendario" element={<CalendarioPage />} />
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
