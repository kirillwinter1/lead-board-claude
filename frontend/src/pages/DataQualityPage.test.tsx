import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import axios from 'axios'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)
mockedAxios.isAxiosError = ((e: unknown): boolean =>
  !!(e && typeof e === 'object' && 'isAxiosError' in e)) as unknown as typeof axios.isAxiosError

// Controllable role for the auth mock — flip before rendering to test gating.
let mockRole = 'ADMIN'
vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({
    hasRole: (...roles: string[]) => roles.includes(mockRole),
  }),
}))

import { DataQualityPage } from './DataQualityPage'

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
        { rule: 'EPIC_NO_ESTIMATE', severity: 'ERROR' as const, message: 'Story has no estimate', label: 'Epic without estimate', category: 'ESTIMATES', categoryLabel: 'Estimates', fixable: false },
        { rule: 'EPIC_NO_TEAM', severity: 'WARNING' as const, message: 'Story has no assignee', label: 'Epic without team', category: 'TEAM', categoryLabel: 'Team', fixable: true },
      ],
    },
    {
      issueKey: 'EPIC-1',
      issueType: 'Epic',
      summary: 'Test epic with info',
      status: 'In Progress',
      jiraUrl: 'https://jira.example.com/EPIC-1',
      violations: [
        { rule: 'EPIC_OVERDUE', severity: 'INFO' as const, message: 'No updates for 30 days', label: 'Epic overdue', category: 'DUE_DATES', categoryLabel: 'Due Dates', fixable: false },
      ],
    },
  ],
}

const mockTeams = [
  { id: 1, name: 'Team A' },
  { id: 2, name: 'Team B' },
]

