import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { BoardPage } from './pages/BoardPage'
import { TeamsPage } from './pages/TeamsPage'
import { TeamMembersPage } from './pages/TeamMembersPage'
import { TimelinePage } from './pages/TimelinePage'
import { TeamMetricsPage } from './pages/TeamMetricsPage'
import { DataQualityPage } from './pages/DataQualityPage'
import { PlanningPokerPage } from './pages/PlanningPokerPage'
import { PokerRoomPage } from './pages/PokerRoomPage'
import { LandingPage } from './pages/landing/LandingPage'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/landing" element={<LandingPage />} />
        <Route path="/board" element={<Layout />}>
          <Route index element={<BoardPage />} />
          <Route path="timeline" element={<TimelinePage />} />
          <Route path="metrics" element={<TeamMetricsPage />} />
          <Route path="data-quality" element={<DataQualityPage />} />
          <Route path="poker" element={<PlanningPokerPage />} />
          <Route path="poker/room/:roomCode" element={<PokerRoomPage />} />
          <Route path="teams" element={<TeamsPage />} />
          <Route path="teams/:teamId" element={<TeamMembersPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
