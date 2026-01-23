import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { BoardPage } from './pages/BoardPage'
import { TeamsPage } from './pages/TeamsPage'
import { TeamMembersPage } from './pages/TeamMembersPage'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<BoardPage />} />
          <Route path="teams" element={<TeamsPage />} />
          <Route path="teams/:teamId" element={<TeamMembersPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
