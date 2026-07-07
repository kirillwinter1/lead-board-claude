import type { CalendarDay } from '../../api/myWork'
import { ABSENCE_COLORS } from '../AbsenceModal'
import type { AbsenceType } from '../../api/teams'

// CSS classes (mywork-cal-*) live in MyWorkPage.css — consuming pages must
// import that stylesheet.

const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function localDateStr(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function MyWorklogCalendar({ days }: { days: CalendarDay[] }) {
  const todayStr = localDateStr(new Date())

  return (
    <div className="mywork-cal">
      <div className="mywork-cal-weekdays">
        {WEEKDAY_LABELS.map(label => (
          <span key={label} className="mywork-cal-weekday">{label}</span>
        ))}
      </div>
      <div className="mywork-cal-grid">
        {days.map(d => {
          const isFuture = d.date > todayStr
          const isLow = d.dayType === 'WORKDAY' && !d.absenceType && !isFuture && d.loggedH < d.normH * 0.5
          const absenceColor = d.absenceType
            ? ABSENCE_COLORS[d.absenceType as AbsenceType] || ABSENCE_COLORS.OTHER
            : undefined

          return (
            <div
              key={d.date}
              className={`mywork-cal-cell ${d.dayType.toLowerCase()} ${d.date === todayStr ? 'today' : ''} ${d.absenceType ? 'absence' : ''} ${isLow ? 'low-hours' : ''} ${isFuture ? 'future' : ''}`}
              style={absenceColor ? { backgroundColor: absenceColor } : undefined}
              title={d.byIssue.map(i => `${i.issueKey}: ${i.hours}h`).join('\n') || 'No worklog'}
            >
              <span className="mywork-cal-daynum">{Number(d.date.slice(8))}</span>
              {d.dayType === 'WORKDAY' && !d.absenceType && (
                <span className="mywork-cal-hours">{d.loggedH}/{d.normH}h</span>
              )}
            </div>
          )
        })}
      </div>
      <div className="mywork-cal-legend">
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-logged" />
          Logged
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-low-hours" />
          Low hours
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-weekend" />
          Weekend
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-holiday" />
          Holiday
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-absence" />
          Absence
        </span>
      </div>
    </div>
  )
}
