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
      'Missing estimate': 5,
      'No assignee': 8,
      'Stale issue': 5,
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
        { rule: 'Missing estimate', severity: 'ERROR' as const, message: 'Story has no estimate' },
        { rule: 'No assignee', severity: 'WARNING' as const, message: 'Story has no assignee' },
      ],
    },
    {
      issueKey: 'EPIC-1',
      issueType: 'Epic',
      summary: 'Test epic with info',
      status: 'In Progress',
      jiraUrl: 'https://jira.example.com/EPIC-1',
      violations: [
        { rule: 'Stale issue', severity: 'INFO' as const, message: 'No updates for 30 days' },
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

      expect(screen.getByText('Loading data quality report...')).toBeInTheDocument()
    })

    it('should render summary cards after loading', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Total Issues')).toBeInTheDocument()
        expect(screen.getByText('100')).toBeInTheDocument()
        expect(screen.getByText('Errors')).toBeInTheDocument()
        expect(screen.getByText('5')).toBeInTheDocument()
        expect(screen.getByText('Warnings')).toBeInTheDocument()
        expect(screen.getByText('10')).toBeInTheDocument()
      })
    })

    it('should render violations table', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })

    it('should render table headers', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('KEY')).toBeInTheDocument()
        expect(screen.getByText('TYPE')).toBeInTheDocument()
        expect(screen.getByText('SUMMARY')).toBeInTheDocument()
        expect(screen.getByText('STATUS')).toBeInTheDocument()
        expect(screen.getByText('SEVERITY')).toBeInTheDocument()
        expect(screen.getByText('ISSUES')).toBeInTheDocument()
      })
    })

    it('should show issue summary', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Test story with violations')).toBeInTheDocument()
        expect(screen.getByText('Test epic with info')).toBeInTheDocument()
      })
    })

    it('should show violation count per issue', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('2')).toBeInTheDocument() // STORY-1 has 2 violations
      })
    })
  })

  describe('Filters', () => {
    it('should render team filter', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('All teams')).toBeInTheDocument()
        expect(screen.getByText('Team A')).toBeInTheDocument()
        expect(screen.getByText('Team B')).toBeInTheDocument()
      })
    })

    it('should render severity checkboxes', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        const checkboxes = screen.getAllByRole('checkbox')
        expect(checkboxes.length).toBe(3) // ERROR, WARNING, INFO
      })
    })

    it('should filter by severity when checkbox toggled', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
      })

      // Uncheck ERROR and WARNING, keep only INFO
      const checkboxes = screen.getAllByRole('checkbox')
      fireEvent.click(checkboxes[0]) // Uncheck ERROR
      fireEvent.click(checkboxes[1]) // Uncheck WARNING

      // STORY-1 has ERROR and WARNING, so it should be hidden
      // EPIC-1 has INFO, so it should remain
      await waitFor(() => {
        expect(screen.queryByText('STORY-1')).not.toBeInTheDocument()
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })

    it('should render rule filter', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('All rules')).toBeInTheDocument()
      })
    })
  })

  describe('Refresh', () => {
    it('should render Refresh button', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Refresh')).toBeInTheDocument()
      })
    })

    it('should refetch data on Refresh click', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Refresh')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Refresh'))

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

      // Find and click the row
      const row = screen.getByText('Test story with violations').closest('tr')
      fireEvent.click(row!)

      await waitFor(() => {
        expect(screen.getByText('Story has no estimate')).toBeInTheDocument()
        expect(screen.getByText('Story has no assignee')).toBeInTheDocument()
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
        expect(screen.getByText('No data quality issues found!')).toBeInTheDocument()
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
        expect(screen.getByText('Error: Failed to load')).toBeInTheDocument()
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
