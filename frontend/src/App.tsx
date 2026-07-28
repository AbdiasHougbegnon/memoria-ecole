import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { SessionsListPage } from './pages/SessionsListPage'
import { SessionDetailPage } from './pages/SessionDetailPage'
import { MobileUploadPage } from './pages/MobileUploadPage'
import { RecherchePage } from './pages/RecherchePage'
import { FilsMemoirePage } from './pages/FilsMemoirePage'
import { EngagementsPage } from './pages/EngagementsPage'
import { LoginPage } from './pages/LoginPage'
import { ChoixModulePage } from './pages/ChoixModulePage'
import { CouloirsPage } from './pages/CouloirsPage'
import { CouloirDetailPage } from './pages/CouloirDetailPage'
import { RejoindreCouloirPage } from './pages/RejoindreCouloirPage'
import { ParametresCompteePage } from './pages/ParametresCompteePage'
import { TableauDeBordPage } from './pages/TableauDeBordPage'
import { MatieresPage } from './pages/MatieresPage'
import { ImporterMatieresPage } from './pages/ImporterMatieresPage'
import { MatiereApercuPage } from './pages/MatiereApercuPage'
import { MatiereDocumentsPage } from './pages/MatiereDocumentsPage'
import { MatiereRevisionPage } from './pages/MatiereRevisionPage'
import { RevisionMatieresPage } from './pages/RevisionMatieresPage'
import { TutoratMatieresPage } from './pages/TutoratMatieresPage'
import { SeanceDetailPage } from './pages/SeanceDetailPage'
import { TuteurVocalPage } from './pages/TuteurVocalPage'
import { AdminPage } from './pages/AdminPage'
import { RouteProtegee } from './components/RouteProtegee'

function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen">
        <Routes>
          <Route path="/choix-module" element={<ChoixModulePage />} />
          <Route path="/connexion" element={<LoginPage />} />
          {/* Pas de garde d'auth : flux QR code scanne depuis un telephone, sans compte. */}
          <Route path="/mobile/sessions/:id" element={<MobileUploadPage />} />

          <Route path="/" element={<RouteProtegee><SessionsListPage /></RouteProtegee>} />
          <Route path="/sessions/:id" element={<RouteProtegee><SessionDetailPage /></RouteProtegee>} />
          <Route path="/recherche" element={<RouteProtegee><RecherchePage /></RouteProtegee>} />
          <Route path="/fils-memoire" element={<RouteProtegee><FilsMemoirePage /></RouteProtegee>} />
          <Route path="/engagements" element={<RouteProtegee module="ENTREPRISE"><EngagementsPage /></RouteProtegee>} />
          <Route path="/couloirs" element={<RouteProtegee><CouloirsPage /></RouteProtegee>} />
          <Route path="/couloirs/:id" element={<RouteProtegee><CouloirDetailPage /></RouteProtegee>} />
          <Route path="/couloirs/:id/rejoindre" element={<RouteProtegee><RejoindreCouloirPage /></RouteProtegee>} />
          <Route path="/parametres" element={<RouteProtegee><ParametresCompteePage /></RouteProtegee>} />
          <Route path="/admin" element={<RouteProtegee adminRequis><AdminPage /></RouteProtegee>} />
          <Route path="/tableau-de-bord" element={<RouteProtegee module="ENTREPRISE"><TableauDeBordPage /></RouteProtegee>} />
          <Route path="/couloirs/:id/matieres" element={<RouteProtegee module="ECOLE"><MatieresPage /></RouteProtegee>} />
          <Route path="/import-matieres" element={<RouteProtegee module="ECOLE"><ImporterMatieresPage /></RouteProtegee>} />
          <Route path="/matieres/:id" element={<RouteProtegee module="ECOLE"><MatiereApercuPage /></RouteProtegee>} />
          <Route path="/matieres/:id/documents" element={<RouteProtegee module="ECOLE"><MatiereDocumentsPage /></RouteProtegee>} />
          <Route path="/matieres/:id/revision" element={<RouteProtegee module="ECOLE"><MatiereRevisionPage /></RouteProtegee>} />
          <Route path="/revision" element={<RouteProtegee module="ECOLE"><RevisionMatieresPage /></RouteProtegee>} />
          <Route path="/tutorat" element={<RouteProtegee module="ECOLE"><TutoratMatieresPage /></RouteProtegee>} />
          <Route path="/seances/:id" element={<RouteProtegee module="ECOLE"><SeanceDetailPage /></RouteProtegee>} />
          <Route path="/tutorat/:seanceTutoratId" element={<RouteProtegee module="ECOLE"><TuteurVocalPage /></RouteProtegee>} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