const mockFixPreview = {
  issueKey: 'STORY-1',
  rule: 'EPIC_NO_TEAM',
  fixType: 'TEAM',
  title: 'Set epic team',
  applicable: true,
  notApplicableReason: null,
  risky: false,
  warning: null,
  authMode: 'OAUTH',
  changes: [
    { issueKey: 'STORY-1', summary: 'Test story', issueType: 'Story', field: 'Team', from: '∅', to: 'Team A', local: true },
  ],
  affectedIssues: [],
  inputs: [
    { name: 'teamId', type: 'select', label: 'Team', required: true, options: [{ value: '1', label: 'Team A' }] },
  ],
  choices: [],
}

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
    mockRole = 'ADMIN'
    mockedAxios.get.mockImplementation((url: string) => {
      if (url.includes('/api/data-quality/fix-preview')) {
        return Promise.resolve({ data: mockFixPreview })
      }
      if (url.includes('/api/teams')) {
        return Promise.resolve({ data: mockTeams })
      }
      if (url.includes('/api/data-quality')) {
        return Promise.resolve({ data: mockDataQuality })
      }
      return Promise.reject(new Error('Unknown URL'))
    })
    mockedAxios.post.mockResolvedValue({ data: { success: true, message: 'Done', updatedIssues: ['STORY-1'] } })
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

  describe('Severity filter', () => {
    it('should render severity dropdown with per-severity counts', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Severity')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Severity'))

      const menu = document.querySelector('.filter-dropdown-menu') as HTMLElement
      expect(menu).toBeInTheDocument()
      expect(within(menu).getByText('ERROR')).toBeInTheDocument()
      expect(within(menu).getByText('WARNING')).toBeInTheDocument()
      expect(within(menu).getByText('INFO')).toBeInTheDocument()
      // counts aus summary.bySeverity: ERROR 5, WARNING 10, INFO 3
      expect(within(menu).getByText('5')).toBeInTheDocument()
      expect(within(menu).getByText('10')).toBeInTheDocument()
      expect(within(menu).getByText('3')).toBeInTheDocument()
    })

    it('should filter the table when a severity is selected', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Severity'))
      const menu = document.querySelector('.filter-dropdown-menu') as HTMLElement
      // STORY-1 имеет ERROR-нарушение, EPIC-1 — только INFO
      fireEvent.click(within(menu).getByText('ERROR'))

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
        expect(screen.queryByText('EPIC-1')).not.toBeInTheDocument()
      })
    })

    it('should show a severity chip and restore all rows on chip remove', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Severity'))
      const menu = document.querySelector('.filter-dropdown-menu') as HTMLElement
      fireEvent.click(within(menu).getByText('ERROR'))

      await waitFor(() => {
        const chipsRow = document.querySelector('.filter-bar-chips') as HTMLElement
        expect(chipsRow).toBeInTheDocument()
        expect(within(chipsRow).getByText('ERROR')).toBeInTheDocument()
      })

      const chipsRow = document.querySelector('.filter-bar-chips') as HTMLElement
      const severityChip = within(chipsRow).getByText('ERROR').closest('.filter-chip') as HTMLElement
      fireEvent.click(severityChip.querySelector('.filter-chip-remove') as HTMLElement)

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })

    it('should not render the old severity toggle buttons', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('Total Issues')).toBeInTheDocument()
      })

      expect(document.querySelector('.filter-checkboxes')).toBeNull()
      expect(document.querySelector('.btn-toggle')).toBeNull()
    })
  })

  describe('Category filtering', () => {
    it('should not render the category summary chips row', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
      })

      expect(document.querySelector('.category-summary-row')).toBeNull()
      expect(document.querySelectorAll('.category-chip').length).toBe(0)
    })

    it('should show category options with counts from byCategory', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('All categories')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('All categories'))

      const menu = document.querySelector('.filter-dropdown-menu') as HTMLElement
      expect(within(menu).getByText('Estimates (12)')).toBeInTheDocument()
      expect(within(menu).getByText('Team (9)')).toBeInTheDocument()
      expect(within(menu).getByText('Due Dates (7)')).toBeInTheDocument()
    })

    it('should filter the table when a category is selected in the dropdown', async () => {
      renderDataQualityPage()

      await waitFor(() => {
        expect(screen.getByText('STORY-1')).toBeInTheDocument()
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('All categories'))
      const menu = document.querySelector('.filter-dropdown-menu') as HTMLElement
      // Только STORY-1 имеет нарушение категории ESTIMATES
      fireEvent.click(within(menu).getByText('Estimates (12)'))

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

  describe('Fix button', () => {
    const expandStory1 = () => {
      const row = screen.getByText('STORY-1').closest('tr')!
      const expanderBtn = row.querySelector('.expander-btn') as HTMLElement
      fireEvent.click(expanderBtn)
    }

    it('shows a Fix button only for fixable violations when the role is allowed', async () => {
      renderDataQualityPage()
      await waitFor(() => expect(screen.getByText('STORY-1')).toBeInTheDocument())
      expandStory1()

      await waitFor(() => {
        const fixButtons = screen.getAllByRole('button', { name: /^Fix:/ })
        // Only the EPIC_NO_TEAM violation is fixable (the estimate one is not).
        expect(fixButtons.length).toBe(1)
      })
    })

    it('hides the Fix button for roles without permission', async () => {
      mockRole = 'VIEWER'
      renderDataQualityPage()
      await waitFor(() => expect(screen.getByText('STORY-1')).toBeInTheDocument())
      expandStory1()

      await waitFor(() => {
        expect(screen.getByText('Epic without team')).toBeInTheDocument()
      })
      expect(screen.queryByRole('button', { name: /^Fix:/ })).not.toBeInTheDocument()
    })

    it('opens the fix modal when Fix is clicked', async () => {
      renderDataQualityPage()
      await waitFor(() => expect(screen.getByText('STORY-1')).toBeInTheDocument())
      expandStory1()

      const fixBtn = await screen.findByRole('button', { name: /^Fix:/ })
      fireEvent.click(fixBtn)

      await waitFor(() => {
        // Modal title is derived from the rule label (consistent with the table)
        expect(screen.getByText('Fix: Epic without team')).toBeInTheDocument()
        // The preview's neutral title renders as a lead/description line
        expect(screen.getByText('Set epic team')).toBeInTheDocument()
      })
    })

    it('refetches the report after a successful fix (onApplied)', async () => {
      renderDataQualityPage()
      await waitFor(() => expect(screen.getByText('STORY-1')).toBeInTheDocument())
      expandStory1()

      const fixBtn = await screen.findByRole('button', { name: /^Fix:/ })
      fireEvent.click(fixBtn)

      const applyBtn = await screen.findByRole('button', { name: 'Apply' })
      // Pick a team so the required native select is satisfied
      fireEvent.change(screen.getByLabelText('Team'), { target: { value: '1' } })
      await waitFor(() => expect(applyBtn).not.toBeDisabled())

      const callsBefore = mockedAxios.get.mock.calls.filter(
        c => c[0].includes('/api/data-quality') && !c[0].includes('fix-preview'),
      ).length

      fireEvent.click(applyBtn)

      await waitFor(() => {
        const callsAfter = mockedAxios.get.mock.calls.filter(
          c => c[0].includes('/api/data-quality') && !c[0].includes('fix-preview'),
        ).length
        expect(callsAfter).toBeGreaterThan(callsBefore)
      }, { timeout: 2000 })
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
