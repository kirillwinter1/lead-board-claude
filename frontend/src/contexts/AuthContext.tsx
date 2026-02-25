import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react'
import axios from 'axios'

export interface AuthUser {
  id: number
  accountId: string
  displayName: string
  email: string
  avatarUrl: string | null
  role: string
  permissions: string[]
}

export interface AuthState {
  authenticated: boolean
  user: AuthUser | null
  loading: boolean
}

interface AuthContextValue extends AuthState {
  isAdmin: () => boolean
  isProjectManager: () => boolean
  isTeamLead: () => boolean
  isMember: () => boolean
  isViewer: () => boolean
  hasRole: (...roles: string[]) => boolean
  hasPermission: (permission: string) => boolean
  canEdit: () => boolean
  logout: () => Promise<void>
  refresh: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    authenticated: false,
    user: null,
    loading: true,
  })

  const fetchAuthStatus = useCallback(() => {
    axios.get<{ authenticated: boolean; user: AuthUser | null }>('/oauth/atlassian/status')
      .then(response => {
        setState({
          authenticated: response.data.authenticated,
          user: response.data.user,
          loading: false,
        })
      })
      .catch(() => {
        setState({ authenticated: false, user: null, loading: false })
      })
  }, [])

  useEffect(() => {
    fetchAuthStatus()
  }, [fetchAuthStatus])

  const role = state.user?.role ?? ''

  const isAdmin = useCallback(() => role === 'ADMIN', [role])
  const isProjectManager = useCallback(() => role === 'PROJECT_MANAGER', [role])
  const isTeamLead = useCallback(() => role === 'TEAM_LEAD', [role])
  const isMember = useCallback(() => role === 'MEMBER', [role])
  const isViewer = useCallback(() => role === 'VIEWER', [role])

  const hasRole = useCallback((...roles: string[]) => roles.includes(role), [role])

  const hasPermission = useCallback(
    (permission: string) => state.user?.permissions.includes(permission) ?? false,
    [state.user?.permissions],
  )

  // Can modify data (not VIEWER)
  const canEdit = useCallback(() => role !== 'VIEWER' && role !== '', [role])

  const logout = useCallback(async () => {
    try {
      await axios.post('/oauth/atlassian/logout')
      localStorage.removeItem('setupWizardStep')
      localStorage.removeItem('setupWizardMonths')
      setState({ authenticated: false, user: null, loading: false })
    } catch (err) {
      console.error('Logout failed:', err)
    }
  }, [])

  const value: AuthContextValue = {
    ...state,
    isAdmin,
    isProjectManager,
    isTeamLead,
    isMember,
    isViewer,
    hasRole,
    hasPermission,
    canEdit,
    logout,
    refresh: fetchAuthStatus,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
