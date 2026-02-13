import { useEffect, useState } from 'react'
import {
  workflowConfigApi,
  WorkflowConfigResponse,
  WorkflowRoleDto,
  IssueTypeMappingDto,
  StatusMappingDto,
  LinkTypeMappingDto,
  ValidationResult,
  AutoDetectResult,
  JiraIssueTypeMetadata,
  JiraStatusesByType,
  JiraLinkTypeMetadata,
} from '../api/workflowConfig'
import './WorkflowConfigPage.css'

type TabKey = 'roles' | 'issueTypes' | 'statuses' | 'linkTypes'

const BOARD_CATEGORIES = ['EPIC', 'STORY', 'SUBTASK', 'IGNORE'] as const
const STATUS_CATEGORIES = ['NEW', 'REQUIREMENTS', 'PLANNED', 'IN_PROGRESS', 'DONE'] as const
const LINK_CATEGORIES = ['BLOCKS', 'RELATED', 'IGNORE'] as const

const WIZARD_STEPS = ['Fetch', 'Issue Types', 'Roles', 'Statuses', 'Link Types', 'Review & Save']

// --- Auto-suggest functions ---

function guessRoleFromSubtaskName(name: string): string {
  const lower = name.toLowerCase()
  if (lower.includes('аналитик') || lower.includes('анализ') || lower.includes('analys') || lower.includes('requirements') || lower.includes('требовани') || lower.includes('sa ') || lower === 'sa') return 'SA'
  if (lower.includes('тестирован') || lower.includes('тест') || lower.includes('test') || lower.includes('qa ') || lower === 'qa' || lower.includes('quality')) return 'QA'
  return 'DEV'
}

function suggestIssueTypes(jiraTypes: JiraIssueTypeMetadata[]): IssueTypeMappingDto[] {
  return jiraTypes.map(jt => {
    const lower = jt.name.toLowerCase()
    let boardCategory: IssueTypeMappingDto['boardCategory'] = 'IGNORE'
    let workflowRoleCode: string | null = null

    if (jt.subtask) {
      boardCategory = 'SUBTASK'
      workflowRoleCode = guessRoleFromSubtaskName(jt.name)
    } else if (lower.includes('epic') || lower.includes('эпик')) {
      boardCategory = 'EPIC'
    } else if (lower === 'story' || lower === 'bug' || lower === 'task' || lower === 'история' || lower === 'баг' || lower === 'задача' || lower.includes('story') || lower.includes('bug') || lower.includes('task')) {
      boardCategory = 'STORY'
    }

    return { id: null, jiraTypeName: jt.name, boardCategory, workflowRoleCode }
  })
}

function suggestRolesFromIssueTypes(suggestedTypes: IssueTypeMappingDto[]): WorkflowRoleDto[] {
  const roleSet = new Set<string>()
  suggestedTypes.forEach(t => {
    if (t.boardCategory === 'SUBTASK' && t.workflowRoleCode) {
      roleSet.add(t.workflowRoleCode)
    }
  })

  const roleDefaults: Record<string, { displayName: string; color: string; order: number; isDefault: boolean }> = {
    SA: { displayName: 'System Analysis', color: '#3b82f6', order: 1, isDefault: false },
    DEV: { displayName: 'Development', color: '#10b981', order: 2, isDefault: true },
    QA: { displayName: 'Quality Assurance', color: '#f59e0b', order: 3, isDefault: false },
  }

  if (roleSet.size === 0) {
    roleSet.add('SA')
    roleSet.add('DEV')
    roleSet.add('QA')
  }

  return Array.from(roleSet).map(code => {
    const def = roleDefaults[code]
    if (def) {
      return { id: null, code, displayName: def.displayName, color: def.color, sortOrder: def.order, isDefault: def.isDefault }
    }
    return { id: null, code, displayName: code, color: '#6B778C', sortOrder: 10, isDefault: false }
  }).sort((a, b) => a.sortOrder - b.sortOrder)
}

