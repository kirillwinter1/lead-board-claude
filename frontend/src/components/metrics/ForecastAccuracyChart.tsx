import { ForecastAccuracyResponse, EpicAccuracy } from '../../api/metrics'
import { getAccuracyColor, DSR_GREEN, DSR_YELLOW, DSR_RED, PROGRESS_IN_PROGRESS, TEXT_MUTED } from '../../constants/colors'
import './ForecastAccuracyChart.css'

interface ForecastAccuracyChartProps {
  data: ForecastAccuracyResponse
  jiraBaseUrl?: string
}

export function ForecastAccuracyChart({ data, jiraBaseUrl = '' }: ForecastAccuracyChartProps) {
  if (data.totalCompleted === 0) {
    return (
      <div className="chart-section">
        <h3>Forecast Accuracy</h3>
        <div className="chart-empty">
          No completed epics for the selected period.
          <br />
          <small style={{ color: TEXT_MUTED }}>
            Forecast accuracy requires epics with completion date (done_at) and forecast snapshots.
          </small>
        </div>
      </div>
    )
  }

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '—'
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'EARLY': return 'Early'
      case 'ON_TIME': return 'On Time'
      case 'LATE': return 'Late'
      default: return status
    }
  }

  const formatEstimate = (epic: EpicAccuracy) => {
    const initial = epic.initialEstimateHours
    const developing = epic.developingEstimateHours
    if (!initial && !developing) return '—'
    if (initial === developing || !developing) return `${initial}h`
    return `${initial}h → ${developing}h`
  }

  const getEstimateClass = (epic: EpicAccuracy) => {
    const initial = epic.initialEstimateHours
    const developing = epic.developingEstimateHours
    if (!initial || !developing || initial === developing) return 'estimate-same'
    return developing > initial ? 'estimate-up' : 'estimate-down'
  }

  return (
    <div className="chart-section">
      <div className="accuracy-header">
        <h3>Forecast Accuracy</h3>
        <p className="accuracy-description">
          How accurately the team hits planned deadlines for epics.
          Compares forecast dates from planning with actual completion dates.
        </p>
      </div>

      {/* Summary Cards */}
      <div className="accuracy-summary">
        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: getAccuracyColor(data.avgAccuracyRatio) }}>
            {data.avgAccuracyRatio.toFixed(2)}
          </div>
          <div className="accuracy-label">
            Accuracy Ratio
            <span className="accuracy-hint">planned days / actual days (&gt; 1 = faster than planned)</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: data.onTimeDeliveryRate >= 80 ? DSR_GREEN : data.onTimeDeliveryRate >= 50 ? DSR_YELLOW : DSR_RED }}>
            {data.onTimeDeliveryRate.toFixed(0)}%
          </div>
          <div className="accuracy-label">
            On-Time Delivery
            <span className="accuracy-hint">% of epics completed on time or early</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: data.avgScheduleVariance <= 0 ? DSR_GREEN : data.avgScheduleVariance <= 5 ? DSR_YELLOW : DSR_RED }}>
            {data.avgScheduleVariance > 0 ? '+' : ''}{data.avgScheduleVariance.toFixed(1)}d
          </div>
          <div className="accuracy-label">
            Schedule Variance
            <span className="accuracy-hint">average deviation in days (− early, + late)</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-breakdown">
            <span style={{ color: DSR_GREEN }}>{data.earlyCount}</span>
            <span style={{ color: TEXT_MUTED }}> / </span>
            <span style={{ color: PROGRESS_IN_PROGRESS }}>{data.onTimeCount}</span>
            <span style={{ color: TEXT_MUTED }}> / </span>
            <span style={{ color: DSR_RED }}>{data.lateCount}</span>
          </div>
          <div className="accuracy-label">
            Early / On Time / Late
            <span className="accuracy-hint">of {data.totalCompleted} epics</span>
          </div>
        </div>
      </div>

      {/* Epic breakdown table */}
      {data.epics.length > 0 && (
        <div className="accuracy-table-container">
          <table className="metrics-table accuracy-table">
            <thead>
              <tr>
                <th>Epic</th>
                <th title="Planned start and end dates from forecast">Planned</th>
                <th title="Actual start and completion dates">Actual</th>
                <th title="Working days planned">Plan Days</th>
                <th title="Working days actual">Actual Days</th>
                <th title="Estimate change: at creation → at development start">Estimate</th>
                <th title="Planned days / Actual days. 1.0 = perfect accuracy">Ratio</th>
                <th title="Difference in days: actual minus planned. Minus = ahead of schedule">Variance</th>
                <th title="Ahead of schedule / On time / Late">Status</th>
              </tr>
            </thead>
            <tbody>
              {data.epics.map((epic: EpicAccuracy) => (
                <tr key={epic.epicKey}>
                  <td>
                    <div className="epic-cell">
                      <a
                        href={`${jiraBaseUrl}${epic.epicKey}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="issue-key"
                      >
                        {epic.epicKey}
                      </a>
                      <span className="epic-summary" title={epic.summary}>
                        {epic.summary.length > 40 ? epic.summary.substring(0, 40) + '...' : epic.summary}
                      </span>
                    </div>
                  </td>
                  <td className="date-cell">
                    {formatDate(epic.plannedStart)} → {formatDate(epic.plannedEnd)}
                  </td>
                  <td className="date-cell">
                    {formatDate(epic.actualStart)} → {formatDate(epic.actualEnd)}
                  </td>
                  <td className="number-cell">{epic.plannedDays}</td>
                  <td className="number-cell">{epic.actualDays}</td>
                  <td className="number-cell">
                    <span className={`estimate-pill ${getEstimateClass(epic)}`}>
                      {formatEstimate(epic)}
                    </span>
                  </td>
                  <td className="number-cell">
                    <div className="ratio-cell">
                      <span style={{ color: getAccuracyColor(epic.accuracyRatio) }}>
                        {epic.accuracyRatio.toFixed(2)}
                      </span>
                      <div className="ratio-gauge">
                        <div
                          className="ratio-gauge-fill"
                          style={{
                            width: `${Math.min(epic.accuracyRatio / 1.5 * 100, 100)}%`,
                            backgroundColor: getAccuracyColor(epic.accuracyRatio),
                          }}
                        />
                      </div>
                    </div>
                  </td>
                  <td className="number-cell">
                    <span className={`variance-pill ${epic.scheduleVariance < 0 ? 'variance-early' : epic.scheduleVariance > 0 ? 'variance-late' : 'variance-ontime'}`}>
                      {epic.scheduleVariance > 0 ? '+' : ''}{epic.scheduleVariance}d
                    </span>
                  </td>
                  <td>
                    <span className={`status-pill ${epic.status === 'EARLY' ? 'status-early' : epic.status === 'ON_TIME' ? 'status-ontime' : 'status-late'}`}>
                      {getStatusLabel(epic.status)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* How to read */}
      <div className="accuracy-howto">
        <details>
          <summary className="accuracy-howto-toggle">How to read this table?</summary>
          <div className="accuracy-howto-content">
            <div className="howto-block">
              <strong>What does Forecast Accuracy show?</strong>
              <p>Compares planned epic timelines (from planning forecasts) with actual completion dates. All calculations use <b>working days</b> (excluding weekends and holidays). Actual dates are taken from status transition history (entering Developing → exiting to Done).</p>
            </div>
            <div className="howto-block">
              <strong>Accuracy Ratio</strong>
              <p>Planned working days divided by actual. Ideal is <b>1.00</b> (plan = actual).</p>
              <ul>
                <li><span style={{ color: DSR_GREEN }}>0.95–1.05</span> — excellent accuracy</li>
                <li><span style={{ color: DSR_YELLOW }}>0.80–1.20</span> — acceptable deviation</li>
                <li><span style={{ color: DSR_RED }}>&lt;0.80 or &gt;1.20</span> — needs attention</li>
              </ul>
              <p>Ratio &gt; 1 — completed faster than planned. Ratio &lt; 1 — slower. <em>Note: this is opposite to DSR, where &gt; 1 = slower.</em></p>
            </div>
            <div className="howto-block">
              <strong>Schedule Variance</strong>
              <p>Difference in working days between actual and planned.</p>
              <ul>
                <li><span className="variance-pill variance-early">−3d</span> — completed 3 working days early</li>
                <li><span className="variance-pill variance-ontime">0d</span> — exactly on time</li>
                <li><span className="variance-pill variance-late">+5d</span> — 5 working days late</li>
              </ul>
            </div>
            <div className="howto-block">
              <strong>Estimate Change</strong>
              <p>Change in epic effort estimate: from first appearance in forecast to entering development.</p>
              <ul>
                <li><span className="estimate-pill estimate-same">120h</span> — estimate unchanged</li>
                <li><span className="estimate-pill estimate-up">80h → 120h</span> — estimate increased (scope creep)</li>
                <li><span className="estimate-pill estimate-down">120h → 80h</span> — estimate decreased</li>
              </ul>
            </div>
            <div className="howto-block">
              <strong>What to do with the results?</strong>
              <ul>
                <li>On-Time Delivery &lt; 50% — consider revisiting the estimation approach</li>
                <li>Ratio consistently &lt; 1 — team systematically underestimates complexity</li>
                <li>Ratio consistently &gt; 1 — estimates are too high, can plan more tightly</li>
              </ul>
            </div>
          </div>
        </details>
      </div>
    </div>
  )
}
