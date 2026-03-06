import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { BoardPage } from './pages/BoardPage'
import { TeamsPage } from './pages/TeamsPage'
import { TeamMembersPage } from './pages/TeamMembersPage'
import { TimelinePage } from './pages/TimelinePage'
import { TeamMetricsPage } from './pages/TeamMetricsPage'
import { DataQualityPage } from './pages/DataQualityPage'
import { PlanningPokerPage } from './pages/PlanningPokerPage'
import { PokerRoomPage } from './pages/PokerRoomPage'
import { SettingsPage } from './pages/SettingsPage'
import { WorkflowConfigPage } from './pages/WorkflowConfigPage'
import { BugMetricsPage } from './pages/BugMetricsPage'
import { MemberProfilePage } from './pages/MemberProfilePage'
import { TeamCompetencyPage } from './pages/TeamCompetencyPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { QuarterlyPlanningPage } from './pages/QuarterlyPlanningPage'
import { LandingPage } from './pages/landing/LandingPage'
import RegistrationPage from './pages/RegistrationPage'
import { WorkflowConfigProvider } from './contexts/WorkflowConfigContext'
import { AuthProvider } from './contexts/AuthContext'
import { getTenantSlug } from './utils/tenant'
import './App.css'

function TenantAwareRoot() {
  const slug = getTenantSlug()
  return slug ? <Layout /> : <LandingPage />
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
      <WorkflowConfigProvider>
      <Routes>
        <Route path="/landing" element={<LandingPage />} />
        <Route path="/register" element={<RegistrationPage />} />
        <Route path="/" element={<TenantAwareRoot />}>
          <Route index element={<BoardPage />} />
          <Route path="timeline" element={<TimelinePage />} />
          <Route path="metrics" element={<TeamMetricsPage />} />
          <Route path="data-quality" element={<DataQualityPage />} />
          <Route path="poker" element={<PlanningPokerPage />} />
          <Route path="poker/room/:roomCode" element={<PokerRoomPage />} />
          <Route path="projects" element={<ProjectsPage />} />
          <Route path="quarterly-planning" element={<QuarterlyPlanningPage />} />
          <Route path="teams" element={<TeamsPage />} />
          <Route path="teams/:teamId" element={<TeamMembersPage />} />
          <Route path="teams/:teamId/member/:memberId" element={<MemberProfilePage />} />
          <Route path="teams/:teamId/competency" element={<TeamCompetencyPage />} />
          <Route path="settings" element={<ProtectedRoute roles={['ADMIN']}><SettingsPage /></ProtectedRoute>} />
          <Route path="workflow" element={<ProtectedRoute roles={['ADMIN']}><WorkflowConfigPage /></ProtectedRoute>} />
          <Route path="bug-metrics" element={<BugMetricsPage />} />
        </Route>
      </Routes>
      </WorkflowConfigProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
