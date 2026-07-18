import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import axios from 'axios'
import type { FixPreview } from '../../api/dataQuality'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)
// isAxiosError is used by the component to inspect thrown errors.
mockedAxios.isAxiosError = ((e: unknown): boolean =>
  !!(e && typeof e === 'object' && 'isAxiosError' in e)) as unknown as typeof axios.isAxiosError

// Stub RiceForm so the RICE case does not need the rice API.
vi.mock('../rice/RiceForm', () => ({
  RiceForm: ({ issueKey, onSaved }: { issueKey: string; onSaved?: () => void }) => (
    <div data-testid="rice-form">
      RICE for {issueKey}
      <button onClick={() => onSaved?.()}>rice-save</button>
    </div>
  ),
}))

import { FixModal } from './FixModal'

const basePreview: FixPreview = {
  issueKey: 'LB-42',
  rule: 'EPIC_NO_DUE_DATE',
  fixType: 'DUE_DATE',
  title: 'Set epic due date',
  applicable: true,
  notApplicableReason: null,
  risky: false,
  warning: null,
  authMode: 'OAUTH',
  changes: [
    { issueKey: 'LB-42', summary: 'Some epic', issueType: 'Epic', field: 'Due date', from: '∅', to: '2026-08-01', local: false },
  ],
  affectedIssues: [],
  inputs: [
    { name: 'dueDate', type: 'date', label: 'Due date', required: true },
  ],
  choices: [],
}

function mockPreview(preview: FixPreview) {
  mockedAxios.get.mockImplementation((url: string) => {
    if (url.includes('/api/data-quality/fix-preview')) {
      return Promise.resolve({ data: preview })
    }
    return Promise.reject(new Error('Unknown URL'))
  })
}

const noop = () => {}

