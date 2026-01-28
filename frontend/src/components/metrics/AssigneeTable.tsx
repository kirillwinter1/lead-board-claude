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

  return (
    <div className="chart-section">
      <h3>By Team Member</h3>
      <table className="metrics-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Issues Closed</th>
            <th>Avg Lead Time</th>
            <th>Avg Cycle Time</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map(a => (
            <tr key={a.accountId}>
              <td>{a.displayName}</td>
              <td className="metrics-table-number">{a.issuesClosed}</td>
              <td className="metrics-table-number">{a.avgLeadTimeDays.toFixed(1)} days</td>
              <td className="metrics-table-number">{a.avgCycleTimeDays.toFixed(1)} days</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
