import { ForecastAccuracyResponse, EpicAccuracy } from '../../api/metrics'

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
          Нет завершённых эпиков за выбранный период.
          <br />
          <small style={{ color: '#6b778c' }}>
            Для расчёта точности прогноза нужны эпики с датой завершения (done_at) и снэпшоты прогнозов.
          </small>
        </div>
      </div>
    )
  }

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '—'
    const date = new Date(dateStr)
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'EARLY': return '#36B37E'
      case 'ON_TIME': return '#0065FF'
      case 'LATE': return '#FF5630'
      default: return '#6B778C'
    }
  }

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'EARLY': return 'Раньше срока'
      case 'ON_TIME': return 'В срок'
      case 'LATE': return 'С опозданием'
      default: return status
    }
  }

  const getAccuracyColor = (ratio: number) => {
    if (ratio >= 0.95 && ratio <= 1.05) return '#36B37E' // Perfect
    if (ratio >= 0.8 && ratio <= 1.2) return '#FFAB00'   // Acceptable
    return '#FF5630' // Poor
  }

  return (
    <div className="chart-section">
      <h3>Forecast Accuracy</h3>

      {/* Summary Cards */}
      <div className="accuracy-summary">
        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: getAccuracyColor(data.avgAccuracyRatio) }}>
            {data.avgAccuracyRatio.toFixed(2)}
          </div>
          <div className="accuracy-label">
            Accuracy Ratio
            <span className="accuracy-hint">план/факт</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: data.onTimeDeliveryRate >= 80 ? '#36B37E' : data.onTimeDeliveryRate >= 50 ? '#FFAB00' : '#FF5630' }}>
            {data.onTimeDeliveryRate.toFixed(0)}%
          </div>
          <div className="accuracy-label">
            On-Time Delivery
            <span className="accuracy-hint">в срок или раньше</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: data.avgScheduleVariance <= 0 ? '#36B37E' : data.avgScheduleVariance <= 5 ? '#FFAB00' : '#FF5630' }}>
            {data.avgScheduleVariance > 0 ? '+' : ''}{data.avgScheduleVariance.toFixed(1)}д
          </div>
          <div className="accuracy-label">
            Schedule Variance
            <span className="accuracy-hint">среднее отклонение</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-breakdown">
            <span style={{ color: '#36B37E' }}>{data.earlyCount}</span>
            <span style={{ color: '#6B778C' }}> / </span>
            <span style={{ color: '#0065FF' }}>{data.onTimeCount}</span>
            <span style={{ color: '#6B778C' }}> / </span>
            <span style={{ color: '#FF5630' }}>{data.lateCount}</span>
          </div>
          <div className="accuracy-label">
            Рано / В срок / Поздно
            <span className="accuracy-hint">из {data.totalCompleted} эпиков</span>
          </div>
        </div>
      </div>

      {/* Epic breakdown table */}
      {data.epics.length > 0 && (
        <div className="accuracy-table-container">
          <table className="metrics-table accuracy-table">
            <thead>
              <tr>
                <th>Эпик</th>
                <th>План</th>
                <th>Факт</th>
                <th>Дней план</th>
                <th>Дней факт</th>
                <th>Ratio</th>
                <th>Отклонение</th>
                <th>Статус</th>
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
                  <td className="number-cell" style={{ color: getAccuracyColor(epic.accuracyRatio) }}>
                    {epic.accuracyRatio.toFixed(2)}
                  </td>
                  <td className="number-cell" style={{ color: getStatusColor(epic.status) }}>
                    {epic.scheduleVariance > 0 ? '+' : ''}{epic.scheduleVariance}д
                  </td>
                  <td>
                    <span
                      className="status-badge"
                      style={{
                        backgroundColor: getStatusColor(epic.status) + '20',
                        color: getStatusColor(epic.status),
                      }}
                    >
                      {getStatusLabel(epic.status)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Legend */}
      <div className="accuracy-legend">
        <div className="legend-section">
          <strong>Accuracy Ratio:</strong>
          <span style={{ color: '#36B37E' }}>0.95-1.05 = отлично</span>
          <span style={{ color: '#FFAB00' }}>0.8-1.2 = приемлемо</span>
          <span style={{ color: '#FF5630' }}>&lt;0.8 или &gt;1.2 = требует внимания</span>
        </div>
        <div className="legend-section">
          <span>Ratio &gt; 1 = сделали быстрее плана</span>
          <span>Ratio &lt; 1 = сделали медленнее плана</span>
        </div>
      </div>
    </div>
  )
}