describe('FixModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders change lines from the preview', async () => {
    mockPreview(basePreview)
    render(<FixModal issueKey="LB-42" rule="EPIC_NO_DUE_DATE" ruleLabel="Epic without due date" onClose={noop} onApplied={noop} />)

    await waitFor(() => {
      // Modal title is derived from the human rule label, not the preview title
      expect(screen.getByText('Fix: Epic without due date')).toBeInTheDocument()
      // The neutral preview title renders as a lead/description line
      expect(screen.getByText('Set epic due date')).toBeInTheDocument()
      expect(screen.getByText('LB-42')).toBeInTheDocument()
      expect(screen.getByText('2026-08-01')).toBeInTheDocument()
    })
  })

  it('drives the right-hand StatusBadge from the selected targetStatus input', async () => {
    const preview: FixPreview = {
      ...basePreview,
      rule: 'STATUS_MISMATCH',
      title: 'Change story status',
      changes: [
        { issueKey: 'LB-500', summary: 'Some story', issueType: 'Story', field: 'Status', from: 'Новое', to: 'In Review', local: false },
      ],
      inputs: [
        {
          name: 'targetStatus',
          type: 'select',
          label: 'New status',
          required: true,
          defaultValue: 'In Review',
          options: [
            { value: 'In Review', label: 'In Review' },
            { value: 'Done', label: 'Done' },
          ],
        },
      ],
    }
    mockPreview(preview)
    render(<FixModal issueKey="LB-500" rule="STATUS_MISMATCH" ruleLabel="Status mismatch" onClose={noop} onApplied={noop} />)

    await waitFor(() => expect(screen.getByText('LB-500')).toBeInTheDocument())

    // The right-hand badge (2nd) initially reflects the default targetStatus
    const badges = () => document.querySelectorAll('.fix-change-status .status-badge')
    expect(badges()).toHaveLength(2)
    expect(badges()[1].textContent).toBe('In Review')

    // Pick "Done" from the "New status" dropdown
    const trigger = document.querySelector('.filter-dropdown-trigger') as HTMLElement
    fireEvent.click(trigger)
    const doneOption = Array.from(document.querySelectorAll('.filter-dropdown-item-label'))
      .find(el => el.textContent === 'Done') as HTMLElement
    fireEvent.click(doneOption)

    // The right-hand badge now reflects the new selection
    await waitFor(() => expect(badges()[1].textContent).toBe('Done'))
  })

  it('renders the issue-type icon and StatusBadge for Status changes', async () => {
    const preview: FixPreview = {
      ...basePreview,
      rule: 'STATUS_MISMATCH',
      title: 'Fix status',
      changes: [
        { issueKey: 'LB-431', summary: 'Настройка PgBouncer', issueType: 'Story', field: 'Status', from: 'Новое', to: 'Test Review', local: true },
      ],
      inputs: [],
    }
    mockPreview(preview)
    render(<FixModal issueKey="LB-431" rule="STATUS_MISMATCH" ruleLabel="Status mismatch" onClose={noop} onApplied={noop} />)

    await waitFor(() => expect(screen.getByText('LB-431')).toBeInTheDocument())

    // Issue-type icon rendered next to the key (alt = issue type name)
    expect(screen.getByAltText('Story')).toBeInTheDocument()

    // Status values rendered via StatusBadge (not plain text)
    const badges = document.querySelectorAll('.status-badge')
    expect(badges.length).toBe(2)
    expect(screen.getByText('Новое')).toBeInTheDocument()
    expect(screen.getByText('Test Review')).toBeInTheDocument()

    // The "local" hint marker is preserved
    expect(screen.getByText('local')).toBeInTheDocument()
  })

  it('disables Apply until a required input is filled', async () => {
    mockPreview(basePreview)
    render(<FixModal issueKey="LB-42" rule="EPIC_NO_DUE_DATE" ruleLabel="Epic without due date" onClose={noop} onApplied={noop} />)

    const applyBtn = await screen.findByRole('button', { name: 'Apply' })
    expect(applyBtn).toBeDisabled()

    // Fill the date input
    const dateInput = document.querySelector('input[type="date"]') as HTMLInputElement
    fireEvent.change(dateInput, { target: { value: '2026-08-15' } })

    await waitFor(() => expect(applyBtn).not.toBeDisabled())
  })

  it('toggles the visible inputs/changes when a choice is picked', async () => {
    const preview: FixPreview = {
      ...basePreview,
      rule: 'CHILD_DUE_AFTER_EPIC',
      title: 'Resolve due date conflict',
      changes: [],
      inputs: [],
      choices: [
        {
          id: 'moveStory',
          label: 'Move story',
          changes: [{ issueKey: 'LB-1', summary: 'Story', issueType: 'Story', field: 'Due date', from: 'x', to: 'y', local: false }],
          inputs: [{ name: 'storyDate', type: 'date', label: 'Story due date', required: true }],
        },
        {
          id: 'moveEpic',
          label: 'Move epic',
          changes: [{ issueKey: 'LB-99', summary: 'Epic', issueType: 'Epic', field: 'Due date', from: 'a', to: 'b', local: false }],
          inputs: [{ name: 'epicDate', type: 'date', label: 'Epic due date', required: true }],
        },
      ],
    }
    mockPreview(preview)
    render(<FixModal issueKey="LB-1" rule="CHILD_DUE_AFTER_EPIC" ruleLabel="Child due after epic" onClose={noop} onApplied={noop} />)

    await waitFor(() => expect(screen.getByText('LB-1')).toBeInTheDocument())
    // First choice active by default
    expect(screen.getByText('Story due date')).toBeInTheDocument()
    expect(screen.queryByText('Epic due date')).not.toBeInTheDocument()

    // Switch to the second choice
    fireEvent.click(screen.getByRole('radio', { name: 'Move epic' }))

    await waitFor(() => {
      expect(screen.getByText('Epic due date')).toBeInTheDocument()
      expect(screen.getByText('LB-99')).toBeInTheDocument()
      expect(screen.queryByText('Story due date')).not.toBeInTheDocument()
    })
  })

  it('shows the red warning box and affected issues for risky fixes', async () => {
    const preview: FixPreview = {
      ...basePreview,
      rule: 'EPIC_DONE_OPEN_CHILDREN',
      title: 'Close open children',
      risky: true,
      warning: 'This will close 3 issues',
      inputs: [],
      affectedIssues: ['LB-2 — Child one', 'LB-3 — Child two'],
    }
    mockPreview(preview)
    render(<FixModal issueKey="LB-42" rule="EPIC_DONE_OPEN_CHILDREN" ruleLabel="Epic done open children" onClose={noop} onApplied={noop} />)

    await waitFor(() => {
      expect(screen.getByText('This will close 3 issues')).toBeInTheDocument()
      expect(screen.getByText('LB-2 — Child one')).toBeInTheDocument()
      expect(screen.getByText('LB-3 — Child two')).toBeInTheDocument()
    })
  })

  it('shows the not-applicable state and closes via onApplied', async () => {
    const preview: FixPreview = {
      ...basePreview,
      applicable: false,
      notApplicableReason: 'Already resolved in Jira',
      inputs: [],
      changes: [],
    }
    mockPreview(preview)
    const onApplied = vi.fn()
    render(<FixModal issueKey="LB-42" rule="EPIC_NO_DUE_DATE" ruleLabel="Epic without due date" onClose={noop} onApplied={onApplied} />)

    await waitFor(() => expect(screen.getByText('Already resolved in Jira')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Apply' })).not.toBeInTheDocument()

    // The shared Modal now also renders an X button with aria-label "Close" —
    // target the footer action button specifically.
    fireEvent.click(screen.getByText('Close', { selector: 'button.btn' }))
    expect(onApplied).toHaveBeenCalled()
  })

  it('renders the RiceForm for the RICE rule without fetching a preview', async () => {
    render(<FixModal issueKey="LB-7" rule="RICE_MISSING_ASSESSMENT" ruleLabel="Missing RICE assessment" onClose={noop} onApplied={noop} />)

    expect(screen.getByTestId('rice-form')).toBeInTheDocument()
    expect(screen.getByText('RICE for LB-7')).toBeInTheDocument()
    expect(mockedAxios.get).not.toHaveBeenCalled()
  })

  it('shows an inline error message when apply fails', async () => {
    mockPreview(basePreview)
    mockedAxios.post.mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { message: 'Transition not found' } },
    })
    render(<FixModal issueKey="LB-42" rule="EPIC_NO_DUE_DATE" ruleLabel="Epic without due date" onClose={noop} onApplied={noop} />)

    const applyBtn = await screen.findByRole('button', { name: 'Apply' })
    const dateInput = document.querySelector('input[type="date"]') as HTMLInputElement
    fireEvent.change(dateInput, { target: { value: '2026-08-15' } })
    await waitFor(() => expect(applyBtn).not.toBeDisabled())

    fireEvent.click(applyBtn)

    await waitFor(() => expect(screen.getByText('Transition not found')).toBeInTheDocument())
  })
})