function suggestStatuses(
  jiraStatuses: JiraStatusesByType[],
  suggestedIssueTypes: IssueTypeMappingDto[],
): StatusMappingDto[] {
  const result: StatusMappingDto[] = []
  const seen = new Set<string>()

  const issueTypeMap = new Map<string, IssueTypeMappingDto['boardCategory']>()
  suggestedIssueTypes.forEach(t => issueTypeMap.set(t.jiraTypeName, t.boardCategory))

  const issueCategories: IssueTypeMappingDto['boardCategory'][] = ['EPIC', 'STORY', 'SUBTASK']

  for (const group of jiraStatuses) {
    const boardCat = issueTypeMap.get(group.issueType)
    if (!boardCat || boardCat === 'IGNORE') continue

    for (const st of group.statuses) {
      for (const issueCat of issueCategories) {
        if (issueCat === 'SUBTASK' && boardCat !== 'SUBTASK' && boardCat !== 'STORY') continue
        if (issueCat === 'EPIC' && boardCat !== 'EPIC') continue
        if (issueCat === 'STORY' && boardCat !== 'STORY') continue

        const key = `${st.name}|${issueCat}`
        if (seen.has(key)) continue
        seen.add(key)

        const jiraCat = st.statusCategory?.toLowerCase() || ''
        let statusCategory: StatusMappingDto['statusCategory'] = 'IN_PROGRESS'
        let workflowRoleCode: string | null = null
        let scoreWeight = 0

        if (jiraCat === 'new' || jiraCat === 'undefined') {
          statusCategory = 'NEW'
          scoreWeight = 0
        } else if (jiraCat === 'done') {
          statusCategory = 'DONE'
          scoreWeight = 100
        } else {
          // indeterminate
          const lower = st.name.toLowerCase()

          if (issueCat === 'EPIC') {
            if (lower.includes('requirement') || lower.includes('требовани')) {
              statusCategory = 'REQUIREMENTS'
              scoreWeight = 25
            } else if (lower.includes('plan') || lower.includes('заплан')) {
              statusCategory = 'PLANNED'
              scoreWeight = 50
            } else {
              statusCategory = 'IN_PROGRESS'
              scoreWeight = 75
            }
          } else {
            // STORY or SUBTASK
            if (lower.includes('analy') || lower.includes('анализ') || lower.includes('requirement') || lower.includes('требовани')) {
              workflowRoleCode = 'SA'
              scoreWeight = 25
            } else if (lower.includes('develop') || lower.includes('разработ') || lower.includes('coding') || lower.includes('implement')) {
              workflowRoleCode = 'DEV'
              scoreWeight = 50
            } else if (lower.includes('test') || lower.includes('тестирован') || lower.includes('qa') || lower.includes('review')) {
              workflowRoleCode = 'QA'
              scoreWeight = 75
            } else {
              scoreWeight = 50
            }
            statusCategory = 'IN_PROGRESS'
          }
        }

        result.push({
          id: null,
          jiraStatusName: st.name,
          issueCategory: issueCat,
          statusCategory,
          workflowRoleCode,
          sortOrder: result.length + 1,
          scoreWeight,
        })
      }
    }
  }

  return result
}

function suggestLinkTypes(jiraLinks: JiraLinkTypeMetadata[]): LinkTypeMappingDto[] {
  return jiraLinks.map(lt => {
    const lower = lt.name.toLowerCase()
    let linkCategory: LinkTypeMappingDto['linkCategory'] = 'IGNORE'

    if (lower.includes('block')) {
      linkCategory = 'BLOCKS'
    } else if (lower.includes('relat') || lower.includes('связ')) {
      linkCategory = 'RELATED'
    }

    return { id: null, jiraLinkTypeName: lt.name, linkCategory }
  })
}

// --- Component ---

