import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import './SettingsPage.css'

interface User {
  id: number
  accountId: string
  displayName: string
  email: string
  avatarUrl: string | null
  role: string
}

const ROLES = ['ADMIN', 'TEAM_LEAD', 'MEMBER', 'VIEWER'] as const

export function SettingsPage() {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [updating, setUpdating] = useState<number | null>(null)

  useEffect(() => {
    fetchUsers()
  }, [])

  const fetchUsers = async () => {
    try {
      setLoading(true)
      const response = await axios.get<User[]>('/api/admin/users')
      setUsers(response.data)
      setError(null)
    } catch (err) {
      setError('Failed to load users')
      console.error('Failed to fetch users:', err)
    } finally {
      setLoading(false)
    }
  }

  const updateRole = async (userId: number, newRole: string) => {
    try {
      setUpdating(userId)
      const response = await axios.patch<User>(`/api/admin/users/${userId}/role`, {
        role: newRole
      })
      setUsers(users.map(u => u.id === userId ? response.data : u))
    } catch (err) {
      console.error('Failed to update role:', err)
      alert('Failed to update role')
    } finally {
      setUpdating(null)
    }
  }

  if (loading) {
    return (
      <div className="settings-page">
        <div className="settings-loading">Loading users...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="settings-page">
        <div className="settings-error">{error}</div>
      </div>
    )
  }

  return (
    <div className="settings-page">
      <h1 className="settings-title">Settings</h1>

      <section className="settings-section">
        <h2 className="settings-section-title">User Management</h2>
        <p className="settings-section-description">
          Manage user roles and permissions. Changes take effect immediately.
        </p>

        <table className="users-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Email</th>
              <th>Role</th>
            </tr>
          </thead>
          <tbody>
            {users.map(user => (
              <tr key={user.id}>
                <td className="user-cell">
                  {user.avatarUrl && (
                    <img
                      src={user.avatarUrl}
                      alt=""
                      className="user-avatar-small"
                    />
                  )}
                  <span className="user-name">{user.displayName || 'Unknown'}</span>
                </td>
                <td className="email-cell">{user.email || '-'}</td>
                <td className="role-cell">
                  <select
                    value={user.role}
                    onChange={(e) => updateRole(user.id, e.target.value)}
                    disabled={updating === user.id}
                    className="role-select"
                  >
                    {ROLES.map(role => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>
                  {updating === user.id && (
                    <span className="updating-indicator">...</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {users.length === 0 && (
          <div className="no-users">No users found</div>
        )}
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Workflow Configuration</h2>
        <p className="settings-section-description">
          Configure issue types, statuses, roles and link type mappings.
        </p>
        <Link to="/board/workflow" className="role-select" style={{ display: 'inline-block', textDecoration: 'none', textAlign: 'center', padding: '8px 16px', background: '#F4F5F7', borderRadius: 4, color: '#172B4D', fontWeight: 500 }}>
          Open Workflow Configuration
        </Link>
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Role Permissions</h2>
        <div className="permissions-table-wrapper">
          <table className="permissions-table">
            <thead>
              <tr>
                <th>Permission</th>
                <th>Admin</th>
                <th>Team Lead</th>
                <th>Member</th>
                <th>Viewer</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>View Board/Timeline</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
              </tr>
              <tr>
                <td>Planning Poker</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Manage Teams</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-partial">Own team</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Edit Priorities</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Sync Trigger</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Settings/Admin</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
