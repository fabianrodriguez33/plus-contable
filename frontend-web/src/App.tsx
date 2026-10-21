import { Link, Outlet, Route, Routes, useParams } from 'react-router-dom'
import TrabajadoresPage from './pages/TrabajadoresPage'
import DocumentosPage from './pages/DocumentosPage'
import AcuerdoPage from './pages/AcuerdoPage'
import DeclaracionPage from './pages/DeclaracionPage'
import VencimientosPage from './pages/VencimientosPage'

// Rutas de las historias de Jeanpierre. El login y la ficha maestra (HU-36, HU-01) viven en otras ramas.
function LayoutCliente() {
  const { clienteId } = useParams()
  return (
    <>
      <nav className="flex flex-wrap gap-4 p-4 border-b">
        <Link to={`/clientes/${clienteId}/trabajadores`}>Trabajadores</Link>
        <Link to={`/clientes/${clienteId}/documentos`}>Expediente</Link>
        <Link to={`/clientes/${clienteId}/acuerdo`}>Acuerdo</Link>
      </nav>
      <main className="p-4">
        <Outlet />
      </main>
    </>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/clientes/:clienteId" element={<LayoutCliente />}>
        <Route path="trabajadores" element={<TrabajadoresPage />} />
        <Route path="documentos" element={<DocumentosPage />} />
        <Route path="acuerdo" element={<AcuerdoPage />} />
      </Route>
      <Route path="/obligaciones/:obligacionId/declarar" element={<DeclaracionPage />} />
      <Route path="/obligaciones/vencimientos" element={<VencimientosPage />} />
      <Route path="*" element={<p className="p-4">Abra /clientes/1/trabajadores</p>} />
    </Routes>
  )
}
