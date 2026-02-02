import { useState, useEffect } from 'react'
import { getRoleLoad, RoleLoadResponse, RoleLoadInfo, UtilizationStatus, RoleLoadAlert } from '../../api/forecast'
import './RoleLoadBlock.css'

interface RoleLoadBlockProps {
  teamId: number
}

/**
 * Блок загрузки команды по ролям (SA/DEV/QA).
 * Показывает capacity, assigned hours и utilization для каждой роли.
 */
export function RoleLoadBlock({ teamId }: RoleLoadBlockProps) {
  const [data, setData] = useState<RoleLoadResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    getRoleLoad(teamId)
      .then(response => {
        setData(response)
        setLoading(false)
      })
      .catch(err => {
        setError('Не удалось загрузить данные: ' + err.message)
        setLoading(false)
      })
  }, [teamId])

  if (loading) {
    return (
      <div className="role-load-block">
        <div className="role-load-header">
          <h3>Загрузка по ролям</h3>
        </div>
        <div className="role-load-loading">Загрузка...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="role-load-block">
        <div className="role-load-header">
          <h3>Загрузка по ролям</h3>
        </div>
        <div className="role-load-error">{error}</div>
      </div>
    )
  }

  if (!data) {
    return null
  }

  return (
    <div className="role-load-block">
      <div className="role-load-header">
        <h3>Загрузка по ролям</h3>
        <span className="role-load-period">{data.periodDays} рабочих дней</span>
      </div>

      <div className="role-load-cards">
        <RoleCard role="SA" info={data.sa} />
        <RoleCard role="DEV" info={data.dev} />
        <RoleCard role="QA" info={data.qa} />
      </div>

      {data.alerts.length > 0 && (
        <div className="role-load-alerts">
          {data.alerts.map((alert, index) => (
            <AlertBadge key={index} alert={alert} />
          ))}
        </div>
      )}
    </div>
  )
}

interface RoleCardProps {
  role: 'SA' | 'DEV' | 'QA'
  info: RoleLoadInfo
}

function RoleCard({ role, info }: RoleCardProps) {
  const statusLabel = getStatusLabel(info.status)
  const statusClass = getStatusClass(info.status)
  const barWidth = Math.min(info.utilizationPercent, 150) // Cap at 150% for display

  return (
    <div className={`role-card role-card-${role.toLowerCase()}`}>
      <div className="role-card-title">{role}</div>
      <div className="role-card-members">{info.memberCount} чел.</div>

      <div className="role-card-bar-container">
        <div
          className={`role-card-bar ${info.status === 'OVERLOAD' || info.status === 'NO_CAPACITY' ? statusClass : ''}`}
          style={{ width: `${(barWidth / 150) * 100}%` }}
        />
        <div className="role-card-bar-markers">
          <div className="role-card-bar-marker" style={{ left: '33.33%' }} title="50%" />
          <div className="role-card-bar-marker role-card-bar-marker-limit" style={{ left: '66.66%' }} title="100%" />
        </div>
      </div>

      <div className="role-card-percent">{info.utilizationPercent.toFixed(0)}%</div>
      <div className={`role-card-status ${statusClass}`}>{statusLabel}</div>
      <div className="role-card-hours">
        {info.totalAssignedHours.toFixed(0)}h / {info.totalCapacityHours.toFixed(0)}h
      </div>
    </div>
  )
}

function getStatusLabel(status: UtilizationStatus): string {
  switch (status) {
    case 'OVERLOAD':
      return 'Перегрузка'
    case 'NORMAL':
      return 'Норма'
    case 'IDLE':
      return 'Простой'
    case 'NO_CAPACITY':
      return 'Нет ресурсов'
  }
}

function getStatusClass(status: UtilizationStatus): string {
  switch (status) {
    case 'OVERLOAD':
      return 'status-overload'
    case 'NORMAL':
      return 'status-normal'
    case 'IDLE':
      return 'status-idle'
    case 'NO_CAPACITY':
      return 'status-no-capacity'
  }
}

interface AlertBadgeProps {
  alert: RoleLoadAlert
}

function AlertBadge({ alert }: AlertBadgeProps) {
  const icon = getAlertIcon(alert.type)
  const className = getAlertClass(alert.type)

  return (
    <div className={`role-load-alert ${className}`}>
      <span className="role-load-alert-icon">{icon}</span>
      <span className="role-load-alert-message">{alert.message}</span>
    </div>
  )
}

function getAlertIcon(type: string): string {
  switch (type) {
    case 'ROLE_OVERLOAD':
      return '!'
    case 'ROLE_IDLE':
      return '~'
    case 'IMBALANCE':
      return '~'
    case 'NO_CAPACITY':
      return '!'
    default:
      return '?'
  }
}

function getAlertClass(type: string): string {
  switch (type) {
    case 'ROLE_OVERLOAD':
    case 'NO_CAPACITY':
      return 'alert-error'
    case 'ROLE_IDLE':
    case 'IMBALANCE':
      return 'alert-warning'
    default:
      return 'alert-info'
  }
}
