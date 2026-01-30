export interface DemoStory {
  key: string
  title: string
  status: string
  statusColor: string
  role: 'SA' | 'DEV' | 'QA'
  assignee: string
  progress: number
}

export interface DemoEpic {
  key: string
  title: string
  status: string
  statusColor: string
  progress: { sa: number; dev: number; qa: number }
  expectedDone: string
  variance: number // дней: + опоздание, - раньше срока
  stories: DemoStory[]
}

export const mockEpics: DemoEpic[] = [
  {
    key: 'DEMO-001',
    title: 'Интеграция с внешними системами',
    status: 'В работе',
    statusColor: '#0052cc',
    progress: { sa: 100, dev: 65, qa: 20 },
    expectedDone: '15 февраля',
    variance: -5,
    stories: [
      { key: 'DEMO-101', title: 'API интеграция с CRM', status: 'Done', statusColor: '#00875a', role: 'DEV', assignee: 'Алексей К.', progress: 100 },
      { key: 'DEMO-102', title: 'Обработка webhook-ов', status: 'In Progress', statusColor: '#0052cc', role: 'DEV', assignee: 'Мария С.', progress: 70 },
      { key: 'DEMO-103', title: 'Тестирование интеграции', status: 'To Do', statusColor: '#505f79', role: 'QA', assignee: 'Елена В.', progress: 0 }
    ]
  },
  {
    key: 'DEMO-002',
    title: 'Управление сотрудниками',
    status: 'Анализ',
    statusColor: '#6554c0',
    progress: { sa: 40, dev: 0, qa: 0 },
    expectedDone: '28 февраля',
    variance: 12,
    stories: [
      { key: 'DEMO-201', title: 'Анализ требований HR', status: 'In Progress', statusColor: '#0052cc', role: 'SA', assignee: 'Ольга Н.', progress: 60 },
      { key: 'DEMO-202', title: 'Проектирование БД сотрудников', status: 'To Do', statusColor: '#505f79', role: 'SA', assignee: 'Иван П.', progress: 0 },
      { key: 'DEMO-203', title: 'CRUD операции сотрудников', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Дмитрий К.', progress: 0 },
      { key: 'DEMO-204', title: 'Импорт из Excel', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Алексей К.', progress: 0 }
    ]
  },
  {
    key: 'DEMO-003',
    title: 'Автоматизация отчётности',
    status: 'Готово к разработке',
    statusColor: '#00875a',
    progress: { sa: 100, dev: 0, qa: 0 },
    expectedDone: '10 марта',
    variance: 0,
    stories: [
      { key: 'DEMO-301', title: 'Шаблоны отчётов', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Мария С.', progress: 0 },
      { key: 'DEMO-302', title: 'Генерация PDF', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Иван П.', progress: 0 },
      { key: 'DEMO-303', title: 'Рассылка по расписанию', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Дмитрий К.', progress: 0 }
    ]
  },
  {
    key: 'DEMO-004',
    title: 'Дашборды и виджеты',
    status: 'В работе',
    statusColor: '#0052cc',
    progress: { sa: 100, dev: 80, qa: 50 },
    expectedDone: '5 февраля',
    variance: -3,
    stories: [
      { key: 'DEMO-401', title: 'Виджет KPI', status: 'Done', statusColor: '#00875a', role: 'DEV', assignee: 'Алексей К.', progress: 100 },
      { key: 'DEMO-402', title: 'График динамики', status: 'Done', statusColor: '#00875a', role: 'DEV', assignee: 'Мария С.', progress: 100 },
      { key: 'DEMO-403', title: 'Тестирование виджетов', status: 'In Progress', statusColor: '#0052cc', role: 'QA', assignee: 'Елена В.', progress: 50 }
    ]
  },
  {
    key: 'DEMO-005',
    title: 'Система уведомлений',
    status: 'В работе',
    statusColor: '#0052cc',
    progress: { sa: 100, dev: 45, qa: 0 },
    expectedDone: '20 февраля',
    variance: 8,
    stories: [
      { key: 'DEMO-501', title: 'Email уведомления', status: 'Done', statusColor: '#00875a', role: 'DEV', assignee: 'Иван П.', progress: 100 },
      { key: 'DEMO-502', title: 'Push уведомления', status: 'In Progress', statusColor: '#0052cc', role: 'DEV', assignee: 'Дмитрий К.', progress: 40 },
      { key: 'DEMO-503', title: 'Настройки подписок', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Алексей К.', progress: 0 },
      { key: 'DEMO-504', title: 'Тестирование уведомлений', status: 'To Do', statusColor: '#505f79', role: 'QA', assignee: 'Елена В.', progress: 0 }
    ]
  },
  {
    key: 'DEMO-006',
    title: 'Доступы и ролевая модель',
    status: 'Анализ',
    statusColor: '#6554c0',
    progress: { sa: 60, dev: 0, qa: 0 },
    expectedDone: '15 марта',
    variance: 0,
    stories: [
      { key: 'DEMO-601', title: 'Модель ролей и прав', status: 'In Progress', statusColor: '#0052cc', role: 'SA', assignee: 'Ольга Н.', progress: 80 },
      { key: 'DEMO-602', title: 'Интеграция с LDAP', status: 'To Do', statusColor: '#505f79', role: 'SA', assignee: 'Иван П.', progress: 0 }
    ]
  }
]

export const mockMetrics = {
  ltcActual: 1.05,
  ltcForecast: 0.93,
  throughput: 13,
  storiesCompleted: 4,
  epicsCompleted: 2,
  onTimeRate: 85,
  onTimeCount: 17,
  totalEpics: 20
}

export interface TimelineStory {
  key: string
  title: string
  start: number
  duration: number
  phases: {
    sa?: { start: number; duration: number }
    dev?: { start: number; duration: number }
    qa?: { start: number; duration: number }
  }
}

export interface TimelineEpic {
  key: string
  title: string
  dueDate: number // день дедлайна (от начала таймлайна)
  stories: TimelineStory[]
}

export const mockTimelineData: TimelineEpic[] = [
  {
    key: 'DEMO-001',
    title: 'Интеграция с внешними системами',
    dueDate: 30, // дедлайн на 30й день
    stories: [
      {
        key: 'DEMO-101',
        title: 'API интеграция с CRM',
        start: 0,
        duration: 22,
        phases: {
          sa: { start: 0, duration: 3 },
          dev: { start: 5, duration: 10 },
          qa: { start: 18, duration: 4 }
        }
      },
      {
        key: 'DEMO-102',
        title: 'Обработка webhook-ов',
        start: 8,
        duration: 24,
        phases: {
          sa: { start: 0, duration: 2 },
          dev: { start: 4, duration: 12 },
          qa: { start: 19, duration: 5 }
        }
      },
      {
        key: 'DEMO-103',
        title: 'Тестирование интеграции',
        start: 20,
        duration: 14,
        phases: {
          qa: { start: 4, duration: 10 }
        }
      }
    ]
  },
  {
    key: 'DEMO-002',
    title: 'Управление сотрудниками',
    dueDate: 32, // дедлайн на 32й день
    stories: [
      {
        key: 'DEMO-201',
        title: 'Анализ требований HR',
        start: 6,
        duration: 12,
        phases: {
          sa: { start: 0, duration: 8 }
        }
      },
      {
        key: 'DEMO-202',
        title: 'CRUD операции сотрудников',
        start: 10,
        duration: 26,
        phases: {
          sa: { start: 0, duration: 3 },
          dev: { start: 5, duration: 12 },
          qa: { start: 20, duration: 6 }
        }
      }
    ]
  },
  {
    key: 'DEMO-003',
    title: 'Дашборды и виджеты',
    dueDate: 40, // дедлайн на 40й день
    stories: [
      {
        key: 'DEMO-301',
        title: 'Виджет KPI',
        start: 14,
        duration: 20,
        phases: {
          sa: { start: 0, duration: 3 },
          dev: { start: 5, duration: 8 },
          qa: { start: 16, duration: 4 }
        }
      },
      {
        key: 'DEMO-302',
        title: 'График динамики',
        start: 18,
        duration: 20,
        phases: {
          sa: { start: 0, duration: 2 },
          dev: { start: 4, duration: 10 },
          qa: { start: 16, duration: 4 }
        }
      },
      {
        key: 'DEMO-303',
        title: 'Экспорт отчётов',
        start: 24,
        duration: 16,
        phases: {
          dev: { start: 2, duration: 8 },
          qa: { start: 12, duration: 4 }
        }
      }
    ]
  }
]
