import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { SessionsListPage } from './pages/SessionsListPage'
import { SessionDetailPage } from './pages/SessionDetailPage'
import { MobileUploadPage } from './pages/MobileUploadPage'
import { RecherchePage } from './pages/RecherchePage'
import { FilsMemoirePage } from './pages/FilsMemoirePage'
import { EngagementsPage } from './pages/EngagementsPage'
import { LoginPage } from './pages/LoginPage'
import { RouteProtegee } from './components/RouteProtegee'

function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-slate-50">
        <Routes>
          <Route path="/connexion" element={<LoginPage />} />
          {/* Pas de garde d'auth : flux QR code scanne depuis un telephone, sans compte. */}
          <Route path="/mobile/sessions/:id" element={<MobileUploadPage />} />

          <Route path="/" element={<RouteProtegee><SessionsListPage /></RouteProtegee>} />
          <Route path="/sessions/:id" element={<RouteProtegee><SessionDetailPage /></RouteProtegee>} />
          <Route path="/recherche" element={<RouteProtegee><RecherchePage /></RouteProtegee>} />
          <Route path="/fils-memoire" element={<RouteProtegee><FilsMemoirePage /></RouteProtegee>} />
          <Route path="/engagements" element={<RouteProtegee><EngagementsPage /></RouteProtegee>} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
