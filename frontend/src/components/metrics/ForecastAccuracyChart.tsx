import { ForecastAccuracyResponse, EpicAccuracy } from '../../api/metrics'
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

  const formatEstimate = (epic: EpicAccuracy) => {
    const initial = epic.initialEstimateHours
    const developing = epic.developingEstimateHours
    if (!initial && !developing) return '—'
    if (initial === developing || !developing) return `${initial}ч`
    return `${initial}ч → ${developing}ч`
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
          Насколько точно команда попадает в плановые сроки по эпикам.
          Сравниваем прогноз из планирования с фактической датой завершения.
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
            <span className="accuracy-hint">плановые дни / фактические дни</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: data.onTimeDeliveryRate >= 80 ? '#36B37E' : data.onTimeDeliveryRate >= 50 ? '#FFAB00' : '#FF5630' }}>
            {data.onTimeDeliveryRate.toFixed(0)}%
          </div>
          <div className="accuracy-label">
            On-Time Delivery
            <span className="accuracy-hint">% эпиков завершённых в срок или раньше</span>
          </div>
        </div>

        <div className="accuracy-card">
          <div className="accuracy-value" style={{ color: data.avgScheduleVariance <= 0 ? '#36B37E' : data.avgScheduleVariance <= 5 ? '#FFAB00' : '#FF5630' }}>
            {data.avgScheduleVariance > 0 ? '+' : ''}{data.avgScheduleVariance.toFixed(1)}д
          </div>
          <div className="accuracy-label">
            Schedule Variance
            <span className="accuracy-hint">среднее отклонение в днях (− раньше, + позже)</span>
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
                <th title="Плановые даты начала и окончания из прогноза">План</th>
                <th title="Фактические даты начала и завершения">Факт</th>
                <th title="Количество рабочих дней по плану">Дней план</th>
                <th title="Количество рабочих дней по факту">Дней факт</th>
                <th title="Изменение оценки: при появлении → при входе в разработку">Оценка</th>
                <th title="Плановые дни / Фактические дни. 1.0 = идеальное попадание">Ratio</th>
                <th title="Разница в днях: факт минус план. Минус = раньше срока">Отклонение</th>
                <th title="Раньше срока / В срок / С опозданием">Статус</th>
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
                      {epic.scheduleVariance > 0 ? '+' : ''}{epic.scheduleVariance}д
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
          <summary className="accuracy-howto-toggle">Как читать эту таблицу?</summary>
          <div className="accuracy-howto-content">
            <div className="howto-block">
              <strong>Что показывает Forecast Accuracy?</strong>
              <p>Сравнивает плановые сроки эпика (из прогноза на планировании) с фактическими датами завершения. Все расчёты ведутся в <b>рабочих днях</b> (без выходных и праздников). Фактические даты берутся из истории переходов статусов (вход в Developing → выход в Done).</p>
            </div>
            <div className="howto-block">
              <strong>Accuracy Ratio</strong>
              <p>Плановые рабочие дни делим на фактические. Идеал — <b>1.00</b> (план = факт).</p>
              <ul>
                <li><span style={{ color: '#36B37E' }}>0.95–1.05</span> — отличное попадание</li>
                <li><span style={{ color: '#FFAB00' }}>0.80–1.20</span> — приемлемое отклонение</li>
                <li><span style={{ color: '#FF5630' }}>&lt;0.80 или &gt;1.20</span> — требует внимания</li>
              </ul>
              <p>Ratio &gt; 1 — сделали быстрее плана. Ratio &lt; 1 — медленнее.</p>
            </div>
            <div className="howto-block">
              <strong>Отклонение (Schedule Variance)</strong>
              <p>Разница в рабочих днях между фактом и планом.</p>
              <ul>
                <li><span className="variance-pill variance-early">−3д</span> — завершили на 3 рабочих дня раньше</li>
                <li><span className="variance-pill variance-ontime">0д</span> — точно в срок</li>
                <li><span className="variance-pill variance-late">+5д</span> — опоздание на 5 рабочих дней</li>
              </ul>
            </div>
            <div className="howto-block">
              <strong>Оценка (Estimate Change)</strong>
              <p>Изменение трудозатрат эпика: от первого появления в прогнозе до входа в разработку.</p>
              <ul>
                <li><span className="estimate-pill estimate-same">120ч</span> — оценка не менялась</li>
                <li><span className="estimate-pill estimate-up">80ч → 120ч</span> — оценка выросла (scope creep)</li>
                <li><span className="estimate-pill estimate-down">120ч → 80ч</span> — оценка снизилась</li>
              </ul>
            </div>
            <div className="howto-block">
              <strong>Что делать с результатами?</strong>
              <ul>
                <li>On-Time Delivery &lt; 50% — стоит пересмотреть подход к оценке сроков</li>
                <li>Ratio стабильно &lt; 1 — команда систематически недооценивает сложность</li>
                <li>Ratio стабильно &gt; 1 — оценки завышены, можно планировать плотнее</li>
              </ul>
            </div>
          </div>
        </details>
      </div>
    </div>
  )
}
