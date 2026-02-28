import { useState, useEffect, useCallback, useRef } from 'react'
import axios from 'axios'
import { WorkflowConfigPage } from './WorkflowConfigPage'
import './SetupWizardPage.css'

interface SetupWizardPageProps {
  onComplete: () => void
}

const STEP_LABELS = ['Jira', 'Period', 'Sync', 'Workflow', 'Done']
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

  // Jira connection (step 1)
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')
  const [jiraEmail, setJiraEmail] = useState('')
  const [jiraApiToken, setJiraApiToken] = useState('')
  const [projectKey, setProjectKey] = useState('')
  const [jiraSaving, setJiraSaving] = useState(false)
  const [jiraTesting, setJiraTesting] = useState(false)
  const [jiraTestResult, setJiraTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [jiraSaveError, setJiraSaveError] = useState<string | null>(null)

  // Issue types for step 2
  const [jiraIssueTypes, setJiraIssueTypes] = useState<Array<{ name: string; iconUrl?: string; subtask?: boolean }>>([])
  const [loadingIssueTypes, setLoadingIssueTypes] = useState(false)

  const [syncing, setSyncing] = useState(false)
  const [syncDone, setSyncDone] = useState(false)
  const [syncError, setSyncError] = useState<string | null>(null)
  const [syncIssueCount, setSyncIssueCount] = useState(0)
  const [syncCurrentCount, setSyncCurrentCount] = useState(0)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const pollFailCount = useRef(0)

  // On mount: validate saved step — if DB was reset, issuesCount=0 means sync hasn't happened
  useEffect(() => {
    if (step > 2) {
      axios.get<{ issuesCount: number }>('/api/sync/status')
        .then(res => {
          if (res.data.issuesCount === 0) {
            setStep(2)
            localStorage.setItem(WIZARD_STEP_KEY, '2')
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

  // Fetch issue types when entering step 2
  useEffect(() => {
    if (step === 2 && jiraIssueTypes.length === 0) {
      setLoadingIssueTypes(true)
      axios.get<Array<{ name: string; iconUrl?: string; subtask?: boolean }>>('/api/admin/jira-metadata/issue-types')
        .then(res => setJiraIssueTypes(res.data))
        .catch(() => {})
        .finally(() => setLoadingIssueTypes(false))
    }
  }, [step])

  // Cleanup poll on unmount
  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [])

  // Load existing Jira config on mount
  useEffect(() => {
    axios.get<{
      configured: boolean
      jiraBaseUrl: string
      jiraEmail: string
      hasApiToken: boolean
      projectKey: string
      setupCompleted?: boolean
    }>('/api/jira-config')
      .then(res => {
        const d = res.data
        // If setup already completed, skip wizard entirely
        if (d.setupCompleted) {
          onComplete()
          return
        }
        if (d.jiraBaseUrl) setJiraBaseUrl(d.jiraBaseUrl)
        if (d.jiraEmail) setJiraEmail(d.jiraEmail)
        if (d.projectKey) setProjectKey(d.projectKey)
        // If already configured, auto-advance past step 1
        if (d.configured && step === 1) {
          setStep(2)
          localStorage.setItem(WIZARD_STEP_KEY, '2')
        }
      })
      .catch(() => {})
  }, [])

  const handleTestConnection = useCallback(() => {
    setJiraTesting(true)
    setJiraTestResult(null)
    axios.post<{ success: boolean; message?: string; error?: string }>('/api/jira-config/test', {
      jiraBaseUrl, jiraEmail, jiraApiToken, projectKey
    })
      .then(res => {
        setJiraTestResult({
          success: res.data.success,
          message: res.data.success ? (res.data.message || 'Connected!') : (res.data.error || 'Failed')
        })
      })
      .catch(err => {
        setJiraTestResult({ success: false, message: err.response?.data?.error || 'Connection test failed' })
      })
      .finally(() => setJiraTesting(false))
  }, [jiraBaseUrl, jiraEmail, jiraApiToken, projectKey])

  const handleSaveJiraConfig = useCallback(() => {
    setJiraSaving(true)
    setJiraSaveError(null)
    axios.put('/api/jira-config', {
      jiraBaseUrl, jiraEmail, jiraApiToken, projectKey,
      syncIntervalSeconds: 300, manualTeamManagement: false
    })
      .then(() => {
        setStep(2)
        localStorage.setItem(WIZARD_STEP_KEY, '2')
      })
      .catch(err => {
        setJiraSaveError(err.response?.data?.error || 'Failed to save configuration')
      })
      .finally(() => setJiraSaving(false))
  }, [jiraBaseUrl, jiraEmail, jiraApiToken, projectKey])

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
    setSyncError(null)
    setSyncCurrentCount(0)
    pollFailCount.current = 0
    setStep(3)

    axios.post('/api/sync/trigger', null, {
      params: { months: months > 0 ? months : undefined }
    })
      .then(() => {
        pollRef.current = setInterval(() => {
          axios.get<{ syncInProgress: boolean; issuesCount: number }>('/api/sync/status')
            .then(res => {
              pollFailCount.current = 0
              setSyncCurrentCount(res.data.issuesCount)
              if (!res.data.syncInProgress) {
                if (pollRef.current) clearInterval(pollRef.current)
                pollRef.current = null
                setSyncing(false)
                setSyncDone(true)
                setSyncIssueCount(res.data.issuesCount)
                setTimeout(() => setStep(4), 1500)
              }
            })
            .catch(() => {
              pollFailCount.current++
              if (pollFailCount.current >= 5) {
                if (pollRef.current) clearInterval(pollRef.current)
                pollRef.current = null
                setSyncing(false)
                setSyncError('Lost connection to server. Please refresh and try again.')
              }
            })
        }, 2000)
      })
      .catch(() => {
        setSyncing(false)
        setSyncError('Failed to start sync. Please try again.')
      })
  }, [months])

  const handleComplete = useCallback(() => {
    axios.post('/api/jira-config/setup-complete')
      .catch(() => {})
      .finally(() => {
        localStorage.removeItem(WIZARD_STEP_KEY)
        localStorage.removeItem(WIZARD_MONTHS_KEY)
        onComplete()
      })
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

  const renderStepJira = () => (
    <div className="wizard-card">
      <h2>Connect to Jira</h2>
      <p>Enter your Jira Cloud credentials to connect OneLane to your project.</p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 16 }}>
        <div>
          <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>Jira Base URL</label>
          <input
            type="url"
            className="wizard-period-input"
            style={{ width: '100%' }}
            value={jiraBaseUrl}
            onChange={e => setJiraBaseUrl(e.target.value)}
            placeholder="https://your-domain.atlassian.net"
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>Email</label>
          <input
            type="email"
            className="wizard-period-input"
            style={{ width: '100%' }}
            value={jiraEmail}
            onChange={e => setJiraEmail(e.target.value)}
            placeholder="your-email@example.com"
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>API Token</label>
          <input
            type="password"
            className="wizard-period-input"
            style={{ width: '100%' }}
            value={jiraApiToken}
            onChange={e => { setJiraApiToken(e.target.value); setJiraTestResult(null) }}
            placeholder="Your Jira API token"
          />
          <p style={{ fontSize: 11, color: '#97a0af', margin: '4px 0 0' }}>
            Generate at <a href="https://id.atlassian.com/manage-profile/security/api-tokens" target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>id.atlassian.com</a>
          </p>
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>Project Key</label>
          <input
            type="text"
            className="wizard-period-input"
            style={{ width: '100%' }}
            value={projectKey}
            onChange={e => setProjectKey(e.target.value.toUpperCase())}
            placeholder="PROJ"
          />
        </div>
      </div>

      {jiraTestResult && (
        <div className={`wizard-issue-count ${jiraTestResult.success ? '' : 'error'}`}>
          {jiraTestResult.success ? '\u2705 ' : '\u274c '}{jiraTestResult.message}
        </div>
      )}

      {jiraSaveError && (
        <div className="wizard-issue-count error">{jiraSaveError}</div>
      )}

      <div className="wizard-actions">
        <button
          className="wizard-btn wizard-btn-secondary"
          onClick={handleTestConnection}
          disabled={jiraTesting || !jiraBaseUrl || !jiraEmail || !jiraApiToken}
        >
          {jiraTesting ? 'Testing...' : 'Test Connection'}
        </button>
        <button
          className="wizard-btn wizard-btn-primary"
          onClick={handleSaveJiraConfig}
          disabled={jiraSaving || !jiraBaseUrl || !jiraEmail || !jiraApiToken || !projectKey}
        >
          {jiraSaving ? 'Saving...' : 'Save & Continue'}
        </button>
      </div>
    </div>
  )

  const renderStepPeriod = () => (
    <div className="wizard-card">
      <h2>Sync Period</h2>
      <p>Choose a time period to sync issues from Jira.</p>

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
        Recommended: 3–12 months. Set to 0 to sync all issues (may take longer).
      </p>

      {loadingIssueTypes && (
        <p style={{ fontSize: 13, color: '#6B778C' }}>Loading issue types from Jira...</p>
      )}
      {jiraIssueTypes.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <p style={{ fontSize: 13, fontWeight: 500, marginBottom: 8, color: '#42526E' }}>
            Issue types discovered in your Jira project:
          </p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {jiraIssueTypes.map(t => (
              <span key={t.name} style={{
                display: 'inline-flex', alignItems: 'center', gap: 6,
                padding: '4px 10px', background: '#F4F5F7', borderRadius: 12,
                fontSize: 13, color: '#42526E'
              }}>
                {t.iconUrl && <img src={t.iconUrl} alt="" style={{ width: 16, height: 16 }} />}
                {t.name}
                {t.subtask && <span style={{ fontSize: 10, color: '#97a0af' }}>(subtask)</span>}
              </span>
            ))}
          </div>
        </div>
      )}

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

  const renderStepSync = () => {
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
          {syncError && (
            <div className="wizard-issue-count error">
              {syncError}
            </div>
          )}
        </div>
      </div>
    )
  }

  const renderStepWorkflow = () => (
    <div className="wizard-workflow-inline">
      <WorkflowConfigPage onComplete={() => setStep(5)} />
    </div>
  )

  const renderStepDone = () => (
    <div className="wizard-card">
      <div className="wizard-done-icon">{'\u2705'}</div>
      <h2>Setup Complete!</h2>
      <p>Your board is ready. You can start managing your project.</p>
      <div style={{ margin: '16px 0', padding: '12px 16px', background: '#E9F2FE', borderRadius: 8, fontSize: 14, color: '#172B4D' }}>
        <strong>Next step:</strong> Go to the <strong>Teams</strong> tab to set up team members — assign roles, grades, and capacity for accurate planning and forecasting.
      </div>

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
      {step === 1 && renderStepJira()}
      {step === 2 && renderStepPeriod()}
      {step === 3 && renderStepSync()}
      {step === 4 && renderStepWorkflow()}
      {step === 5 && renderStepDone()}
    </div>
  )
}
