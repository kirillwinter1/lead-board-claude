/**
 * Demo version of RoleLoadBlock for landing page
 * Static data matching the screenshot
 */

type UtilizationStatus = 'NORMAL' | 'OVERLOAD' | 'IDLE'

interface RoleInfo {
  memberCount: number
  utilizationPercent: number
  status: UtilizationStatus
  assignedHours: number
  capacityHours: number
}

interface Alert {
  type: 'ROLE_OVERLOAD' | 'ROLE_IDLE' | 'IMBALANCE'
  message: string
}

const mockData = {
  periodDays: 30,
  sa: {
    memberCount: 2,
    utilizationPercent: 64,
    status: 'NORMAL' as UtilizationStatus,
    assignedHours: 238,
    capacityHours: 372
  },
  dev: {
    memberCount: 1,
    utilizationPercent: 100,
    status: 'OVERLOAD' as UtilizationStatus,
    assignedHours: 187,
    capacityHours: 186
  },
  qa: {
    memberCount: 3,
    utilizationPercent: 34,
    status: 'IDLE' as UtilizationStatus,
    assignedHours: 192,
    capacityHours: 567
  },
  alerts: [
    { type: 'ROLE_OVERLOAD' as const, message: 'DEV перегружены: 100.4%' },
    { type: 'ROLE_IDLE' as const, message: 'QA недозагружены: 33.6%' },
    { type: 'IMBALANCE' as const, message: 'Дисбаланс нагрузки: DEV (100%) vs QA (34%)' }
  ]
}

function RoleCard({ role, info }: { role: 'SA' | 'DEV' | 'QA'; info: RoleInfo }) {
  const statusLabel = {
    NORMAL: 'Норма',
    OVERLOAD: 'Перегрузка',
    IDLE: 'Простой'
  }[info.status]

  const statusClass = {
    NORMAL: 'status-normal',
    OVERLOAD: 'status-overload',
    IDLE: 'status-idle'
  }[info.status]

  const barWidth = Math.min(info.utilizationPercent, 150)

  return (
    <div className={`demo-role-load-card demo-role-load-card-${role.toLowerCase()}`}>
      <div className="demo-role-load-title">{role}</div>
      <div className="demo-role-load-members">{info.memberCount} чел.</div>

      <div className="demo-role-load-bar-container">
        <div
          className={`demo-role-load-bar ${info.status === 'OVERLOAD' ? 'bar-overload' : ''}`}
          style={{ '--fill-width': `${(barWidth / 150) * 100}%` } as React.CSSProperties}
        />
        <div className="demo-role-load-bar-markers">
          <div className="demo-role-load-bar-marker" style={{ left: '33.33%' }} />
          <div className="demo-role-load-bar-marker demo-role-load-bar-marker-limit" style={{ left: '66.66%' }} />
        </div>
      </div>

      <div className="demo-role-load-percent">{info.utilizationPercent}%</div>
      <div className={`demo-role-load-status ${statusClass}`}>{statusLabel}</div>
      <div className="demo-role-load-hours">
        {info.assignedHours}h / {info.capacityHours}h
      </div>
    </div>
  )
}

function AlertBadge({ alert }: { alert: Alert }) {
  const icon = alert.type === 'ROLE_OVERLOAD' ? '!' : '~'
  const className = alert.type === 'ROLE_OVERLOAD' ? 'alert-error' : 'alert-warning'

  return (
    <div className={`demo-role-load-alert ${className}`}>
      <span className="demo-role-load-alert-icon">{icon}</span>
      <span className="demo-role-load-alert-message">{alert.message}</span>
    </div>
  )
}

export function DemoRoleLoad() {
  return (
    <div className="demo-role-load-block">
      <div className="demo-role-load-header">
        <h3>Загрузка по ролям</h3>
        <span className="demo-role-load-period">{mockData.periodDays} рабочих дней</span>
      </div>

      <div className="demo-role-load-cards">
        <RoleCard role="SA" info={mockData.sa} />
        <RoleCard role="DEV" info={mockData.dev} />
        <RoleCard role="QA" info={mockData.qa} />
      </div>

      <div className="demo-role-load-alerts">
        {mockData.alerts.map((alert, index) => (
          <AlertBadge key={index} alert={alert} />
        ))}
      </div>
    </div>
  )
}
