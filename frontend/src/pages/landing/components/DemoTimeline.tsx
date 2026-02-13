import { useState } from 'react'
import { mockTimelineData, TimelineStory } from '../mockData'
import epicIcon from '../../../icons/epic.png'
import storyIcon from '../../../icons/story.png'

const PHASE_COLORS: Record<string, string> = {
  SA: '#85B8FF',
  DEV: '#D6A0FB',
  QA: '#8BDBE5'
}

const PHASE_LABELS: Record<string, string> = {
  SA: 'Анализ',
  DEV: 'Разработка',
  QA: 'Тестирование',
  buffer: 'Буфер'
}

interface TooltipState {
  visible: boolean
  x: number
  y: number
  title: string
  phase: string
  duration: number
}

interface DemoTimelineProps {
  onHighlight?: (index: number | null) => void
}

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

// Get Monday of current week as start date
function getTimelineStartDate(): Date {
  const today = new Date()
  const dayOfWeek = today.getDay()
  const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek
  const startDate = new Date(today)
  startDate.setDate(today.getDate() + mondayOffset)
  return startDate
}

// Format date from day offset
function formatDateFromOffset(dayOffset: number): string {
  const startDate = getTimelineStartDate()
  const targetDate = new Date(startDate)
  targetDate.setDate(startDate.getDate() + dayOffset)
  return `${targetDate.getDate()} ${MONTHS[targetDate.getMonth()]}`
}

// Calculate epic end day (max of all stories end)
function getEpicEndDay(stories: TimelineStory[]): number {
  return Math.max(...stories.map(s => s.start + s.duration))
}

// Generate week headers starting from current week
function generateWeeks(): { label: string; dateRange: string }[] {
  const startDate = getTimelineStartDate()

  const weeks: { label: string; dateRange: string }[] = []

  for (let i = 0; i < 6; i++) {
    const weekStart = new Date(startDate)
    weekStart.setDate(startDate.getDate() + i * 7)
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekStart.getDate() + 6)

    const startDay = weekStart.getDate()
    const endDay = weekEnd.getDate()
    const startMonth = MONTHS[weekStart.getMonth()]
    const endMonth = MONTHS[weekEnd.getMonth()]

    const dateRange = startMonth === endMonth
      ? `${startDay}–${endDay} ${startMonth}`
      : `${startDay} ${startMonth} – ${endDay} ${endMonth}`

    weeks.push({
      label: i === 0 ? 'Текущая' : `Нед ${i + 1}`,
      dateRange
    })
  }

  return weeks
}