export function WorkflowConfigPage() {
  const [config, setConfig] = useState<WorkflowConfigResponse | null>(null)
  const [activeTab, setActiveTab] = useState<TabKey>('roles')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [validation, setValidation] = useState<ValidationResult | null>(null)
  const [validating, setValidating] = useState(false)

  const [roles, setRoles] = useState<WorkflowRoleDto[]>([])
  const [issueTypes, setIssueTypes] = useState<IssueTypeMappingDto[]>([])
  const [statuses, setStatuses] = useState<StatusMappingDto[]>([])
  const [linkTypes, setLinkTypes] = useState<LinkTypeMappingDto[]>([])
  const [statusFilter, setStatusFilter] = useState<string>('ALL')

  // --- Auto-detect state ---
  const [autoDetecting, setAutoDetecting] = useState(false)
  const [autoDetectResult, setAutoDetectResult] = useState<AutoDetectResult | null>(null)

  // --- Wizard state ---
  const [wizardMode, setWizardMode] = useState(false)
  const [wizardStep, setWizardStep] = useState(0)
  const [wizardLoading, setWizardLoading] = useState(false)
  const [wizardError, setWizardError] = useState<string | null>(null)
  const [wizardSaving, setWizardSaving] = useState(false)
  const [wizardValidation, setWizardValidation] = useState<ValidationResult | null>(null)

  const [wizardRoles, setWizardRoles] = useState<WorkflowRoleDto[]>([])
  const [wizardIssueTypes, setWizardIssueTypes] = useState<IssueTypeMappingDto[]>([])
  const [wizardStatuses, setWizardStatuses] = useState<StatusMappingDto[]>([])
  const [wizardLinkTypes, setWizardLinkTypes] = useState<LinkTypeMappingDto[]>([])
  const [wizardJiraIssueTypes, setWizardJiraIssueTypes] = useState<JiraIssueTypeMetadata[]>([])
  const [wizardJiraLinkTypes, setWizardJiraLinkTypes] = useState<JiraLinkTypeMetadata[]>([])
  const [wizardStatusFilter, setWizardStatusFilter] = useState<string>('ALL')

  useEffect(() => {
    loadConfig()
  }, [])

  const loadConfig = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await workflowConfigApi.getConfig()
      setConfig(data)
      setRoles(data.roles)
      setIssueTypes(data.issueTypes)
      setStatuses(data.statuses)
      setLinkTypes(data.linkTypes)
    } catch (err) {
      setError('Failed to load workflow configuration')
      console.error('Failed to load config:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleAutoDetect = async () => {
    try {
      setAutoDetecting(true)
      setError(null)
      setAutoDetectResult(null)
      const result = await workflowConfigApi.runAutoDetect()
      setAutoDetectResult(result)
      await loadConfig()
      showSaveSuccess(`Auto-detected: ${result.roleCount} roles, ${result.issueTypeCount} types, ${result.statusMappingCount} statuses`)
    } catch (err: any) {
      setError(err.response?.data?.message || 'Auto-detection failed. Check Jira connection.')
    } finally {
      setAutoDetecting(false)
    }
  }

  const isConfigEmpty = roles.length === 0 && issueTypes.length === 0

  const showSaveSuccess = (msg: string) => {
    setSaveMessage(msg)
    setTimeout(() => setSaveMessage(null), 3000)
  }

  const handleSaveRoles = async () => {
    try {
      setSaving(true)
      setError(null)
      const updated = await workflowConfigApi.updateRoles(roles)
      setRoles(updated)
      showSaveSuccess('Roles saved')
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to save roles')
    } finally {
      setSaving(false)
    }
  }

  const handleSaveIssueTypes = async () => {
    try {
      setSaving(true)
      setError(null)
      const updated = await workflowConfigApi.updateIssueTypes(issueTypes)
      setIssueTypes(updated)
      showSaveSuccess('Issue types saved')
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to save issue types')
    } finally {
      setSaving(false)
    }
  }

  const handleSaveStatuses = async () => {
    try {
      setSaving(true)
      setError(null)
      const updated = await workflowConfigApi.updateStatuses(statuses)
      setStatuses(updated)
      showSaveSuccess('Statuses saved')
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to save statuses')
    } finally {
      setSaving(false)
    }
  }

  const handleSaveLinkTypes = async () => {
    try {
      setSaving(true)
      setError(null)
      const updated = await workflowConfigApi.updateLinkTypes(linkTypes)
      setLinkTypes(updated)
      showSaveSuccess('Link types saved')
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to save link types')
    } finally {
      setSaving(false)
    }
  }

  const handleValidate = async () => {
    try {
      setValidating(true)
      const result = await workflowConfigApi.validate()
      setValidation(result)
    } catch (err) {
      console.error('Validation failed:', err)
      setValidation({ valid: false, errors: ['Failed to run validation'], warnings: [] })
    } finally {
      setValidating(false)
    }
  }

  // -- Role helpers --
  const addRole = () => {
    const maxOrder = roles.reduce((max, r) => Math.max(max, r.sortOrder), 0)
    setRoles([...roles, {
      id: null, code: '', displayName: '', color: '#6B778C',
      sortOrder: maxOrder + 1, isDefault: false,
    }])
  }

  const updateRole = (index: number, field: keyof WorkflowRoleDto, value: any) => {
    setRoles(roles.map((r, i) => i === index ? { ...r, [field]: value } : r))
  }

  const deleteRole = (index: number) => {
    setRoles(roles.filter((_, i) => i !== index))
  }

  // -- Issue Type helpers --
  const addIssueType = () => {
    setIssueTypes([...issueTypes, {
      id: null, jiraTypeName: '', boardCategory: 'IGNORE', workflowRoleCode: null,
    }])
  }

  const updateIssueType = (index: number, field: keyof IssueTypeMappingDto, value: any) => {
    const updated = issueTypes.map((t, i) => {
      if (i !== index) return t
      const next = { ...t, [field]: value }
      if (field === 'boardCategory' && value !== 'SUBTASK') {
        next.workflowRoleCode = null
      }
      return next
    })
    setIssueTypes(updated)
  }

  const deleteIssueType = (index: number) => {
    setIssueTypes(issueTypes.filter((_, i) => i !== index))
  }

  // -- Status helpers --
  const addStatus = () => {
    const maxOrder = statuses.reduce((max, s) => Math.max(max, s.sortOrder), 0)
    setStatuses([...statuses, {
      id: null, jiraStatusName: '', issueCategory: 'STORY',
      statusCategory: 'NEW', workflowRoleCode: null,
      sortOrder: maxOrder + 1, scoreWeight: 0,
    }])
  }

  const updateStatus = (index: number, field: keyof StatusMappingDto, value: any) => {
    setStatuses(statuses.map((s, i) => i === index ? { ...s, [field]: value } : s))
  }

  const deleteStatus = (index: number) => {
    setStatuses(statuses.filter((_, i) => i !== index))
  }

  const filteredStatuses = statusFilter === 'ALL'
    ? statuses
    : statuses.filter(s => s.issueCategory === statusFilter)

  // -- Link Type helpers --
  const addLinkType = () => {
    setLinkTypes([...linkTypes, {
      id: null, jiraLinkTypeName: '', linkCategory: 'IGNORE',
    }])
  }

  const updateLinkType = (index: number, field: keyof LinkTypeMappingDto, value: any) => {
    setLinkTypes(linkTypes.map((l, i) => i === index ? { ...l, [field]: value } : l))
  }

  const deleteLinkType = (index: number) => {
    setLinkTypes(linkTypes.filter((_, i) => i !== index))
  }

  // --- Wizard logic ---

  const startWizard = () => {
    setWizardMode(true)
    setWizardStep(0)
    setWizardError(null)
    setWizardValidation(null)
    setWizardRoles([])
    setWizardIssueTypes([])
    setWizardStatuses([])
    setWizardLinkTypes([])
    fetchJiraMetadata()
  }

  const cancelWizard = () => {
    setWizardMode(false)
    setWizardStep(0)
    setWizardError(null)
    setWizardValidation(null)
  }

  const fetchJiraMetadata = async () => {
    try {
      setWizardLoading(true)
      setWizardError(null)

      const [jiraTypes, jiraStatuses, jiraLinks] = await Promise.all([
        workflowConfigApi.fetchJiraIssueTypes(),
        workflowConfigApi.fetchJiraStatuses(),
        workflowConfigApi.fetchJiraLinkTypes(),
      ])

      setWizardJiraIssueTypes(jiraTypes)
      setWizardJiraLinkTypes(jiraLinks)

      const suggestedTypes = suggestIssueTypes(jiraTypes)
      setWizardIssueTypes(suggestedTypes)

      const suggestedRoles = suggestRolesFromIssueTypes(suggestedTypes)
      setWizardRoles(suggestedRoles)

      const suggestedStatuses = suggestStatuses(jiraStatuses, suggestedTypes)
      setWizardStatuses(suggestedStatuses)

      const suggestedLinks = suggestLinkTypes(jiraLinks)
      setWizardLinkTypes(suggestedLinks)

      setWizardStep(1)
    } catch (err: any) {
      setWizardError(err.response?.data?.message || err.message || 'Failed to fetch Jira metadata')
    } finally {
      setWizardLoading(false)
    }
  }

  const wizardSave = async () => {
    try {
      setWizardSaving(true)
      setWizardError(null)

      await workflowConfigApi.updateRoles(wizardRoles)
      await workflowConfigApi.updateIssueTypes(wizardIssueTypes)
      await workflowConfigApi.updateStatuses(wizardStatuses)
      await workflowConfigApi.updateLinkTypes(wizardLinkTypes)

      const validationResult = await workflowConfigApi.validate()
      setWizardValidation(validationResult)

      await loadConfig()
      setWizardMode(false)
      setWizardStep(0)
      showSaveSuccess('Wizard configuration saved successfully!')
    } catch (err: any) {
      setWizardError(err.response?.data?.message || 'Failed to save configuration')
    } finally {
      setWizardSaving(false)
    }
  }

  // Wizard helpers for editing wizard-local state
  const updateWizardIssueType = (index: number, field: keyof IssueTypeMappingDto, value: any) => {
    setWizardIssueTypes(prev => prev.map((t, i) => {
      if (i !== index) return t
      const next = { ...t, [field]: value }
      if (field === 'boardCategory' && value !== 'SUBTASK') {
        next.workflowRoleCode = null
      }
      return next
    }))
  }

  const updateWizardRole = (index: number, field: keyof WorkflowRoleDto, value: any) => {
    setWizardRoles(prev => prev.map((r, i) => i === index ? { ...r, [field]: value } : r))
  }

  const addWizardRole = () => {
    const maxOrder = wizardRoles.reduce((max, r) => Math.max(max, r.sortOrder), 0)
    setWizardRoles(prev => [...prev, {
      id: null, code: '', displayName: '', color: '#6B778C',
      sortOrder: maxOrder + 1, isDefault: false,
    }])
  }

  const deleteWizardRole = (index: number) => {
    setWizardRoles(prev => prev.filter((_, i) => i !== index))
  }

  const updateWizardStatus = (index: number, field: keyof StatusMappingDto, value: any) => {
    setWizardStatuses(prev => prev.map((s, i) => i === index ? { ...s, [field]: value } : s))
  }

  const updateWizardLinkType = (index: number, field: keyof LinkTypeMappingDto, value: any) => {
    setWizardLinkTypes(prev => prev.map((l, i) => i === index ? { ...l, [field]: value } : l))
  }

  const filteredWizardStatuses = wizardStatusFilter === 'ALL'
    ? wizardStatuses
    : wizardStatuses.filter(s => s.issueCategory === wizardStatusFilter)

  // -- Rendering --

  if (loading) {
    return (
      <div className="workflow-page">
        <div className="workflow-loading">Loading workflow configuration...</div>
      </div>
    )
  }

  if (error && !config) {
    return (
      <div className="workflow-page">
        <div className="workflow-error">{error}</div>
      </div>
    )
  }

  if (wizardMode) {
    return renderWizard()
  }

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: 'roles', label: 'Roles', count: roles.length },
    { key: 'issueTypes', label: 'Issue Types', count: issueTypes.length },
    { key: 'statuses', label: 'Statuses', count: statuses.length },
    { key: 'linkTypes', label: 'Link Types', count: linkTypes.length },
  ]

  return (
    <div className="workflow-page">
      <div className="workflow-header">
        <h1 className="workflow-title">Workflow Configuration</h1>
        <div className="workflow-header-actions">
          {saveMessage && <span className="save-success">{saveMessage}</span>}
          <button className="btn btn-wizard" onClick={startWizard}>
            Setup Wizard
          </button>
          <button className="btn btn-validate" onClick={handleValidate} disabled={validating}>
            {validating ? 'Validating...' : 'Validate'}
          </button>
        </div>
      </div>

      {error && (
        <div style={{ color: '#DE350B', marginBottom: 16, padding: '8px 12px', background: '#FFEBE6', borderRadius: 4 }}>
          {error}
        </div>
      )}

      {isConfigEmpty && (
        <div className="empty-config-banner">
          <div className="empty-config-icon">&#9881;</div>
          <h2>Workflow configuration is empty</h2>
          <p>
            No roles, issue types, or status mappings configured yet.
            Auto-detect from your Jira project to get started quickly.
          </p>
          <div className="empty-config-actions">
            <button
              className="btn btn-primary"
              onClick={handleAutoDetect}
              disabled={autoDetecting}
            >
              {autoDetecting ? 'Detecting...' : 'Auto-detect from Jira'}
            </button>
            <button className="btn btn-secondary" onClick={startWizard}>
              Setup Wizard
            </button>
          </div>
          {autoDetectResult && autoDetectResult.warnings.length > 0 && (
            <div className="auto-detect-warnings">
              {autoDetectResult.warnings.map((w, i) => (
                <div key={i} className="auto-detect-warning">{w}</div>
              ))}
            </div>
          )}
        </div>
      )}

      {validation && (
        <div className={`validation-box ${validation.valid ? 'valid' : 'invalid'}`}>
          <div className="validation-title">
            {validation.valid ? 'Configuration is valid' : 'Configuration has issues'}
          </div>
          {validation.errors.length > 0 && (
            <ul className="validation-errors">
              {validation.errors.map((e, i) => <li key={i}>{e}</li>)}
            </ul>
          )}
          {validation.warnings.length > 0 && (
            <ul className="validation-warnings">
              {validation.warnings.map((w, i) => <li key={i}>{w}</li>)}
            </ul>
          )}
        </div>
      )}

      <div className="workflow-tabs">
        {tabs.map(tab => (
          <button
            key={tab.key}
            className={`workflow-tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}<span className="tab-count">({tab.count})</span>
          </button>
        ))}
      </div>

      <div className="workflow-section">
        {activeTab === 'roles' && renderRolesTab()}
        {activeTab === 'issueTypes' && renderIssueTypesTab()}
        {activeTab === 'statuses' && renderStatusesTab()}
        {activeTab === 'linkTypes' && renderLinkTypesTab()}
      </div>
    </div>
  )

  // --- Wizard rendering ---

  function renderWizard() {
    return (
      <div className="workflow-page">
        <div className="workflow-header">
          <h1 className="workflow-title">Setup Wizard</h1>
          <button className="btn btn-secondary" onClick={cancelWizard}>Cancel</button>
        </div>

        <div className="wizard-progress">
          {WIZARD_STEPS.map((label, idx) => (
            <div
              key={idx}
              className={`wizard-step-indicator ${idx === wizardStep ? 'active' : ''} ${idx < wizardStep ? 'completed' : ''}`}
            >
              <div className="wizard-step-number">{idx < wizardStep ? '\u2713' : idx + 1}</div>
              <div className="wizard-step-label">{label}</div>
            </div>
          ))}
        </div>

        {wizardError && (
          <div className="wizard-error">
            {wizardError}
            {wizardStep === 0 && (
              <button className="btn btn-secondary" style={{ marginLeft: 12 }} onClick={fetchJiraMetadata}>
                Retry
              </button>
            )}
          </div>
        )}

        <div className="workflow-section">
          {wizardStep === 0 && renderWizardFetch()}
          {wizardStep === 1 && renderWizardIssueTypes()}
          {wizardStep === 2 && renderWizardRoles()}
          {wizardStep === 3 && renderWizardStatuses()}
          {wizardStep === 4 && renderWizardLinkTypes()}
          {wizardStep === 5 && renderWizardReview()}
        </div>

        {wizardStep >= 1 && (
          <div className="wizard-nav">
            <button
              className="btn btn-secondary"
              onClick={() => setWizardStep(s => s - 1)}
              disabled={wizardStep <= 1}
            >
              Back
            </button>
            <div className="wizard-nav-spacer" />
            {wizardStep < 5 ? (
              <button className="btn btn-primary" onClick={() => setWizardStep(s => s + 1)}>
                Next
              </button>
            ) : (
              <button className="btn btn-wizard" onClick={wizardSave} disabled={wizardSaving}>
                {wizardSaving ? 'Saving...' : 'Save Configuration'}
              </button>
            )}
          </div>
        )}
      </div>
    )
  }

  function renderWizardFetch() {
    if (wizardLoading) {
      return (
        <div className="wizard-fetch">
          <div className="wizard-spinner" />
          <div>Fetching metadata from Jira...</div>
          <div className="wizard-step-hint">Loading issue types, statuses, and link types from your Jira project</div>
        </div>
      )
    }
    if (wizardError) {
      return (
        <div className="wizard-fetch">
          <div className="wizard-step-hint">Could not connect to Jira. Check your credentials and try again.</div>
        </div>
      )
    }
    return null
  }

  function renderWizardIssueTypes() {
    return (
      <>
        <div className="wizard-step-hint">
          Review how Jira issue types map to board categories. Subtasks need a workflow role assignment.
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Jira Type</th>
                <th>Subtask</th>
                <th>Board Category</th>
                <th>Role</th>
              </tr>
            </thead>
            <tbody>
              {wizardIssueTypes.map((it, idx) => {
                const jiraMeta = wizardJiraIssueTypes.find(j => j.name === it.jiraTypeName)
                return (
                  <tr key={idx}>
                    <td><strong>{it.jiraTypeName}</strong></td>
                    <td>
                      {jiraMeta?.subtask
                        ? <span className="jira-badge subtask">Subtask</span>
                        : <span className="jira-badge standard">Standard</span>
                      }
                    </td>
                    <td>
                      <select
                        className="workflow-select"
                        value={it.boardCategory}
                        onChange={e => updateWizardIssueType(idx, 'boardCategory', e.target.value)}
                      >
                        {BOARD_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </td>
                    <td>
                      {it.boardCategory === 'SUBTASK' ? (
                        <select
                          className="workflow-select"
                          value={it.workflowRoleCode || ''}
                          onChange={e => updateWizardIssueType(idx, 'workflowRoleCode', e.target.value || null)}
                        >
                          <option value="">-- none --</option>
                          {wizardRoles.map(r => (
                            <option key={r.code} value={r.code}>{r.displayName || r.code}</option>
                          ))}
                        </select>
                      ) : (
                        <span style={{ color: '#6B778C', fontSize: 13 }}>N/A</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </>
    )
  }

  function renderWizardRoles() {
    return (
      <>
        <div className="wizard-step-hint">
          Define workflow roles. These are used to track progress by role (e.g. SA, DEV, QA).
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Display Name</th>
                <th>Color</th>
                <th>Sort Order</th>
                <th>Default</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {wizardRoles.map((role, idx) => (
                <tr key={idx}>
                  <td>
                    <input
                      className="workflow-input"
                      value={role.code}
                      onChange={e => updateWizardRole(idx, 'code', e.target.value.toUpperCase())}
                      placeholder="e.g. DEV"
                      style={{ width: 100 }}
                    />
                  </td>
                  <td>
                    <input
                      className="workflow-input"
                      value={role.displayName}
                      onChange={e => updateWizardRole(idx, 'displayName', e.target.value)}
                      placeholder="e.g. Development"
                    />
                  </td>
                  <td>
                    <div className="color-cell">
                      <input
                        type="color"
                        className="workflow-input"
                        value={role.color}
                        onChange={e => updateWizardRole(idx, 'color', e.target.value)}
                      />
                      <div className="color-preview" style={{ backgroundColor: role.color }} />
                    </div>
                  </td>
                  <td>
                    <input
                      type="number"
                      className="workflow-input"
                      value={role.sortOrder}
                      onChange={e => updateWizardRole(idx, 'sortOrder', parseInt(e.target.value) || 0)}
                      min={0}
                    />
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      className="workflow-checkbox"
                      checked={role.isDefault}
                      onChange={e => updateWizardRole(idx, 'isDefault', e.target.checked)}
                    />
                  </td>
                  <td>
                    <button className="btn-danger-text" onClick={() => deleteWizardRole(idx)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {wizardRoles.length === 0 && (
                <tr><td colSpan={6} style={{ textAlign: 'center', color: '#6B778C' }}>No roles configured</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="workflow-actions">
          <button className="btn btn-secondary" onClick={addWizardRole}>Add Role</button>
        </div>
      </>
    )
  }

  function renderWizardStatuses() {
    return (
      <>
        <div className="wizard-step-hint">
          Review status mappings. Each Jira status is mapped to a category and optionally a role.
        </div>
        <div className="status-filter">
          {['ALL', 'EPIC', 'STORY', 'SUBTASK'].map(f => (
            <button
              key={f}
              className={`status-filter-btn ${wizardStatusFilter === f ? 'active' : ''}`}
              onClick={() => setWizardStatusFilter(f)}
            >
              {f === 'ALL'
                ? `All (${wizardStatuses.length})`
                : `${f} (${wizardStatuses.filter(s => s.issueCategory === f).length})`
              }
            </button>
          ))}
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Jira Status</th>
                <th>Issue Category</th>
                <th>Status Category</th>
                <th>Role</th>
                <th>Sort Order</th>
                <th>Score Weight</th>
              </tr>
            </thead>
            <tbody>
              {filteredWizardStatuses.map((st) => {
                const realIdx = wizardStatuses.indexOf(st)
                return (
                  <tr key={realIdx}>
                    <td><strong>{st.jiraStatusName}</strong></td>
                    <td>
                      <select
                        className="workflow-select"
                        value={st.issueCategory}
                        onChange={e => updateWizardStatus(realIdx, 'issueCategory', e.target.value)}
                      >
                        {BOARD_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </td>
                    <td>
                      <select
                        className="workflow-select"
                        value={st.statusCategory}
                        onChange={e => updateWizardStatus(realIdx, 'statusCategory', e.target.value)}
                      >
                        {STATUS_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </td>
                    <td>
                      <select
                        className="workflow-select"
                        value={st.workflowRoleCode || ''}
                        onChange={e => updateWizardStatus(realIdx, 'workflowRoleCode', e.target.value || null)}
                      >
                        <option value="">-- none --</option>
                        {wizardRoles.map(r => (
                          <option key={r.code} value={r.code}>{r.displayName || r.code}</option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input
                        type="number"
                        className="workflow-input"
                        value={st.sortOrder}
                        onChange={e => updateWizardStatus(realIdx, 'sortOrder', parseInt(e.target.value) || 0)}
                        min={0}
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        className="workflow-input"
                        value={st.scoreWeight}
                        onChange={e => updateWizardStatus(realIdx, 'scoreWeight', parseInt(e.target.value) || 0)}
                        min={0}
                        max={100}
                      />
                    </td>
                  </tr>
                )
              })}
              {filteredWizardStatuses.length === 0 && (
                <tr><td colSpan={6} style={{ textAlign: 'center', color: '#6B778C' }}>No statuses for this filter</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </>
    )
  }

  function renderWizardLinkTypes() {
    return (
      <>
        <div className="wizard-step-hint">
          Review link type mappings. "Blocks" links are used for dependency tracking.
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Jira Link Type</th>
                <th>Inward</th>
                <th>Outward</th>
                <th>Category</th>
              </tr>
            </thead>
            <tbody>
              {wizardLinkTypes.map((lt, idx) => {
                const jiraMeta = wizardJiraLinkTypes.find(j => j.name === lt.jiraLinkTypeName)
                return (
                  <tr key={idx}>
                    <td><strong>{lt.jiraLinkTypeName}</strong></td>
                    <td style={{ color: '#6B778C', fontSize: 13 }}>{jiraMeta?.inward || '-'}</td>
                    <td style={{ color: '#6B778C', fontSize: 13 }}>{jiraMeta?.outward || '-'}</td>
                    <td>
                      <select
                        className="workflow-select"
                        value={lt.linkCategory}
                        onChange={e => updateWizardLinkType(idx, 'linkCategory', e.target.value)}
                      >
                        {LINK_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </>
    )
  }

  function renderWizardReview() {
    const activeIssueTypes = wizardIssueTypes.filter(t => t.boardCategory !== 'IGNORE')
    const activeLinks = wizardLinkTypes.filter(l => l.linkCategory !== 'IGNORE')

    return (
      <>
        <div className="wizard-step-hint">
          Review the summary below, then save to apply your configuration.
        </div>
        <div className="wizard-review">
          <div className="wizard-review-card">
            <div className="wizard-review-count">{wizardRoles.length}</div>
            <div className="wizard-review-label">Roles</div>
            <div className="wizard-review-detail">{wizardRoles.map(r => r.code).join(', ')}</div>
          </div>
          <div className="wizard-review-card">
            <div className="wizard-review-count">{activeIssueTypes.length}</div>
            <div className="wizard-review-label">Active Issue Types</div>
            <div className="wizard-review-detail">
              {activeIssueTypes.map(t => `${t.jiraTypeName} (${t.boardCategory})`).join(', ')}
            </div>
          </div>
          <div className="wizard-review-card">
            <div className="wizard-review-count">{wizardStatuses.length}</div>
            <div className="wizard-review-label">Status Mappings</div>
            <div className="wizard-review-detail">
              EPIC: {wizardStatuses.filter(s => s.issueCategory === 'EPIC').length},
              STORY: {wizardStatuses.filter(s => s.issueCategory === 'STORY').length},
              SUBTASK: {wizardStatuses.filter(s => s.issueCategory === 'SUBTASK').length}
            </div>
          </div>
          <div className="wizard-review-card">
            <div className="wizard-review-count">{activeLinks.length}</div>
            <div className="wizard-review-label">Active Link Types</div>
            <div className="wizard-review-detail">
              {activeLinks.map(l => `${l.jiraLinkTypeName} (${l.linkCategory})`).join(', ')}
            </div>
          </div>
        </div>

        {wizardValidation && (
          <div className={`validation-box ${wizardValidation.valid ? 'valid' : 'invalid'}`} style={{ marginTop: 16 }}>
            <div className="validation-title">
              {wizardValidation.valid ? 'Configuration is valid' : 'Configuration has issues'}
            </div>
            {wizardValidation.errors.length > 0 && (
              <ul className="validation-errors">
                {wizardValidation.errors.map((e, i) => <li key={i}>{e}</li>)}
              </ul>
            )}
            {wizardValidation.warnings.length > 0 && (
              <ul className="validation-warnings">
                {wizardValidation.warnings.map((w, i) => <li key={i}>{w}</li>)}
              </ul>
            )}
          </div>
        )}
      </>
    )
  }

  // --- Tab rendering ---

  function renderRolesTab() {
    return (
      <>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Display Name</th>
                <th>Color</th>
                <th>Sort Order</th>
                <th>Default</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {roles.map((role, idx) => (
                <tr key={role.id ?? `new-${idx}`}>
                  <td>
                    <input
                      className="workflow-input"
                      value={role.code}
                      onChange={e => updateRole(idx, 'code', e.target.value.toUpperCase())}
                      placeholder="e.g. DEV"
                      style={{ width: 100 }}
                    />
                  </td>
                  <td>
                    <input
                      className="workflow-input"
                      value={role.displayName}
                      onChange={e => updateRole(idx, 'displayName', e.target.value)}
                      placeholder="e.g. Development"
                    />
                  </td>
                  <td>
                    <div className="color-cell">
                      <input
                        type="color"
                        className="workflow-input"
                        value={role.color}
                        onChange={e => updateRole(idx, 'color', e.target.value)}
                      />
                      <div className="color-preview" style={{ backgroundColor: role.color }} />
                    </div>
                  </td>
                  <td>
                    <input
                      type="number"
                      className="workflow-input"
                      value={role.sortOrder}
                      onChange={e => updateRole(idx, 'sortOrder', parseInt(e.target.value) || 0)}
                      min={0}
                    />
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      className="workflow-checkbox"
                      checked={role.isDefault}
                      onChange={e => updateRole(idx, 'isDefault', e.target.checked)}
                    />
                  </td>
                  <td>
                    <button className="btn-danger-text" onClick={() => deleteRole(idx)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {roles.length === 0 && (
                <tr><td colSpan={6} style={{ textAlign: 'center', color: '#6B778C' }}>No roles configured</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="workflow-actions">
          <button className="btn btn-secondary" onClick={addRole}>Add Role</button>
          <button className="btn btn-primary" onClick={handleSaveRoles} disabled={saving}>
            {saving ? 'Saving...' : 'Save Roles'}
          </button>
        </div>
      </>
    )
  }

  function renderIssueTypesTab() {
    return (
      <>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Jira Type Name</th>
                <th>Board Category</th>
                <th>Workflow Role</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {issueTypes.map((it, idx) => (
                <tr key={it.id ?? `new-${idx}`}>
                  <td>
                    <input
                      className="workflow-input"
                      value={it.jiraTypeName}
                      onChange={e => updateIssueType(idx, 'jiraTypeName', e.target.value)}
                      placeholder="e.g. Story"
                    />
                  </td>
                  <td>
                    <select
                      className="workflow-select"
                      value={it.boardCategory}
                      onChange={e => updateIssueType(idx, 'boardCategory', e.target.value)}
                    >
                      {BOARD_CATEGORIES.map(c => (
                        <option key={c} value={c}>{c}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    {it.boardCategory === 'SUBTASK' ? (
                      <select
                        className="workflow-select"
                        value={it.workflowRoleCode || ''}
                        onChange={e => updateIssueType(idx, 'workflowRoleCode', e.target.value || null)}
                      >
                        <option value="">-- none --</option>
                        {roles.map(r => (
                          <option key={r.code} value={r.code}>{r.displayName || r.code}</option>
                        ))}
                      </select>
                    ) : (
                      <span style={{ color: '#6B778C', fontSize: 13 }}>N/A</span>
                    )}
                  </td>
                  <td>
                    <button className="btn-danger-text" onClick={() => deleteIssueType(idx)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {issueTypes.length === 0 && (
                <tr><td colSpan={4} style={{ textAlign: 'center', color: '#6B778C' }}>No issue types configured</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="workflow-actions">
          <button className="btn btn-secondary" onClick={addIssueType}>Add Issue Type</button>
          <button className="btn btn-primary" onClick={handleSaveIssueTypes} disabled={saving}>
            {saving ? 'Saving...' : 'Save Issue Types'}
          </button>
        </div>
      </>
    )
  }

  function renderStatusesTab() {
    return (
      <>
        <div className="status-filter">
          {['ALL', 'EPIC', 'STORY', 'SUBTASK'].map(f => (
            <button
              key={f}
              className={`status-filter-btn ${statusFilter === f ? 'active' : ''}`}
              onClick={() => setStatusFilter(f)}
            >
              {f === 'ALL' ? `All (${statuses.length})` : `${f} (${statuses.filter(s => s.issueCategory === f).length})`}
            </button>
          ))}
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Jira Status</th>
                <th>Issue Category</th>
                <th>Status Category</th>
                <th>Role</th>
                <th>Sort Order</th>
                <th>Score Weight</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredStatuses.map((st) => {
                const realIdx = statuses.indexOf(st)
                return (
                  <tr key={st.id ?? `new-${realIdx}`}>
                    <td>
                      <input
                        className="workflow-input"
                        value={st.jiraStatusName}
                        onChange={e => updateStatus(realIdx, 'jiraStatusName', e.target.value)}
                        placeholder="e.g. In Progress"
                      />
                    </td>
                    <td>
                      <select
                        className="workflow-select"
                        value={st.issueCategory}
                        onChange={e => updateStatus(realIdx, 'issueCategory', e.target.value)}
                      >
                        {BOARD_CATEGORIES.map(c => (
                          <option key={c} value={c}>{c}</option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <select
                        className="workflow-select"
                        value={st.statusCategory}
                        onChange={e => updateStatus(realIdx, 'statusCategory', e.target.value)}
                      >
                        {STATUS_CATEGORIES.map(c => (
                          <option key={c} value={c}>{c}</option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <select
                        className="workflow-select"
                        value={st.workflowRoleCode || ''}
                        onChange={e => updateStatus(realIdx, 'workflowRoleCode', e.target.value || null)}
                      >
                        <option value="">-- none --</option>
                        {roles.map(r => (
                          <option key={r.code} value={r.code}>{r.displayName || r.code}</option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input
                        type="number"
                        className="workflow-input"
                        value={st.sortOrder}
                        onChange={e => updateStatus(realIdx, 'sortOrder', parseInt(e.target.value) || 0)}
                        min={0}
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        className="workflow-input"
                        value={st.scoreWeight}
                        onChange={e => updateStatus(realIdx, 'scoreWeight', parseInt(e.target.value) || 0)}
                        min={0}
                        max={100}
                      />
                    </td>
                    <td>
                      <button className="btn-danger-text" onClick={() => deleteStatus(realIdx)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                )
              })}
              {filteredStatuses.length === 0 && (
                <tr><td colSpan={7} style={{ textAlign: 'center', color: '#6B778C' }}>No statuses for this filter</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="workflow-actions">
          <button className="btn btn-secondary" onClick={addStatus}>Add Status</button>
          <button className="btn btn-primary" onClick={handleSaveStatuses} disabled={saving}>
            {saving ? 'Saving...' : 'Save Statuses'}
          </button>
        </div>
      </>
    )
  }

  function renderLinkTypesTab() {
    return (
      <>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Jira Link Type</th>
                <th>Category</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {linkTypes.map((lt, idx) => (
                <tr key={lt.id ?? `new-${idx}`}>
                  <td>
                    <input
                      className="workflow-input"
                      value={lt.jiraLinkTypeName}
                      onChange={e => updateLinkType(idx, 'jiraLinkTypeName', e.target.value)}
                      placeholder="e.g. Blocks"
                    />
                  </td>
                  <td>
                    <select
                      className="workflow-select"
                      value={lt.linkCategory}
                      onChange={e => updateLinkType(idx, 'linkCategory', e.target.value)}
                    >
                      {LINK_CATEGORIES.map(c => (
                        <option key={c} value={c}>{c}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <button className="btn-danger-text" onClick={() => deleteLinkType(idx)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {linkTypes.length === 0 && (
                <tr><td colSpan={3} style={{ textAlign: 'center', color: '#6B778C' }}>No link types configured</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="workflow-actions">
          <button className="btn btn-secondary" onClick={addLinkType}>Add Link Type</button>
          <button className="btn btn-primary" onClick={handleSaveLinkTypes} disabled={saving}>
            {saving ? 'Saving...' : 'Save Link Types'}
          </button>
        </div>
      </>
    )
  }
}
