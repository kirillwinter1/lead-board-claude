import { NavLink, Outlet, useSearchParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import axios from 'axios'
import logo from '../icons/logo.png'
import { useAuth } from '../contexts/AuthContext'
import { SetupWizardPage } from '../pages/SetupWizardPage'
import { getTenantSlug, setTenantSlug } from '../utils/tenant'
import { ChatWidget } from './chat/ChatWidget'
import './Header.css'

declare const __APP_VERSION__: string

interface SyncStatus {
  syncInProgress: boolean
  lastSyncStartedAt: string | null
  lastSyncCompletedAt: string | null
  issuesCount: number
  error: string | null
}

export function Layout() {
  const [searchParams] = useSearchParams()
  const auth = useAuth()
  const [setupRequired, setSetupRequired] = useState<boolean | null>(null)

  // Preserve teamId in navigation links
  const teamId = searchParams.get('teamId')
  const queryString = teamId ? `?teamId=${teamId}` : ''

  const [tenantError, setTenantError] = useState(false)

  // Check if initial setup is needed
  useEffect(() => {
    if (auth.authenticated) {
      axios.get<SyncStatus & { setupCompleted?: boolean }>('/api/sync/status')
        .then(res => {
          setSetupRequired(!res.data.setupCompleted)
        })
        .catch((err) => {
          if (err?.response?.status === 404 && err?.response?.data?.error === 'Not Found') {
            // Tenant not found - all API calls will fail
            setTenantError(true)
          }
          setSetupRequired(false)
        })
    }
  }, [auth.authenticated])

  const handleLogin = () => {
    const slug = getTenantSlug()
    const params = slug ? `?tenant=${encodeURIComponent(slug)}` : ''
    window.location.href = `/oauth/atlassian/authorize${params}`
  }

  const showWizard = auth.authenticated && setupRequired === true
  const showNav = auth.authenticated && !showWizard

  return (
    <div className="app">
      <header className="header">
        <div className="header-left">
          <NavLink to="/" className="logo-link">
            <img src={logo} alt="OneLane" className="header-logo" />
          </NavLink>
          {showNav && (
            <nav className="nav-tabs">
              <NavLink to={`/${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`} end>
                Board
              </NavLink>
              <NavLink to={`/timeline${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Timeline
              </NavLink>
              <NavLink to={`/metrics${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Metrics
              </NavLink>
              <NavLink to={`/data-quality${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Data Quality
              </NavLink>
              <NavLink to={`/bug-metrics${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Bugs
              </NavLink>
              {auth.hasPermission('poker:participate') && (
                <NavLink to={`/poker${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                  Poker
                </NavLink>
              )}
              <NavLink to={`/teams${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Teams
              </NavLink>
              <NavLink to={`/projects${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Projects
              </NavLink>
              <NavLink to={`/quarterly-planning${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Planning
              </NavLink>
              {auth.isAdmin() && (
                <NavLink to="/settings" className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                  Settings
                </NavLink>
              )}
            </nav>
          )}
        </div>
        <div className="header-right">
          <div className="auth-status">
            {auth.authenticated && auth.user ? (
              <div className="user-info">
                {auth.user.avatarUrl && (
                  <img src={auth.user.avatarUrl} alt="" className="user-avatar" />
                )}
                <span className="user-name">{auth.user.displayName}</span>
                <button className="btn btn-link btn-logout" onClick={auth.logout}>
                  Logout
                </button>
              </div>
            ) : (
              <button className="btn btn-secondary" onClick={handleLogin}>
                Login with Atlassian
              </button>
            )}
          </div>
        </div>
      </header>
      {auth.authenticated ? (
        tenantError ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 'calc(100vh - 56px)', gap: '16px', padding: '24px' }}>
            <h2 style={{ margin: 0, color: '#333' }}>Tenant not found</h2>
            <p style={{ margin: 0, color: '#666', textAlign: 'center' }}>
              The tenant <strong>"{getTenantSlug()}"</strong> does not exist.
              Please check your tenant slug or register a new workspace.
            </p>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="btn btn-secondary" onClick={() => {
                setTenantSlug(null)
                window.location.href = '/landing'
              }}>
                Go to Landing Page
              </button>
            </div>
          </div>
        ) : showWizard ? (
          auth.isAdmin() ? (
            <SetupWizardPage onComplete={() => setSetupRequired(false)} />
          ) : (
            <div className="setup-waiting">
              <h2>Initial Setup Required</h2>
              <p>Waiting for an admin to complete the initial project setup.</p>
            </div>
          )
        ) : setupRequired === false ? (
          <Outlet />
        ) : null
      ) : auth.loading ? null : (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 'calc(100vh - 56px)', gap: '16px' }}>
          <h2 style={{ margin: 0, color: '#333' }}>Log in to continue</h2>
          <p style={{ margin: 0, color: '#666' }}>You need to authenticate with Atlassian to access the board</p>
          <button className="btn btn-secondary" onClick={handleLogin} style={{ fontSize: '16px', padding: '10px 24px' }}>
            Login with Atlassian
          </button>
        </div>
      )}
      {auth.authenticated && <ChatWidget />}
      <div style={{ position: 'fixed', bottom: '8px', right: '12px', fontSize: '11px', color: '#aaa', pointerEvents: 'none', zIndex: 1 }}>
        v{__APP_VERSION__}
      </div>
    </div>
  )
}
