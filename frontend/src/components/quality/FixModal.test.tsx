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
    { issueKey: 'LB-42', summary: 'Some epic', field: 'Due date', from: '∅', to: '2026-08-01', local: false },
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
      expect(screen.getByText('Set epic due date')).toBeInTheDocument()
      expect(screen.getByText('LB-42')).toBeInTheDocument()
      expect(screen.getByText('2026-08-01')).toBeInTheDocument()
    })
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
          changes: [{ issueKey: 'LB-1', summary: 'Story', field: 'Due date', from: 'x', to: 'y', local: false }],
          inputs: [{ name: 'storyDate', type: 'date', label: 'Story due date', required: true }],
        },
        {
          id: 'moveEpic',
          label: 'Move epic',
          changes: [{ issueKey: 'LB-99', summary: 'Epic', field: 'Due date', from: 'a', to: 'b', local: false }],
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

    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
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
