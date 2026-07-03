import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { SessionsListPage } from './pages/SessionsListPage'
import { SessionDetailPage } from './pages/SessionDetailPage'

function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-slate-50">
        <Routes>
          <Route path="/" element={<SessionsListPage />} />
          <Route path="/sessions/:id" element={<SessionDetailPage />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
