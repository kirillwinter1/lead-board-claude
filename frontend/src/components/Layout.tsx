import { NavLink, Outlet, useSearchParams } from 'react-router-dom'
import { useEffect, useState, useCallback } from 'react'
import axios from 'axios'
import logo from '../icons/logo.png'
import './Header.css'

interface AuthUser {
  id: number
  accountId: string
  displayName: string
  email: string
  avatarUrl: string | null
}

interface AuthStatus {
  authenticated: boolean
  user: AuthUser | null
}

export function Layout() {
  const [searchParams] = useSearchParams()
  const [authStatus, setAuthStatus] = useState<AuthStatus | null>(null)

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

  return (
    <div className="app">
      <header className="header">
        <div className="header-left">
          <NavLink to="/board" className="logo-link">
            <img src={logo} alt="Lead Board" className="header-logo" />
          </NavLink>
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
          </nav>
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
      <Outlet />
    </div>
  )
}
