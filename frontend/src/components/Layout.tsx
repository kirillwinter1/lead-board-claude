import { NavLink, Outlet, useSearchParams } from 'react-router-dom'
import { useEffect, useState, useCallback } from 'react'
import axios from 'axios'
import logo from '../icons/logo.png'
import { SetupWizardPage } from '../pages/SetupWizardPage'
import './Header.css'

interface AuthUser {
  id: number
  accountId: string
  displayName: string
  email: string
  avatarUrl: string | null
  role: string
  permissions: string[]
}

interface AuthStatus {
  authenticated: boolean
  user: AuthUser | null
}

function isAdmin(user: AuthUser | null | undefined): boolean {
  return user?.role === 'ADMIN'
}

interface SyncStatus {
  syncInProgress: boolean
  lastSyncStartedAt: string | null
  lastSyncCompletedAt: string | null
  issuesCount: number
  error: string | null
}

export function Layout() {
  const [searchParams] = useSearchParams()
  const [authStatus, setAuthStatus] = useState<AuthStatus | null>(null)
  const [setupRequired, setSetupRequired] = useState<boolean | null>(null)

  // Preserve teamId in navigation links
  const teamId = searchParams.get('teamId')
  const queryString = teamId ? `?teamId=${teamId}` : ''

  const fetchAuthStatus = useCallback(() => {
    axios.get<AuthStatus>('/oauth/atlassian/status')
      .then(response => {
        setAuthStatus(response.data)
      })
      .catch(() => {
        setAuthStatus({ authenticated: false, user: null })
      })
  }, [])

  useEffect(() => {
    fetchAuthStatus()
  }, [fetchAuthStatus])

  // Check if initial setup is needed
  useEffect(() => {
    if (authStatus?.authenticated) {
      axios.get<SyncStatus>('/api/sync/status')
        .then(res => {
          setSetupRequired(res.data.lastSyncCompletedAt === null)
        })
        .catch(() => {
          setSetupRequired(false)
        })
    }
  }, [authStatus?.authenticated])

  const handleLogin = () => {
    window.location.href = '/oauth/atlassian/authorize'
  }

  const handleLogout = () => {
    axios.post('/oauth/atlassian/logout')
      .then(() => {
        setAuthStatus({ authenticated: false, user: null })
      })
      .catch(err => {
        console.error('Logout failed:', err)
      })
  }

  const showWizard = authStatus?.authenticated && setupRequired === true
  const showNav = authStatus?.authenticated && !showWizard

  return (
    <div className="app">
      <header className="header">
        <div className="header-left">
          <NavLink to="/board" className="logo-link">
            <img src={logo} alt="OneLane" className="header-logo" />
          </NavLink>
          {showNav && (
            <nav className="nav-tabs">
              <NavLink to={`/board${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`} end>
                Board
              </NavLink>
              <NavLink to={`/board/timeline${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Timeline
              </NavLink>
              <NavLink to={`/board/metrics${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Metrics
              </NavLink>
              <NavLink to={`/board/data-quality${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Data Quality
              </NavLink>
              <NavLink to={`/board/poker${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Poker
              </NavLink>
              <NavLink to={`/board/teams${queryString}`} className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                Teams
              </NavLink>
              {isAdmin(authStatus?.user) && (
                <NavLink to="/board/settings" className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}>
                  Settings
                </NavLink>
              )}
            </nav>
          )}
        </div>
        <div className="header-right">
          <div className="auth-status">
            {authStatus?.authenticated && authStatus.user ? (
              <div className="user-info">
                {authStatus.user.avatarUrl && (
                  <img src={authStatus.user.avatarUrl} alt="" className="user-avatar" />
                )}
                <span className="user-name">{authStatus.user.displayName}</span>
                <button className="btn btn-link btn-logout" onClick={handleLogout}>
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
      {authStatus?.authenticated ? (
        showWizard ? (
          isAdmin(authStatus?.user) ? (
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
      ) : authStatus !== null ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 'calc(100vh - 56px)', gap: '16px' }}>
          <h2 style={{ margin: 0, color: '#333' }}>Log in to continue</h2>
          <p style={{ margin: 0, color: '#666' }}>You need to authenticate with Atlassian to access the board</p>
          <button className="btn btn-secondary" onClick={handleLogin} style={{ fontSize: '16px', padding: '10px 24px' }}>
            Login with Atlassian
          </button>
        </div>
      ) : null}
    </div>
  )
}
