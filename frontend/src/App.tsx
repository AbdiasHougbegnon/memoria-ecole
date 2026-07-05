import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { SessionsListPage } from './pages/SessionsListPage'
import { SessionDetailPage } from './pages/SessionDetailPage'
import { MobileUploadPage } from './pages/MobileUploadPage'

function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-slate-50">
        <Routes>
          <Route path="/" element={<SessionsListPage />} />
          <Route path="/sessions/:id" element={<SessionDetailPage />} />
          <Route path="/mobile/sessions/:id" element={<MobileUploadPage />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
