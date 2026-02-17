import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import axios from 'axios'
import { DataQualityPage } from './DataQualityPage'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

const mockDataQuality = {
  generatedAt: '2024-01-15T10:00:00Z',
  teamId: 1,
  summary: {
    totalIssues: 100,
    issuesWithErrors: 5,
    issuesWithWarnings: 10,
    issuesWithInfo: 3,
    byRule: {
      'EPIC_NO_ESTIMATE': 5,
      'EPIC_NO_TEAM': 8,
      'EPIC_OVERDUE': 5,
    },
    bySeverity: {
      ERROR: 5,
      WARNING: 10,
      INFO: 3,
    },
  },
  violations: [
    {
      issueKey: 'STORY-1',
      issueType: 'Story',
      summary: 'Test story with violations',
      status: 'To Do',
      jiraUrl: 'https://jira.example.com/STORY-1',
      violations: [
        { rule: 'EPIC_NO_ESTIMATE', severity: 'ERROR' as const, message: 'Story has no estimate' },
        { rule: 'EPIC_NO_TEAM', severity: 'WARNING' as const, message: 'Story has no assignee' },
      ],
    },
    {
      issueKey: 'EPIC-1',
      issueType: 'Epic',
      summary: 'Test epic with info',
      status: 'In Progress',
      jiraUrl: 'https://jira.example.com/EPIC-1',
      violations: [
        { rule: 'EPIC_OVERDUE', severity: 'INFO' as const, message: 'No updates for 30 days' },
      ],
    },
  ],
}

const mockTeams = [
  { id: 1, name: 'Team A' },
  { id: 2, name: 'Team B' },
]

const renderDataQualityPage = () => {
  return render(
    <BrowserRouter>
      <DataQualityPage />
    </BrowserRouter>
  )
}

describe('DataQualityPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedAxios.get.mockImplementation((url: string) => {
      if (url.includes('/api/teams')) {
        return Promise.resolve({ data: mockTeams })
      }
      if (url.includes('/api/data-quality')) {
        return Promise.resolve({ data: mockDataQuality })
      }
      return Promise.reject(new Error('Unknown URL'))
    })
  })

  describe('Rendering', () => {
    it('should show loading state initially', () => {
      renderDataQualityPage()

      expect(screen.getByText('Загрузка отчёта...')).toBeInTheDocument()
    })

    it('should render summary cards after loading', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Всего задач')).toBeInTheDocument()
        expect(screen.getByText('100')).toBeInTheDocument()
        expect(screen.getByText('Ошибки')).toBeInTheDocument()
        expect(screen.getByText('5')).toBeInTheDocument()
        expect(screen.getByText('Предупреждения')).toBeInTheDocument()
        expect(screen.getByText('10')).toBeInTheDocument()
      })
    })

    it('should render table headers', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('КЛЮЧ')).toBeInTheDocument()
        expect(screen.getByText('ТИП')).toBeInTheDocument()
        expect(screen.getByText('НАЗВАНИЕ')).toBeInTheDocument()
        expect(screen.getByText('СТАТУС')).toBeInTheDocument()
        expect(screen.getByText('КРИТИЧНОСТЬ')).toBeInTheDocument()
        expect(screen.getByText('ПРОБЛЕМЫ')).toBeInTheDocument()
      })
    })
  })

  describe('Filters', () => {
    it('should render team filter', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Все команды')).toBeInTheDocument()
        expect(screen.getByText('Team A')).toBeInTheDocument()
        expect(screen.getByText('Team B')).toBeInTheDocument()
      })
    })

    it('should render rule filter', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Все правила')).toBeInTheDocument()
      })
    })
  })

  describe('Refresh', () => {
    it('should render Refresh button', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Обновить')).toBeInTheDocument()
      })
    })

    it('should refetch data on Refresh click', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Обновить')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Обновить'))

      await waitFor(() => {
        // Should have called API at least twice (initial + refresh)
        const dataQualityCalls = mockedAxios.get.mock.calls.filter(
          call => call[0].includes('/api/data-quality')
        )
        expect(dataQualityCalls.length).toBeGreaterThanOrEqual(2)
      })
    })
  })

  describe('Expandable rows', () => {
    it('should expand row on click to show violations', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
      })

      // Click the expander button (chevron ›) in the first violation row
      const row = screen.getByText('STORY-1').closest('tr')!
      const expanderBtn = row.querySelector('.expander-btn') as HTMLElement
      fireEvent.click(expanderBtn)

      await waitFor(() => {
        // Rule labels are rendered via getRuleLabel() — check for .violation-rule spans
        const violationRules = document.querySelectorAll('.violation-rule')
        expect(violationRules.length).toBeGreaterThanOrEqual(2)
      })
    })
  })

  describe('Empty state', () => {
    it('should show empty message when no violations', async () => {
      mockedAxios.get.mockImplementation((url: string) => {
        if (url.includes('/api/teams')) {
          return Promise.resolve({ data: mockTeams })
        }
        if (url.includes('/api/data-quality')) {
          return Promise.resolve({
            data: {
              ...mockDataQuality,
              violations: [],
            },
          })
        }
        return Promise.reject(new Error('Unknown URL'))
      })

      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Проблем с качеством данных не найдено!')).toBeInTheDocument()
      })
    })
  })

  describe('Error handling', () => {
    it('should show error message on API failure', async () => {
      mockedAxios.get.mockImplementation((url: string) => {
        if (url.includes('/api/teams')) {
          return Promise.resolve({ data: mockTeams })
        }
        if (url.includes('/api/data-quality')) {
          return Promise.reject(new Error('Failed to load'))
        }
        return Promise.reject(new Error('Unknown URL'))
      })

      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Ошибка: Failed to load')).toBeInTheDocument()
      })
    })
  })

  describe('Links', () => {
    it('should have links to Jira issues', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        const storyLink = screen.getByText('STORY-1').closest('a')
        expect(storyLink).toHaveAttribute('href', 'https://jira.example.com/STORY-1')
        expect(storyLink).toHaveAttribute('target', '_blank')
      })
    })
  })
})
