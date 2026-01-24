import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { BoardPage } from './pages/BoardPage'
import { TeamsPage } from './pages/TeamsPage'
import { TeamMembersPage } from './pages/TeamMembersPage'
import { TimelinePage } from './pages/TimelinePage'
import { TeamMetricsPage } from './pages/TeamMetricsPage'
import { DataQualityPage } from './pages/DataQualityPage'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<BoardPage />} />
          <Route path="timeline" element={<TimelinePage />} />
          <Route path="metrics" element={<TeamMetricsPage />} />
          <Route path="data-quality" element={<DataQualityPage />} />
          <Route path="teams" element={<TeamsPage />} />
          <Route path="teams/:teamId" element={<TeamMembersPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
