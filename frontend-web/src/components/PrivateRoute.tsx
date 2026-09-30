import { Navigate, Outlet } from 'react-router-dom'
import { isAuthenticated } from '../services/authService'

export default function PrivateRoute() {
  return isAuthenticated() ? <Outlet /> : <Navigate to="/login" replace />
}
