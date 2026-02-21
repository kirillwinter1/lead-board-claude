import { AssigneeMetrics } from '../../api/metrics'
import './AssigneeTable.css'

interface AssigneeTableProps {
  data: AssigneeMetrics[]
}

export function AssigneeTable({ data }: AssigneeTableProps) {
  if (data.length === 0) {
    return (
      <div className="chart-section">
        <h3>By Team Member</h3>
        <div className="chart-empty">No assignee data available</div>
      </div>
    )
  }

  const sorted = [...data].sort((a, b) => b.issuesClosed - a.issuesClosed)

  const getDsrColor = (dsr: number | null) => {
    if (dsr === null) return '#6b778c'
    if (dsr <= 1.0) return '#36B37E'
    if (dsr <= 1.2) return '#FFAB00'
    return '#FF5630'
  }

  const getVelocityColor = (velocity: number | null) => {
    if (velocity === null) return '#6b778c'
    if (velocity >= 90 && velocity <= 110) return '#36B37E'
    if (velocity >= 70 && velocity <= 130) return '#FFAB00'
    return '#FF5630'
  }

  const getTrendIcon = (trend: string | null) => {
    switch (trend) {
      case 'UP':
        return <span className="trend-icon trend-up" title="Improving">+</span>
      case 'DOWN':
        return <span className="trend-icon trend-down" title="Declining">-</span>
      case 'STABLE':
      default:
        return <span className="trend-icon trend-stable" title="Stable">=</span>
    }
  }

  return (
    <div className="chart-section">
      <h3>By Team Member</h3>
      <table className="metrics-table">
        <thead>
          <tr>
            <th>Name</th>
            <th title="Number of issues completed in the selected period">Issues Closed</th>
            <th title="Average time from creation to completion">Avg Lead Time</th>
            <th title="Average time from start to completion">Avg Cycle Time</th>
            <th title="Personal Delivery Speed Ratio (time spent / estimate). 1.0 = on target">DSR</th>
            <th title="Time spent as % of estimated time. 100% = on target">Velocity</th>
            <th title="Performance trend compared to previous period">Trend</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map(a => (
            <tr key={a.accountId}>
              <td>{a.displayName}</td>
              <td className="metrics-table-number">{a.issuesClosed}</td>
              <td className="metrics-table-number">{(a.avgLeadTimeDays ?? 0).toFixed(1)} days</td>
              <td className="metrics-table-number">{(a.avgCycleTimeDays ?? 0).toFixed(1)} days</td>
              <td className="metrics-table-number">
                {a.personalDsr !== null ? (
                  <span style={{ color: getDsrColor(a.personalDsr) }}>
                    {a.personalDsr.toFixed(2)}
                  </span>
                ) : (
                  <span className="metrics-na">-</span>
                )}
              </td>
              <td className="metrics-table-number">
                {a.velocityPercent !== null ? (
                  <span style={{ color: getVelocityColor(a.velocityPercent) }}>
                    {a.velocityPercent.toFixed(0)}%
                  </span>
                ) : (
                  <span className="metrics-na">-</span>
                )}
              </td>
              <td className="metrics-table-center">
                {getTrendIcon(a.trend)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="assignee-table-legend">
        <span className="assignee-legend-item">
          <strong>DSR</strong>: ≤1.0 <span style={{ color: '#36B37E' }}>good</span>,
          ≤1.2 <span style={{ color: '#FFAB00' }}>ok</span>,
          &gt;1.2 <span style={{ color: '#FF5630' }}>slow</span>
        </span>
        <span className="assignee-legend-item">
          <strong>Velocity</strong>: 90-110% <span style={{ color: '#36B37E' }}>on target</span>
        </span>
        <span className="assignee-legend-item">
          <strong>Trend</strong>:
          <span className="trend-icon trend-up">+</span> improving,
          <span className="trend-icon trend-stable">=</span> stable,
          <span className="trend-icon trend-down">-</span> declining
        </span>
      </div>
    </div>
  )
}
