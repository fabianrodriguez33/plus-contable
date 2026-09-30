import { Navigate, Route, Routes } from 'react-router-dom'
import Navbar from './components/Navbar'
import PrivateRoute from './components/PrivateRoute'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import FichaMaestraPage from './pages/FichaMaestraPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<PrivateRoute />}>
        <Route element={<Navbar />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/clientes/nuevo" element={<FichaMaestraPage />} />
          <Route path="/clientes/:id" element={<FichaMaestraPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
