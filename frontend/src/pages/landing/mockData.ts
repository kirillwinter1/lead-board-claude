export interface MockEpic {
  key: string
  title: string
  status: string
  statusColor: string
  progress: {
    sa: number
    dev: number
    qa: number
  }
  expectedDone: string
  storyCount: number
}

export interface MockStory {
  key: string
  title: string
  status: string
  assignee: string
  role: 'SA' | 'DEV' | 'QA'
}

export const mockEpics: MockEpic[] = [
  {
    key: 'DEMO-1',
    title: 'Авторизация пользователей',
    status: 'В работе',
    statusColor: '#0052cc',
    progress: { sa: 100, dev: 60, qa: 0 },
    expectedDone: '15 февраля',
    storyCount: 8
  },
  {
    key: 'DEMO-2',
    title: 'Интеграция с платёжной системой',
    status: 'Анализ',
    statusColor: '#6554c0',
    progress: { sa: 40, dev: 0, qa: 0 },
    expectedDone: '28 февраля',
    storyCount: 12
  },
  {
    key: 'DEMO-3',
    title: 'Система уведомлений',
    status: 'Готово к разработке',
    statusColor: '#00875a',
    progress: { sa: 100, dev: 0, qa: 0 },
    expectedDone: '10 марта',
    storyCount: 5
  }
]

export const mockStories: MockStory[] = [
  { key: 'DEMO-11', title: 'Форма входа', status: 'Done', assignee: 'Алексей К.', role: 'DEV' },
  { key: 'DEMO-12', title: 'OAuth Google', status: 'In Progress', assignee: 'Мария С.', role: 'DEV' },
  { key: 'DEMO-13', title: 'Восстановление пароля', status: 'Code Review', assignee: 'Иван П.', role: 'DEV' },
  { key: 'DEMO-14', title: 'Тестирование входа', status: 'To Do', assignee: 'Елена В.', role: 'QA' }
]

export const mockMetrics = {
  ltc: 1.15,
  throughput: 24,
  forecastAccuracy: 87,
  velocity: 42
}

export const mockTimelineData = [
  {
    epic: 'Авторизация',
    phases: [
      { phase: 'SA', start: 0, duration: 15, color: '#0052cc' },
      { phase: 'DEV', start: 10, duration: 25, color: '#36b37e' },
      { phase: 'QA', start: 30, duration: 15, color: '#ff991f' }
    ]
  },
  {
    epic: 'Платёжная система',
    phases: [
      { phase: 'SA', start: 5, duration: 20, color: '#0052cc' },
      { phase: 'DEV', start: 20, duration: 30, color: '#36b37e' },
      { phase: 'QA', start: 45, duration: 20, color: '#ff991f' }
    ]
  },
  {
    epic: 'Уведомления',
    phases: [
      { phase: 'SA', start: 15, duration: 10, color: '#0052cc' },
      { phase: 'DEV', start: 25, duration: 20, color: '#36b37e' },
      { phase: 'QA', start: 40, duration: 10, color: '#ff991f' }
    ]
  }
]
