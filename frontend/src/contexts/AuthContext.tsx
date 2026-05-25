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
  /**
   * True while the team scope (/api/auth/my-teams) is being fetched. Callers
   * that gate UI on `canManageTeam` must wait for this to be false to avoid
   * flicker: until the fetch completes, `canManageTeam` returns false for
   * non-admin users because the team set is still empty.
   */
  teamScopeLoading: boolean
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
  /**
   * True when the current user is allowed to mutate issues belonging to the
   * given team — mirrors AuthorizationService.canManageTeam on the backend.
   * Sourced from /api/auth/my-teams; for ADMIN the team list is empty but
   * the helper always returns true.
   */
  canManageTeam: (teamId: number | null) => boolean
  logout: () => Promise<void>
  refresh: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    authenticated: false,
    user: null,
    loading: true,
    teamScopeLoading: true,
  })
  // Team scope used by canManageTeam. `admin=true` short-circuits the check;
  // otherwise the caller must be a member of the given team.
  const [teamScope, setTeamScope] = useState<{ admin: boolean; teamIds: Set<number> }>({
    admin: false,
    teamIds: new Set(),
  })

  const fetchAuthStatus = useCallback(() => {
    axios.get<{ authenticated: boolean; user: AuthUser | null }>('/oauth/atlassian/status')
      .then(response => {
        const authenticated = response.data.authenticated
        setState({
          authenticated,
          user: response.data.user,
          loading: false,
          // Keep teamScopeLoading=true only while we're about to fetch /my-teams.
          // For unauthenticated users there is no second fetch, so resolve immediately.
          teamScopeLoading: authenticated,
        })
        if (authenticated) {
          axios.get<{ admin: boolean; teamIds: number[] }>('/api/auth/my-teams')
            .then(r => {
              setTeamScope({ admin: r.data.admin, teamIds: new Set(r.data.teamIds) })
              setState(prev => ({ ...prev, teamScopeLoading: false }))
            })
            .catch(err => {
              console.warn('Failed to load team scope from /api/auth/my-teams:', err)
              setTeamScope({ admin: false, teamIds: new Set() })
              setState(prev => ({ ...prev, teamScopeLoading: false }))
            })
        } else {
          setTeamScope({ admin: false, teamIds: new Set() })
        }
      })
      .catch(() => {
        setState({ authenticated: false, user: null, loading: false, teamScopeLoading: false })
        setTeamScope({ admin: false, teamIds: new Set() })
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

  const canManageTeam = useCallback(
    (teamId: number | null) => {
      // `== null` keeps the historical undefined-safety even though the type
      // now forbids it — defensive against runtime callers passing undefined.
      if (teamId == null) return false
      if (teamScope.admin) return true
      return teamScope.teamIds.has(teamId)
    },
    [teamScope],
  )

  const logout = useCallback(async () => {
    try {
      await axios.post('/oauth/atlassian/logout')
      localStorage.removeItem('setupWizardStep')
      localStorage.removeItem('setupWizardMonths')
      setState({ authenticated: false, user: null, loading: false, teamScopeLoading: false })
      setTeamScope({ admin: false, teamIds: new Set() })
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
    canManageTeam,
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
