export interface DemoStory {
  key: string
  title: string
  status: string
  statusColor: string
  role: string
  assignee: string
  progress: number
}

export interface DemoEpic {
  key: string
  title: string
  status: string
  statusColor: string
  progress: Record<string, number>
  totalProgress: number // общий прогресс в %
  targetDay: number // целевая дата от бизнеса (дней от 1 февраля)
  baseExpectedDay: number // прогноз завершения при оптимальной позиции (дней от 1 февраля)
  optimalPosition: number // оптимальная позиция (1-based)
  stories: DemoStory[]
}

// Начальный порядок: пользователь должен переставить эпики чтобы все были вовремя
// Оптимальный порядок: DEMO-004, DEMO-001, DEMO-005, DEMO-002, DEMO-003, DEMO-006
// День 0 = 1 февраля 2026
export const mockEpics: DemoEpic[] = [
  {
    key: 'DEMO-001',
    title: 'Интеграция с внешними системами',
    status: 'Тестирование',
    statusColor: '#00875a',
    progress: { SA: 100, DEV: 65, QA: 20 },
    totalProgress: 70,
    targetDay: 14, // 15 февраля - бизнес хочет к этой дате
    baseExpectedDay: 10, // при оптимальной позиции завершим 11 февраля
    optimalPosition: 2,
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
    progress: { SA: 40, DEV: 0, QA: 0 },
    totalProgress: 13,
    targetDay: 28, // 1 марта
    baseExpectedDay: 25, // при оптимальной позиции завершим 26 февраля
    optimalPosition: 4,
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
    status: 'Анализ',
    statusColor: '#6554c0',
    progress: { SA: 100, DEV: 0, QA: 0 },
    totalProgress: 33,
    targetDay: 38, // 11 марта
    baseExpectedDay: 33, // при оптимальной позиции завершим 6 марта
    optimalPosition: 5,
    stories: [
      { key: 'DEMO-301', title: 'Шаблоны отчётов', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Мария С.', progress: 0 },
      { key: 'DEMO-302', title: 'Генерация PDF', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Иван П.', progress: 0 },
      { key: 'DEMO-303', title: 'Рассылка по расписанию', status: 'To Do', statusColor: '#505f79', role: 'DEV', assignee: 'Дмитрий К.', progress: 0 }
    ]
  },
  {
    key: 'DEMO-004',
    title: 'Дашборды и виджеты',
    status: 'Тестирование',
    statusColor: '#00875a',
    progress: { SA: 100, DEV: 80, QA: 50 },
    totalProgress: 85,
    targetDay: 7, // 8 февраля - ближайший дедлайн
    baseExpectedDay: 4, // при оптимальной позиции завершим 5 февраля
    optimalPosition: 1,
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
    progress: { SA: 100, DEV: 45, QA: 0 },
    totalProgress: 48,
    targetDay: 21, // 22 февраля
    baseExpectedDay: 18, // при оптимальной позиции завершим 19 февраля
    optimalPosition: 3,
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
    progress: { SA: 60, DEV: 0, QA: 0 },
    totalProgress: 20,
    targetDay: 45, // 18 марта
    baseExpectedDay: 40, // при оптимальной позиции завершим 13 марта
    optimalPosition: 6,
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
  phases: Record<string, { start: number; duration: number }>
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
          SA: { start: 0, duration: 3 },
          DEV: { start: 5, duration: 10 },
          QA: { start: 18, duration: 4 }
        }
      },
      {
        key: 'DEMO-102',
        title: 'Обработка webhook-ов',
        start: 8,
        duration: 24,
        phases: {
          SA: { start: 0, duration: 2 },
          DEV: { start: 4, duration: 12 },
          QA: { start: 19, duration: 5 }
        }
      },
      {
        key: 'DEMO-103',
        title: 'Тестирование интеграции',
        start: 20,
        duration: 14,
        phases: {
          QA: { start: 4, duration: 10 }
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
          SA: { start: 0, duration: 8 }
        }
      },
      {
        key: 'DEMO-202',
        title: 'CRUD операции сотрудников',
        start: 10,
        duration: 26,
        phases: {
          SA: { start: 0, duration: 3 },
          DEV: { start: 5, duration: 12 },
          QA: { start: 20, duration: 6 }
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
          SA: { start: 0, duration: 3 },
          DEV: { start: 5, duration: 8 },
          QA: { start: 16, duration: 4 }
        }
      },
      {
        key: 'DEMO-302',
        title: 'График динамики',
        start: 18,
        duration: 20,
        phases: {
          SA: { start: 0, duration: 2 },
          DEV: { start: 4, duration: 10 },
          QA: { start: 16, duration: 4 }
        }
      },
      {
        key: 'DEMO-303',
        title: 'Экспорт отчётов',
        start: 24,
        duration: 16,
        phases: {
          DEV: { start: 2, duration: 8 },
          QA: { start: 12, duration: 4 }
        }
      }
    ]
  }
]