export function DemoTimeline({ onHighlight }: DemoTimelineProps) {
  const weeks = generateWeeks()
  const totalDays = 42

  const [tooltip, setTooltip] = useState<TooltipState>({
    visible: false,
    x: 0,
    y: 0,
    title: '',
    phase: '',
    duration: 0
  })

  const handlePhaseHover = (
    e: React.MouseEvent,
    title: string,
    phase: string,
    duration: number
  ) => {
    const rect = (e.target as HTMLElement).getBoundingClientRect()
    setTooltip({
      visible: true,
      x: rect.left + rect.width / 2,
      y: rect.top - 8,
      title,
      phase,
      duration
    })
  }

  const renderStoryBar = (story: TimelineStory) => {
    const storyLeft = (story.start / totalDays) * 100
    const storyWidth = (story.duration / totalDays) * 100

    // Calculate buffer gaps between phases
    const phases: { type: string; start: number; duration: number; color: string }[] = []

    Object.entries(story.phases).forEach(([type, phase]) => {
      if (phase) {
        phases.push({ type, start: phase.start, duration: phase.duration, color: PHASE_COLORS[type] || '#999' })
      }
    })

    // Sort phases by start time
    phases.sort((a, b) => a.start - b.start)

    // Calculate buffers (gaps between phases)
    const buffers: { start: number; duration: number }[] = []
    for (let i = 0; i < phases.length - 1; i++) {
      const currentEnd = phases[i].start + phases[i].duration
      const nextStart = phases[i + 1].start
      if (nextStart > currentEnd) {
        buffers.push({ start: currentEnd, duration: nextStart - currentEnd })
      }
    }

    return (
      <div
        key={story.key}
        className="demo-gantt-story-bar"
        style={{
          left: `${storyLeft}%`,
          width: `${storyWidth}%`
        }}
      >
        {/* Render phases */}
        {phases.map((phase) => (
          <div
            key={phase.type}
            className={`demo-gantt-phase ${phase.type}`}
            style={{
              left: `${(phase.start / story.duration) * 100}%`,
              width: `${(phase.duration / story.duration) * 100}%`,
              background: phase.color
            }}
            onMouseEnter={(e) => handlePhaseHover(e, story.title, phase.type, phase.duration)}
            onMouseLeave={() => setTooltip(prev => ({ ...prev, visible: false }))}
          />
        ))}
        {/* Render buffers between phases */}
        {buffers.map((buffer, idx) => (
          <div
            key={`buffer-${idx}`}
            className="demo-gantt-phase buffer"
            style={{
              left: `${(buffer.start / story.duration) * 100}%`,
              width: `${(buffer.duration / story.duration) * 100}%`,
              background: '#DFE1E6'
            }}
            onMouseEnter={(e) => handlePhaseHover(e, story.title, 'buffer', buffer.duration)}
            onMouseLeave={() => setTooltip(prev => ({ ...prev, visible: false }))}
          />
        ))}
      </div>
    )
  }

  return (
    <div
      className="demo-gantt"
      onMouseMove={(e) => {
        const rect = e.currentTarget.getBoundingClientRect()
        const y = e.clientY - rect.top

        if (y < 40) {
          onHighlight?.(0) // Header - Gantt по фазам
        } else {
          onHighlight?.(1) // Content - Видимость bottlenecks
        }
      }}
      onMouseLeave={() => onHighlight?.(null)}
    >
      {/* Header */}
      <div className="demo-gantt-container">
        <div className="demo-gantt-labels">
          <div className="demo-gantt-labels-header">Задача</div>
          {mockTimelineData.map((epic) => (
            <div key={epic.key} className="demo-gantt-epic-group">
              <div className="demo-gantt-epic-label">
                <img src={epicIcon} alt="" className="demo-gantt-icon" />
                <span className="demo-gantt-key">{epic.key}</span>
                <span className="demo-gantt-title">{epic.title}</span>
              </div>
              {epic.stories.map((story) => (
                <div key={story.key} className="demo-gantt-story-label">
                  <img src={storyIcon} alt="" className="demo-gantt-icon" />
                  <span className="demo-gantt-key">{story.key}</span>
                  <span className="demo-gantt-title">{story.title}</span>
                </div>
              ))}
            </div>
          ))}
        </div>

        <div className="demo-gantt-chart">
          <div className="demo-gantt-header">
            {weeks.map((week, i) => (
              <div key={i} className={`demo-gantt-header-cell ${i === 0 ? 'current-week' : ''}`}>
                <div className="demo-gantt-week-label">{week.label}</div>
                <div className="demo-gantt-week-dates">{week.dateRange}</div>
              </div>
            ))}
          </div>
          <div className="demo-gantt-body">
            {mockTimelineData.map((epic) => {
              const dueDatePosition = (epic.dueDate / totalDays) * 100
              const epicEndDay = getEpicEndDay(epic.stories)
              const delay = epicEndDay - epic.dueDate
              const dueDateFormatted = formatDateFromOffset(epic.dueDate)

              return (
                <div key={epic.key} className="demo-gantt-epic-rows">
                  {/* Due date line spanning entire epic group */}
                  <div
                    className="demo-gantt-due-line"
                    style={{ left: `${dueDatePosition}%` }}
                  >
                    <div className="demo-gantt-due-line-tooltip">
                      <div className="demo-gantt-due-line-tooltip-date">
                        Дедлайн: {dueDateFormatted}
                      </div>
                      {delay > 0 && (
                        <div className="demo-gantt-due-line-tooltip-delay">
                          Просрочка: {delay} дн.
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="demo-gantt-row demo-gantt-row-epic" />
                  {epic.stories.map((story) => (
                    <div key={story.key} className="demo-gantt-row">
                      {renderStoryBar(story)}
                    </div>
                  ))}
                </div>
              )
            })}
          </div>
        </div>
      </div>

      {/* Tooltip */}
      {tooltip.visible && (
        <div
          className="demo-timeline-tooltip"
          style={{
            left: tooltip.x,
            top: tooltip.y,
            transform: 'translate(-50%, -100%)'
          }}
        >
          <div className="demo-timeline-tooltip-title">
            {PHASE_LABELS[tooltip.phase] || tooltip.phase}
          </div>
          <div className="demo-timeline-tooltip-detail">
            {tooltip.duration} дней
          </div>
        </div>
      )}

      {/* Legend */}
      <div className="demo-gantt-legend">
        {Object.entries(PHASE_COLORS).map(([role, color]) => (
          <div key={role} className="demo-gantt-legend-item">
            <div className="demo-gantt-legend-color" style={{ background: color }} />
            <span>{role}</span>
          </div>
        ))}
        <div className="demo-gantt-legend-item">
          <div className="demo-gantt-legend-color" style={{ background: '#DFE1E6' }} />
          <span>Буфер</span>
        </div>
      </div>
    </div>
  )
}
