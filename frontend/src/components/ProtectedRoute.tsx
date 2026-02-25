import { Navigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

interface ProtectedRouteProps {
  children: React.ReactNode
  roles?: string[]
}

export function ProtectedRoute({ children, roles }: ProtectedRouteProps) {
  const { authenticated, loading, user } = useAuth()

  if (loading) {
    return null
  }

  if (!authenticated) {
    return <Navigate to="/landing" replace />
  }

  if (roles && roles.length > 0 && user) {
    if (!roles.includes(user.role)) {
      return <Navigate to="/" replace />
    }
  }

  return <>{children}</>
}
