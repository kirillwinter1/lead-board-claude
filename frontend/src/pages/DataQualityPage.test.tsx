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
    byCategory: {
      ESTIMATES: 12,
      TEAM: 9,
      DUE_DATES: 7,
    },
  },
  rules: [
    { name: 'EPIC_NO_ESTIMATE', label: 'Epic without estimate', category: 'ESTIMATES', categoryLabel: 'Estimates', severity: 'ERROR' },
    { name: 'EPIC_NO_TEAM', label: 'Epic without team', category: 'TEAM', categoryLabel: 'Team', severity: 'WARNING' },
    { name: 'EPIC_OVERDUE', label: 'Epic overdue', category: 'DUE_DATES', categoryLabel: 'Due Dates', severity: 'INFO' },
  ],
  violations: [
    {
      issueKey: 'STORY-1',
      issueType: 'Story',
      summary: 'Test story with violations',
      status: 'To Do',
      jiraUrl: 'https://jira.example.com/STORY-1',
      violations: [
        { rule: 'EPIC_NO_ESTIMATE', severity: 'ERROR' as const, message: 'Story has no estimate', label: 'Epic without estimate', category: 'ESTIMATES', categoryLabel: 'Estimates' },
        { rule: 'EPIC_NO_TEAM', severity: 'WARNING' as const, message: 'Story has no assignee', label: 'Epic without team', category: 'TEAM', categoryLabel: 'Team' },
      ],
    },
    {
      issueKey: 'EPIC-1',
      issueType: 'Epic',
      summary: 'Test epic with info',
      status: 'In Progress',
      jiraUrl: 'https://jira.example.com/EPIC-1',
      violations: [
        { rule: 'EPIC_OVERDUE', severity: 'INFO' as const, message: 'No updates for 30 days', label: 'Epic overdue', category: 'DUE_DATES', categoryLabel: 'Due Dates' },
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

      expect(screen.getByText('Loading report...')).toBeInTheDocument()
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
  })

  describe('Filters', () => {
    it('should render team filter', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('All teams')).toBeInTheDocument()
      })

      // Open dropdown to see team options
      fireEvent.click(screen.getByText('All teams'))

      await waitFor(() => {
        expect(screen.getByText('Team A')).toBeInTheDocument()
        expect(screen.getByText('Team B')).toBeInTheDocument()
      })
    })

    it('should render rule filter', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('All rules')).toBeInTheDocument()
      })
    })

    it('should render category filter', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('All categories')).toBeInTheDocument()
      })
    })
  })

  describe('Category filtering', () => {
    it('should render category summary chips from byCategory', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        const row = document.querySelector('.category-summary-row')
        expect(row).toBeInTheDocument()
        // 3 categories in byCategory
        expect(document.querySelectorAll('.category-chip').length).toBe(3)
      })
    })

    it('should filter the table when a category chip is clicked', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })

      // Click the "Estimates" category chip — only STORY-1 has an ESTIMATES violation
      const estimatesChip = Array.from(document.querySelectorAll('.category-chip'))
        .find(el => el.textContent?.includes('Estimates')) as HTMLElement
      fireEvent.click(estimatesChip)

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
        expect(screen.queryByText('EPIC-1')).not.toBeInTheDocument()
      })
    })

    it('should show category label and human-readable rule label in expanded row', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
      })

      const row = screen.getByText('STORY-1').closest('tr')!
      const expanderBtn = row.querySelector('.expander-btn') as HTMLElement
      fireEvent.click(expanderBtn)

      await waitFor(() => {
        // Rule label from API (not the enum code)
        expect(screen.getByText('Epic without estimate')).toBeInTheDocument()
        // Category label badge
        const categoryBadges = document.querySelectorAll('.violation-category')
        expect(categoryBadges.length).toBeGreaterThanOrEqual(1)
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
