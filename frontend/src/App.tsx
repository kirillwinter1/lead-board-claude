import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { BoardPage } from './pages/BoardPage'
import { TeamsPage } from './pages/TeamsPage'
import { TeamMembersPage } from './pages/TeamMembersPage'
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
import { MatrixPage } from './pages/MatrixPage'
import { GuidePage } from './pages/GuidePage'
import { LandingPage } from './pages/landing/LandingPage'
import RegistrationPage from './pages/RegistrationPage'
import { MyWorkPage } from './pages/MyWorkPage'
import { WorkflowConfigProvider } from './contexts/WorkflowConfigContext'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { getTenantSlug } from './utils/tenant'
import './App.css'

function TenantAwareRoot() {
  const slug = getTenantSlug()
  return slug ? <Layout /> : <LandingPage />
}

function LegacyTimelineRedirect() {
  const location = useLocation()
  const params = new URLSearchParams(location.search)
  params.set('view', 'timeline')
  const search = params.toString()
  return <Navigate to={{ pathname: '/', search: search ? `?${search}` : '' }} replace />
}

// F88: MEMBER-role users land on My Work instead of the Board — the Board's
// epic/story hierarchy isn't relevant to their day-to-day work.
export function HomeRedirect() {
  const auth = useAuth()
  if (auth.loading) return null
  if (auth.isMember()) return <Navigate to="/my-work" replace />
  return <BoardPage />
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
          <Route index element={<HomeRedirect />} />
          <Route path="my-work" element={<MyWorkPage />} />
          <Route path="timeline" element={<LegacyTimelineRedirect />} />
          <Route path="metrics" element={<TeamMetricsPage />} />
          <Route path="data-quality" element={<DataQualityPage />} />
          <Route path="poker" element={<PlanningPokerPage />} />
          <Route path="poker/:epicKey" element={<PokerRoomPage />} />
          <Route path="projects" element={<ProjectsPage />} />
          <Route path="quarterly-planning" element={<QuarterlyPlanningPage />} />
          <Route path="matrix" element={<MatrixPage />} />
          <Route path="guide" element={<GuidePage />} />
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
