import { useState, useEffect, useCallback, useRef } from 'react'
import axios from 'axios'
import { WorkflowConfigPage } from './WorkflowConfigPage'
import './SetupWizardPage.css'

interface SetupWizardPageProps {
  onComplete: () => void
}

const STEP_LABELS = ['Period', 'Sync', 'Workflow', 'Done']
const WIZARD_STEP_KEY = 'setupWizardStep'
const WIZARD_MONTHS_KEY = 'setupWizardMonths'

export function SetupWizardPage({ onComplete }: SetupWizardPageProps) {
  const [step, setStep] = useState(() => {
    const saved = localStorage.getItem(WIZARD_STEP_KEY)
    return saved ? parseInt(saved, 10) : 1
  })
  const [months, setMonths] = useState(() => {
    const saved = localStorage.getItem(WIZARD_MONTHS_KEY)
    return saved ? parseInt(saved, 10) : 6
  })
  const [issueCount, setIssueCount] = useState<number | null>(null)
  const [checking, setChecking] = useState(false)
  const [checkError, setCheckError] = useState<string | null>(null)

  const [syncing, setSyncing] = useState(false)
  const [syncDone, setSyncDone] = useState(false)
  const [syncIssueCount, setSyncIssueCount] = useState(0)
  const [syncCurrentCount, setSyncCurrentCount] = useState(0)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // On mount: validate saved step — if DB was reset, issuesCount=0 means sync hasn't happened
  useEffect(() => {
    if (step > 1) {
      axios.get<{ issuesCount: number }>('/api/sync/status')
        .then(res => {
          if (res.data.issuesCount === 0) {
            setStep(1)
            localStorage.setItem(WIZARD_STEP_KEY, '1')
          }
        })
        .catch(() => {})
    }
  }, [])

  // Persist step to localStorage
  useEffect(() => {
    localStorage.setItem(WIZARD_STEP_KEY, String(step))
  }, [step])

  useEffect(() => {
    localStorage.setItem(WIZARD_MONTHS_KEY, String(months))
  }, [months])

  // Cleanup poll on unmount
  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [])

  const handleCheck = useCallback(() => {
    setChecking(true)
    setCheckError(null)
    setIssueCount(null)
    axios.get<{ total: number; months: number }>('/api/sync/issue-count', {
      params: { months: months > 0 ? months : undefined }
    })
      .then(res => {
        setIssueCount(res.data.total)
      })
      .catch(err => {
        setCheckError(err.response?.data?.error || err.message || 'Failed to count issues')
      })
      .finally(() => setChecking(false))
  }, [months])

  const handleStartSync = useCallback(() => {
    setSyncing(true)
    setSyncDone(false)
    setSyncCurrentCount(0)
    setStep(2)

    axios.post('/api/sync/trigger', null, {
      params: { months: months > 0 ? months : undefined }
    })
      .then(() => {
        pollRef.current = setInterval(() => {
          axios.get<{ syncInProgress: boolean; issuesCount: number }>('/api/sync/status')
            .then(res => {
              setSyncCurrentCount(res.data.issuesCount)
              if (!res.data.syncInProgress) {
                if (pollRef.current) clearInterval(pollRef.current)
                pollRef.current = null
                setSyncing(false)
                setSyncDone(true)
                setSyncIssueCount(res.data.issuesCount)
                setTimeout(() => setStep(3), 1500)
              }
            })
            .catch(() => {})
        }, 2000)
      })
      .catch(() => {
        setSyncing(false)
      })
  }, [months])

  const handleComplete = useCallback(() => {
    localStorage.removeItem(WIZARD_STEP_KEY)
    localStorage.removeItem(WIZARD_MONTHS_KEY)
    onComplete()
  }, [onComplete])

  const renderStepper = () => (
    <div className="wizard-stepper">
      {STEP_LABELS.map((label, idx) => {
        const stepNum = idx + 1
        const isActive = step === stepNum
        const isCompleted = step > stepNum
        return (
          <div key={label} style={{ display: 'flex', alignItems: 'center' }}>
            {idx > 0 && <div className={`wizard-step-line ${isCompleted || isActive ? 'completed' : ''}`} />}
            <div className="wizard-step-indicator">
              <div className={`wizard-step-circle ${isActive ? 'active' : ''} ${isCompleted ? 'completed' : ''}`}>
                {isCompleted ? '\u2713' : stepNum}
              </div>
              <span className={`wizard-step-label ${isActive ? 'active' : ''} ${isCompleted ? 'completed' : ''}`}>
                {label}
              </span>
            </div>
          </div>
        )
      })}
    </div>
  )

  const renderStep1 = () => (
    <div className="wizard-card">
      <h2>Welcome to OneLane</h2>
      <p>Let's set up your project. Choose a time period to sync issues from Jira.</p>

      <div className="wizard-period-row">
        <label>Sync issues updated in the last</label>
        <input
          type="number"
          className="wizard-period-input"
          value={months}
          min={0}
          max={120}
          onChange={e => {
            setMonths(parseInt(e.target.value, 10) || 0)
            setIssueCount(null)
            setCheckError(null)
          }}
        />
        <span>months</span>
      </div>

      <p style={{ fontSize: 12, color: '#97a0af', margin: '0 0 16px' }}>
        Set to 0 to sync all issues (may take longer)
      </p>

      {issueCount !== null && (
        <div className="wizard-issue-count">
          {issueCount} issues found in Jira
        </div>
      )}

      {checkError && (
        <div className="wizard-issue-count error">
          {checkError}
        </div>
      )}

      <div className="wizard-actions">
        <button
          className="wizard-btn wizard-btn-secondary"
          onClick={handleCheck}
          disabled={checking}
        >
          {checking ? 'Checking...' : 'Check'}
        </button>
        {issueCount !== null && issueCount > 0 && (
          <button
            className="wizard-btn wizard-btn-primary"
            onClick={handleStartSync}
          >
            Start Sync
          </button>
        )}
      </div>
    </div>
  )

  const renderStep2 = () => {
    const target = issueCount || 0
    const progress = target > 0 ? Math.min(95, Math.round((syncCurrentCount / target) * 100)) : 0
    const displayProgress = syncDone ? 100 : progress

    return (
      <div className="wizard-card">
        <h2>Syncing from Jira</h2>

        <div className="wizard-sync-progress">
          {syncing && (
            <>
              <div className="wizard-progress-bar-container">
                <div className="wizard-progress-bar" style={{ width: `${displayProgress}%` }} />
              </div>
              <div className="wizard-sync-status">
                <div className="wizard-spinner-inline" />
                Syncing... {syncCurrentCount > 0 ? `${syncCurrentCount} issues imported` : 'starting'}
              </div>
            </>
          )}
          {syncDone && (
            <div className="wizard-sync-done">
              Done! {syncIssueCount} issues synced.
            </div>
          )}
        </div>
      </div>
    )
  }

  const renderStep3 = () => (
    <div className="wizard-workflow-inline">
      <WorkflowConfigPage onComplete={() => setStep(4)} />
    </div>
  )

  const renderStep4 = () => (
    <div className="wizard-card">
      <div className="wizard-done-icon">{'\u2705'}</div>
      <h2>Setup Complete!</h2>
      <p>Your board is ready. You can start managing your project.</p>

      <div className="wizard-actions">
        <button
          className="wizard-btn wizard-btn-success"
          onClick={handleComplete}
        >
          Go to Board
        </button>
      </div>
    </div>
  )

  return (
    <div className="setup-wizard">
      {renderStepper()}
      {step === 1 && renderStep1()}
      {step === 2 && renderStep2()}
      {step === 3 && renderStep3()}
      {step === 4 && renderStep4()}
    </div>
  )
}
