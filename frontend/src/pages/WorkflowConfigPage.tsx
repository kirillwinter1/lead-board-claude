import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import {
  workflowConfigApi,
  WorkflowConfigResponse,
  WorkflowRoleDto,
  IssueTypeMappingDto,
  StatusMappingDto,
  LinkTypeMappingDto,
  ValidationResult,
  JiraIssueTypeMetadata,
  JiraStatusesByType,
  JiraLinkTypeMetadata,
  StatusIssueCountDto,
} from '../api/workflowConfig'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import './WorkflowConfigPage.css'

type TabKey = 'roles' | 'issueTypes' | 'statuses' | 'linkTypes'

const BOARD_CATEGORIES = ['PROJECT', 'EPIC', 'STORY', 'SUBTASK', 'IGNORE'] as const
const STATUS_CATEGORIES = ['NEW', 'REQUIREMENTS', 'PLANNED', 'IN_PROGRESS', 'DONE'] as const
const LINK_CATEGORIES = ['BLOCKS', 'RELATED', 'IGNORE'] as const

const WIZARD_STEPS = ['Fetch', 'Roles', 'Issue Types', 'Statuses', 'Link Types', 'Review & Save']

const ATLASSIAN_COLORS = [
  { hex: '#1868DB', name: 'Blue' },
  { hex: '#227D9B', name: 'Teal' },
  { hex: '#1F845A', name: 'Green' },
  { hex: '#82B536', name: 'Lime' },
  { hex: '#946F00', name: 'Yellow' },
  { hex: '#E06C00', name: 'Orange' },
  { hex: '#F15B50', name: 'Red' },
  { hex: '#964AC0', name: 'Purple' },
  { hex: '#CD519D', name: 'Magenta' },
] as const

const STATUS_BG_COLORS = [
  { hex: '#DFE1E6', name: 'Gray' },
  { hex: '#DEEBFF', name: 'Blue' },
  { hex: '#E6FCFF', name: 'Teal' },
  { hex: '#E3FCEF', name: 'Green' },
  { hex: '#EAE6FF', name: 'Purple' },
  { hex: '#FFF0B3', name: 'Yellow' },
  { hex: '#FFEBE6', name: 'Red' },
  { hex: '#F3E8FF', name: 'Lavender' },
  { hex: '#E0F2FE', name: 'Sky' },
  { hex: '#FEF3C7', name: 'Amber' },
  { hex: '#FCE7F3', name: 'Pink' },
  { hex: '#D1FAE5', name: 'Mint' },
  { hex: '#FED7AA', name: 'Peach' },
] as const

const STATUS_CATEGORY_DEFAULT_COLORS: Record<string, string> = {
  NEW: '#DFE1E6',
  REQUIREMENTS: '#E6FCFF',
  PLANNED: '#EAE6FF',
  IN_PROGRESS: '#DEEBFF',
  DONE: '#E3FCEF',
}

function ColorPicker({ value, onChange }: { value: string; onChange: (color: string) => void }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="color-picker">
      <button
        className="color-picker-trigger"
        style={{ backgroundColor: value }}
        onClick={() => setOpen(!open)}
        type="button"
        title="Choose color"
      />
      {open && (
        <>
          <div className="color-picker-backdrop" onClick={() => setOpen(false)} />
          <div className="color-picker-dropdown">
            {ATLASSIAN_COLORS.map(c => (
              <button
                key={c.hex}
                className={`color-swatch ${value.toUpperCase() === c.hex.toUpperCase() ? 'selected' : ''}`}
                style={{ backgroundColor: c.hex }}
                title={c.name}
                onClick={() => { onChange(c.hex); setOpen(false) }}
                type="button"
              />
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function StatusColorPicker({ value, onChange }: { value: string; onChange: (color: string) => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const [pos, setPos] = useState({ top: 0, left: 0 })

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const handleOpen = () => {
    if (!open && triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect()
      setPos({ top: rect.bottom + 4, left: rect.left })
    }
    setOpen(!open)
  }

  return (
    <div className="color-picker" ref={ref}>
      <button
        ref={triggerRef}
        className="color-picker-trigger"
        style={{ backgroundColor: value, width: 22, height: 22 }}
        onClick={handleOpen}
        type="button"
        title="Choose color"
      />
      {open && (
        <div className="color-picker-dropdown" style={{ top: pos.top, left: pos.left }}>
          {STATUS_BG_COLORS.map(c => (
            <button
              key={c.hex}
              className={`color-swatch ${value.toUpperCase() === c.hex.toUpperCase() ? 'selected' : ''}`}
              style={{ backgroundColor: c.hex }}
              title={c.name}
              onClick={() => onChange(c.hex)}
              type="button"
            />
          ))}
        </div>
      )}
    </div>
  )
}

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
    } else if (lower.includes('project') || lower.includes('проект')) {
      boardCategory = 'PROJECT'
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
    SA: { displayName: 'System Analysis', color: '#1868DB', order: 1, isDefault: false },
    DEV: { displayName: 'Development', color: '#1F845A', order: 2, isDefault: true },
    QA: { displayName: 'Quality Assurance', color: '#227D9B', order: 3, isDefault: false },
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

/**
 * Recalculate scoreWeight for all statuses in a category based on their sortOrder position.
 * First status (NEW) = -5, last status (DONE) = 0, intermediate = linearly from 5 to 30.
 */
function recalcScoreWeights(allStatuses: StatusMappingDto[], category: string): StatusMappingDto[] {
  const catStatuses = allStatuses
    .filter(s => s.issueCategory === category)
    .sort((a, b) => a.sortOrder - b.sortOrder)
  const maxLevel = catStatuses.length - 1

  const weightByName = new Map<string, number>()
  catStatuses.forEach((s, level) => {
    let w: number
    if (maxLevel === 0) {
      w = -5 // single status
    } else if (level === 0) {
      w = -5 // first = NEW
    } else if (level === maxLevel) {
      w = 0 // last = DONE
    } else if (maxLevel <= 2) {
      w = 15 // only one intermediate status
    } else {
      // Linearly interpolate 5..30 based on position
      const ratio = (level - 1) / (maxLevel - 2)
      w = 5 + Math.round(ratio * 25)
    }
    weightByName.set(s.jiraStatusName, w)
  })

  return allStatuses.map(s => {
    if (s.issueCategory === category && weightByName.has(s.jiraStatusName)) {
      return { ...s, scoreWeight: weightByName.get(s.jiraStatusName)! }
    }
    return s
  })
}

function suggestStatuses(
  jiraStatuses: JiraStatusesByType[],
  suggestedIssueTypes: IssueTypeMappingDto[],
  jiraIssueTypes: JiraIssueTypeMetadata[],
): StatusMappingDto[] {
  const result: StatusMappingDto[] = []
  const seen = new Set<string>()
  const categoryCounter: Record<string, number> = { PROJECT: 0, EPIC: 0, STORY: 0, SUBTASK: 0 }

  // Build mapping: issueTypeId → boardCategory (using jiraIssueTypes to bridge name→id)
  const nameToId = new Map<string, string>()
  jiraIssueTypes.forEach(jt => nameToId.set(jt.name, jt.id))

  const issueTypeIdMap = new Map<string, IssueTypeMappingDto['boardCategory']>()
  suggestedIssueTypes.forEach(t => {
    const id = nameToId.get(t.jiraTypeName)
    if (id) issueTypeIdMap.set(id, t.boardCategory)
  })

  const issueCategories: IssueTypeMappingDto['boardCategory'][] = ['PROJECT', 'EPIC', 'STORY', 'SUBTASK']

  for (const group of jiraStatuses) {
    const boardCat = issueTypeIdMap.get(group.issueTypeId)
    if (!boardCat || boardCat === 'IGNORE') continue

    for (const st of group.statuses) {
      for (const issueCat of issueCategories) {
        if (issueCat === 'SUBTASK' && boardCat !== 'SUBTASK') continue
        if (issueCat === 'PROJECT' && boardCat !== 'PROJECT') continue
        if (issueCat === 'EPIC' && boardCat !== 'EPIC') continue
        if (issueCat === 'STORY' && boardCat !== 'STORY') continue

        const key = `${st.name}|${issueCat}`
        if (seen.has(key)) continue
        seen.add(key)

        const jiraCat = st.statusCategory?.toLowerCase() || ''
        let statusCategory: StatusMappingDto['statusCategory'] = 'IN_PROGRESS'
        let workflowRoleCode: string | null = null

        if (jiraCat === 'new' || jiraCat === 'undefined') {
          statusCategory = 'NEW'
        } else if (jiraCat === 'done') {
          statusCategory = 'DONE'
        } else {
          const lower = st.name.toLowerCase()

          if (issueCat === 'EPIC' || issueCat === 'PROJECT') {
            if (lower.includes('requirement') || lower.includes('требовани')) {
              statusCategory = 'REQUIREMENTS'
            } else if (lower.includes('plan') || lower.includes('заплан')) {
              statusCategory = 'PLANNED'
            } else {
              statusCategory = 'IN_PROGRESS'
            }
          } else {
            if (lower.includes('analy') || lower.includes('анализ') || lower.includes('requirement') || lower.includes('требовани')) {
              workflowRoleCode = 'SA'
            } else if (lower.includes('develop') || lower.includes('разработ') || lower.includes('coding') || lower.includes('implement')) {
              workflowRoleCode = 'DEV'
            } else if (lower.includes('test') || lower.includes('тестирован') || lower.includes('qa') || lower.includes('review')) {
              workflowRoleCode = 'QA'
            }
            statusCategory = 'IN_PROGRESS'
          }
        }

        categoryCounter[issueCat] = (categoryCounter[issueCat] || 0) + 10
        result.push({
          id: null,
          jiraStatusName: st.name,
          issueCategory: issueCat,
          statusCategory,
          workflowRoleCode,
          sortOrder: categoryCounter[issueCat],
          scoreWeight: 0,
          color: STATUS_CATEGORY_DEFAULT_COLORS[statusCategory] || '#DFE1E6',
        })
      }
    }
  }

  // Fallback: if EPIC or STORY have 0 statuses, copy from the first non-empty category
  const epicStatuses = result.filter(s => s.issueCategory === 'EPIC')
  const storyStatuses = result.filter(s => s.issueCategory === 'STORY')
  const allUniqueStatuses = [...new Map(result.map(s => [s.jiraStatusName, s])).values()]

  if (epicStatuses.length === 0 && allUniqueStatuses.length > 0) {
    for (const src of allUniqueStatuses) {
      const key = `${src.jiraStatusName}|EPIC`
      if (seen.has(key)) continue
      seen.add(key)

      const lower = src.jiraStatusName.toLowerCase()
      let statusCategory = src.statusCategory
      if (statusCategory === 'IN_PROGRESS') {
        if (lower.includes('requirement') || lower.includes('требовани')) {
          statusCategory = 'REQUIREMENTS'
        } else if (lower.includes('plan') || lower.includes('заплан')) {
          statusCategory = 'PLANNED'
        }
      }

      categoryCounter['EPIC'] = (categoryCounter['EPIC'] || 0) + 10
      result.push({
        id: null,
        jiraStatusName: src.jiraStatusName,
        issueCategory: 'EPIC',
        statusCategory,
        workflowRoleCode: null,
        sortOrder: categoryCounter['EPIC'],
        scoreWeight: 0,
        color: STATUS_CATEGORY_DEFAULT_COLORS[statusCategory] || '#DFE1E6',
      })
    }
  }

  if (storyStatuses.length === 0 && allUniqueStatuses.length > 0) {
    for (const src of allUniqueStatuses) {
      const key = `${src.jiraStatusName}|STORY`
      if (seen.has(key)) continue
      seen.add(key)

      categoryCounter['STORY'] = (categoryCounter['STORY'] || 0) + 10
      result.push({
        id: null,
        jiraStatusName: src.jiraStatusName,
        issueCategory: 'STORY',
        statusCategory: src.statusCategory === 'REQUIREMENTS' || src.statusCategory === 'PLANNED' ? 'IN_PROGRESS' : src.statusCategory,
        workflowRoleCode: src.workflowRoleCode,
        sortOrder: categoryCounter['STORY'],
        scoreWeight: 0,
        color: STATUS_CATEGORY_DEFAULT_COLORS[src.statusCategory === 'REQUIREMENTS' || src.statusCategory === 'PLANNED' ? 'IN_PROGRESS' : src.statusCategory] || '#DFE1E6',
      })
    }
  }

  // Recalculate scoreWeight by position for all categories
  let final = result
  for (const cat of ['PROJECT', 'EPIC', 'STORY', 'SUBTASK']) {
    final = recalcScoreWeights(final, cat)
  }
  return final
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

interface WorkflowConfigPageProps {
  onComplete?: () => void
}

export function WorkflowConfigPage({ onComplete }: WorkflowConfigPageProps = {}) {
  const { refresh: refreshWorkflowContext } = useWorkflowConfig()
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
  const [statusFilter, setStatusFilter] = useState<string>('EPIC')
  const [issueCounts, setIssueCounts] = useState<StatusIssueCountDto[]>([])
  const [epicLinkType, setEpicLinkType] = useState<string>('parent')
  const [epicLinkName, setEpicLinkName] = useState<string>('')

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
  const [wizardStatusFilter, setWizardStatusFilter] = useState<string>('EPIC')

  useEffect(() => {
    loadConfig()
  }, [])

  // Auto-start wizard when config is empty
  useEffect(() => {
    if (!loading && isConfigEmpty && !wizardMode) {
      startWizard()
    }
  }, [loading])


  const loadConfig = async () => {
    try {
      setLoading(true)
      setError(null)
      const [data, counts] = await Promise.all([
        workflowConfigApi.getConfig(),
        workflowConfigApi.getStatusIssueCounts().catch(() => [] as StatusIssueCountDto[]),
      ])
      setConfig(data)
      setRoles(data.roles)
      setIssueTypes(data.issueTypes)
      setStatuses(data.statuses)
      setLinkTypes(data.linkTypes)
      setIssueCounts(counts)
      setEpicLinkType(data.epicLinkType || 'parent')
      setEpicLinkName(data.epicLinkName || '')
    } catch (err) {
      setError('Failed to load workflow configuration')
      console.error('Failed to load config:', err)
    } finally {
      setLoading(false)
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
      refreshWorkflowContext()
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
      refreshWorkflowContext()
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
      id: null, jiraStatusName: '', issueCategory: statusFilter === 'EPIC' || statusFilter === 'STORY' || statusFilter === 'SUBTASK' ? statusFilter as any : 'STORY',
      statusCategory: 'NEW', workflowRoleCode: null,
      sortOrder: maxOrder + 1, scoreWeight: 0,
      color: STATUS_CATEGORY_DEFAULT_COLORS['NEW'],
    }])
  }

  const updateStatus = (index: number, field: keyof StatusMappingDto, value: any) => {
    setStatuses(statuses.map((s, i) => {
      if (i !== index) return s
      const next = { ...s, [field]: value }
      // Auto-update color when statusCategory changes, if current color is a default
      if (field === 'statusCategory') {
        const oldDefault = STATUS_CATEGORY_DEFAULT_COLORS[s.statusCategory]
        if (!s.color || s.color === oldDefault) {
          next.color = STATUS_CATEGORY_DEFAULT_COLORS[value] || s.color
        }
      }
      return next
    }))
  }

  const deleteStatus = (index: number) => {
    setStatuses(statuses.filter((_, i) => i !== index))
  }

  const filteredStatuses = statuses.filter(s => s.issueCategory === statusFilter)

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

  // -- Move status left/right --
  const moveStatus = (
    allStatuses: StatusMappingDto[],
    setFn: (s: StatusMappingDto[]) => void,
    category: string,
    statusName: string,
    direction: -1 | 1,
  ) => {
    const catStatuses = allStatuses
      .filter(s => s.issueCategory === category)
      .sort((a, b) => a.sortOrder - b.sortOrder)
    const idx = catStatuses.findIndex(s => s.jiraStatusName === statusName)
    if (idx < 0) return
    const targetIdx = idx + direction
    if (targetIdx < 0 || targetIdx >= catStatuses.length) return
    // Swap sortOrders
    const currentOrder = catStatuses[idx].sortOrder
    const targetOrder = catStatuses[targetIdx].sortOrder
    const swapped = allStatuses.map(s => {
      if (s.issueCategory === category && s.jiraStatusName === catStatuses[idx].jiraStatusName) {
        return { ...s, sortOrder: targetOrder }
      }
      if (s.issueCategory === category && s.jiraStatusName === catStatuses[targetIdx].jiraStatusName) {
        return { ...s, sortOrder: currentOrder }
      }
      return s
    })
    // Recalculate scoreWeight by position for this category
    setFn(recalcScoreWeights(swapped, category))
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
    if (onComplete) {
      onComplete()
      return
    }
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

      const suggestedStatuses = suggestStatuses(jiraStatuses, suggestedTypes, jiraTypes)
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
      refreshWorkflowContext()
      setWizardMode(false)
      setWizardStep(0)
      if (onComplete) {
        onComplete()
      } else {
        showSaveSuccess('Wizard configuration saved successfully!')
      }
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
    setWizardStatuses(prev => prev.map((s, i) => {
      if (i !== index) return s
      const next = { ...s, [field]: value }
      if (field === 'statusCategory') {
        const oldDefault = STATUS_CATEGORY_DEFAULT_COLORS[s.statusCategory]
        if (!s.color || s.color === oldDefault) {
          next.color = STATUS_CATEGORY_DEFAULT_COLORS[value] || s.color
        }
      }
      return next
    }))
  }

  const updateWizardLinkType = (index: number, field: keyof LinkTypeMappingDto, value: any) => {
    setWizardLinkTypes(prev => prev.map((l, i) => i === index ? { ...l, [field]: value } : l))
  }

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
        <h1 className="workflow-title">
          Workflow Configuration{config?.projectKey ? ` — ${config.projectKey}` : ''}
        </h1>
        <div className="workflow-header-actions">
          {saveMessage && <span className="save-success">{saveMessage}</span>}
          <button className="btn btn-secondary" onClick={startWizard}>
            Re-run Wizard
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
          <h1 className="workflow-title">Workflow Setup</h1>
          <button className="btn btn-secondary" onClick={cancelWizard}>{onComplete ? 'Skip' : 'Cancel'}</button>
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
          {wizardStep === 1 && renderWizardRoles()}
          {wizardStep === 2 && renderWizardIssueTypes()}
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
          <div>Connecting to Jira...</div>
          <div className="wizard-step-hint">Fetching issue types, statuses & link types from your Jira project.</div>
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
    const subtaskTypes = wizardIssueTypes
      .map((it, idx) => ({ it, idx }))
      .filter(({ it }) => wizardJiraIssueTypes.find(j => j.name === it.jiraTypeName)?.subtask)
    const standardTypes = wizardIssueTypes
      .map((it, idx) => ({ it, idx }))
      .filter(({ it }) => !wizardJiraIssueTypes.find(j => j.name === it.jiraTypeName)?.subtask)

    const renderIssueTypeRow = (it: IssueTypeMappingDto, idx: number) => {
      const jiraMeta = wizardJiraIssueTypes.find(j => j.name === it.jiraTypeName)
      return (
        <tr key={idx}>
          <td>
            <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {jiraMeta?.iconUrl && (
                <img src={jiraMeta.iconUrl} alt="" width={16} height={16} style={{ flexShrink: 0 }} />
              )}
              <strong>{it.jiraTypeName}</strong>
            </span>
          </td>
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
              <span style={{ color: '#B3BAC5', fontSize: 13 }}>—</span>
            )}
          </td>
        </tr>
      )
    }

    return (
      <>
        <div className="wizard-info-block">
          Сопоставьте типы задач Jira с категориями OneLane:
          {' '}<strong>EPIC</strong> — фичи,
          {' '}<strong>STORY</strong> — рабочие задачи,
          {' '}<strong>SUBTASK</strong> — подзадачи с привязкой к роли,
          {' '}<strong>IGNORE</strong> — не отслеживать.
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th title="The issue type name as it appears in your Jira project">Jira Type</th>
                <th title="Whether this type is a Jira subtask (lives under a parent story) or a standard top-level issue">Subtask</th>
                <th title="EPIC = high-level feature, STORY = work item, SUBTASK = role-specific work under a story, IGNORE = don't track">Board Category</th>
                <th title="Which team role handles this subtask type (e.g. analyst, developer, tester)">Role</th>
              </tr>
            </thead>
            <tbody>
              {subtaskTypes.map(({ it, idx }) => renderIssueTypeRow(it, idx))}
              {subtaskTypes.length > 0 && standardTypes.length > 0 && (
                <tr><td colSpan={4} style={{ padding: 0, borderBottom: '2px solid #DFE1E6' }} /></tr>
              )}
              {standardTypes.map(({ it, idx }) => renderIssueTypeRow(it, idx))}
            </tbody>
          </table>
        </div>
      </>
    )
  }

  function renderWizardRoles() {
    return (
      <>
        <div className="wizard-info-block">
          Роли отражают типы работ в команде (анализ, разработка, тестирование).
          OneLane использует роли для расчёта прогресса и прогноза сроков.
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Display Name</th>
                <th>Color</th>
                <th>Sort Order</th>
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
                    <ColorPicker
                      value={role.color}
                      onChange={color => updateWizardRole(idx, 'color', color)}
                    />
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
                    <button className="btn-danger-text" onClick={() => deleteWizardRole(idx)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {wizardRoles.length === 0 && (
                <tr><td colSpan={5} style={{ textAlign: 'center', color: '#6B778C' }}>No roles configured</td></tr>
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
    const wizardFilteredStatuses = wizardStatuses.filter(s => s.issueCategory === wizardStatusFilter)

    // Group by sortOrder for pipeline
    const sortOrderGroups = new Map<number, StatusMappingDto[]>()
    for (const st of wizardFilteredStatuses) {
      const group = sortOrderGroups.get(st.sortOrder) || []
      group.push(st)
      sortOrderGroups.set(st.sortOrder, group)
    }
    const sortedOrders = Array.from(sortOrderGroups.keys()).sort((a, b) => a - b)

    return (
      <>
        <div className="wizard-info-block">
          Сопоставьте статусы Jira с категориями OneLane:
          <br /><br />
          <strong>NEW</strong> — задача не начата,
          <strong> REQUIREMENTS</strong> — сбор требований (эпики),
          <strong> PLANNED</strong> — запланировано (эпики),
          <strong> IN_PROGRESS</strong> — в работе,
          <strong> DONE</strong> — завершено.
          <br />
          <strong>Score Weight</strong> — вес для расчёта % завершения.
        </div>
        <div className="status-filter">
          {['PROJECT', 'EPIC', 'STORY', 'SUBTASK'].map(f => (
            <button
              key={f}
              className={`status-filter-btn ${wizardStatusFilter === f ? 'active' : ''}`}
              onClick={() => setWizardStatusFilter(f)}
            >
              {f} ({wizardStatuses.filter(s => s.issueCategory === f).length})
            </button>
          ))}
        </div>

        {wizardFilteredStatuses.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#6B778C', padding: '48px 0' }}>
            No statuses for this filter
          </div>
        ) : (
          <div className="pipeline-container">
            {sortedOrders.map((order, colIdx) => {
              const group = sortOrderGroups.get(order)!
              return (
                <div key={order} className="pipeline-column-wrapper">
                  <div className="pipeline-column">
                    <div className="pipeline-column-header">
                      <button
                        className="pipeline-move-btn"
                        disabled={colIdx === 0}
                        onClick={() => group.forEach(st => moveStatus(wizardStatuses, setWizardStatuses, wizardStatusFilter, st.jiraStatusName, -1))}
                        title="Move left"
                      >&larr;</button>
                      <span>{colIdx + 1}</span>
                      <button
                        className="pipeline-move-btn"
                        disabled={colIdx === sortedOrders.length - 1}
                        onClick={() => group.forEach(st => moveStatus(wizardStatuses, setWizardStatuses, wizardStatusFilter, st.jiraStatusName, 1))}
                        title="Move right"
                      >&rarr;</button>
                    </div>
                    {group.map(st => {
                      const realIdx = wizardStatuses.indexOf(st)
                      const bgColor = st.color || STATUS_CATEGORY_DEFAULT_COLORS[st.statusCategory] || '#DFE1E6'
                      return (
                        <div
                          key={realIdx}
                          className="pipeline-card"
                          style={{ backgroundColor: bgColor }}
                        >
                          <div className="pipeline-card-name">{st.jiraStatusName}</div>
                          <div className="pipeline-card-fields">
                            <label className="pipeline-field">
                              <span className="pipeline-field-label">Category</span>
                              <select
                                className="pipeline-select"
                                value={st.statusCategory}
                                onChange={e => updateWizardStatus(realIdx, 'statusCategory', e.target.value)}
                              >
                                {STATUS_CATEGORIES.filter(c => wizardStatusFilter === 'EPIC' || wizardStatusFilter === 'PROJECT' || (c !== 'REQUIREMENTS' && c !== 'PLANNED')).map(c => <option key={c} value={c}>{c}</option>)}
                              </select>
                            </label>
                            <label className="pipeline-field">
                              <span className="pipeline-field-label">Weight</span>
                              <input
                                type="number"
                                className="pipeline-input"
                                value={st.scoreWeight}
                                onChange={e => updateWizardStatus(realIdx, 'scoreWeight', parseInt(e.target.value) || 0)}
                                min={-100}
                                max={100}
                              />
                            </label>
                            <label className="pipeline-field">
                              <span className="pipeline-field-label">Color</span>
                              <StatusColorPicker
                                value={bgColor}
                                onChange={color => updateWizardStatus(realIdx, 'color', color)}
                              />
                            </label>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                  {colIdx < sortedOrders.length - 1 && (
                    <div className="pipeline-arrow" />
                  )}
                </div>
              )
            })}
          </div>
        )}
      </>
    )
  }

  function renderWizardLinkTypes() {
    return (
      <>
        <div className="wizard-info-block">
          Типы связей определяют зависимости между задачами:
          {' '}<strong>BLOCKS</strong> — блокирующие зависимости (отображаются на доске),
          {' '}<strong>RELATED</strong> — информационные связи,
          {' '}<strong>IGNORE</strong> — не отслеживать.
        </div>
        <div className="workflow-table-wrapper">
          <table className="workflow-table">
            <thead>
              <tr>
                <th title="The link type name as it appears in your Jira project">Jira Link Type</th>
                <th>Inward</th>
                <th>Outward</th>
                <th title="BLOCKS = dependency, RELATED = informational, IGNORE = skip">Link Category</th>
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
        <div className="wizard-info-block">
          Проверьте конфигурацию перед сохранением. OneLane будет использовать
          эти маппинги для доски, метрик и прогнозов.
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
                    <ColorPicker
                      value={role.color}
                      onChange={color => updateRole(idx, 'color', color)}
                    />
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
                    <button className="btn-danger-text" onClick={() => deleteRole(idx)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {roles.length === 0 && (
                <tr><td colSpan={5} style={{ textAlign: 'center', color: '#6B778C' }}>No roles configured</td></tr>
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

        {issueTypes.some(t => t.boardCategory === 'PROJECT') && (
          <div style={{ marginTop: 24, padding: '16px 20px', background: '#F4F5F7', borderRadius: 8 }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 14, color: '#172B4D' }}>Project → Epic Link Mode</h3>
            <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                <span>Link Type:</span>
                <select
                  className="workflow-select"
                  value={epicLinkType}
                  onChange={e => setEpicLinkType(e.target.value)}
                  style={{ width: 140 }}
                >
                  <option value="parent">Parent (default)</option>
                  <option value="issuelink">Issue Link</option>
                </select>
              </label>
              {epicLinkType === 'issuelink' && (
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                  <span>Link Name:</span>
                  <input
                    className="workflow-input"
                    value={epicLinkName}
                    onChange={e => setEpicLinkName(e.target.value)}
                    placeholder="e.g. is child of"
                    style={{ width: 200 }}
                  />
                </label>
              )}
              <button
                className="btn btn-secondary"
                style={{ fontSize: 13, padding: '4px 12px' }}
                onClick={async () => {
                  try {
                    setSaving(true)
                    await axios.put('/api/admin/workflow-config', { epicLinkType, epicLinkName: epicLinkName || null })
                    refreshWorkflowContext()
                    showSaveSuccess('Epic link config saved')
                  } catch { setError('Failed to save epic link config') }
                  finally { setSaving(false) }
                }}
                disabled={saving}
              >
                Save Link Config
              </button>
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: '#6B778C' }}>
              {epicLinkType === 'parent'
                ? 'Epics are linked to Projects via Jira parent field (Project is the parent of Epic).'
                : 'Epics are linked to Projects via Jira issue links. Specify the link name used in your Jira project.'}
            </div>
          </div>
        )}
      </>
    )
  }

  function renderStatusesTab() {
    // Group by sortOrder for pipeline columns
    const sortOrderGroups = new Map<number, StatusMappingDto[]>()
    for (const st of filteredStatuses) {
      const group = sortOrderGroups.get(st.sortOrder) || []
      group.push(st)
      sortOrderGroups.set(st.sortOrder, group)
    }
    const sortedOrders = Array.from(sortOrderGroups.keys()).sort((a, b) => a - b)

    const getIssueCount = (statusName: string, category: string) => {
      const found = issueCounts.find(c => c.jiraStatusName === statusName && c.issueCategory === category)
      return found?.count ?? 0
    }

    return (
      <>
        <div className="status-filter">
          {['PROJECT', 'EPIC', 'STORY', 'SUBTASK'].map(f => (
            <button
              key={f}
              className={`status-filter-btn ${statusFilter === f ? 'active' : ''}`}
              onClick={() => setStatusFilter(f)}
            >
              {f} ({statuses.filter(s => s.issueCategory === f).length})
            </button>
          ))}
        </div>

        {filteredStatuses.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#6B778C', padding: '48px 0' }}>
            No statuses for {statusFilter}
          </div>
        ) : (
          <div className="pipeline-container">
            {sortedOrders.map((order, colIdx) => {
              const group = sortOrderGroups.get(order)!
              return (
                <div key={order} className="pipeline-column-wrapper">
                  <div className="pipeline-column">
                    <div className="pipeline-column-header">
                      <button
                        className="pipeline-move-btn"
                        disabled={colIdx === 0}
                        onClick={() => group.forEach(st => moveStatus(statuses, setStatuses, statusFilter, st.jiraStatusName, -1))}
                        title="Move left"
                      >&larr;</button>
                      <span>{colIdx + 1}</span>
                      <button
                        className="pipeline-move-btn"
                        disabled={colIdx === sortedOrders.length - 1}
                        onClick={() => group.forEach(st => moveStatus(statuses, setStatuses, statusFilter, st.jiraStatusName, 1))}
                        title="Move right"
                      >&rarr;</button>
                    </div>
                    {group.map(st => {
                      const realIdx = statuses.indexOf(st)
                      const count = getIssueCount(st.jiraStatusName, st.issueCategory)
                      const bgColor = st.color || STATUS_CATEGORY_DEFAULT_COLORS[st.statusCategory] || '#DFE1E6'
                      return (
                        <div
                          key={st.id ?? `new-${realIdx}`}
                          className="pipeline-card"
                          style={{ backgroundColor: bgColor }}
                        >
                          <div className="pipeline-card-name">{st.jiraStatusName || '(unnamed)'}</div>
                          <div className="pipeline-card-fields">
                            <label className="pipeline-field">
                              <span className="pipeline-field-label">Category</span>
                              <select
                                className="pipeline-select"
                                value={st.statusCategory}
                                onChange={e => updateStatus(realIdx, 'statusCategory', e.target.value)}
                              >
                                {STATUS_CATEGORIES.filter(c => statusFilter === 'EPIC' || statusFilter === 'PROJECT' || (c !== 'REQUIREMENTS' && c !== 'PLANNED')).map(c => <option key={c} value={c}>{c}</option>)}
                              </select>
                            </label>
                            <label className="pipeline-field">
                              <span className="pipeline-field-label">Weight</span>
                              <input
                                type="number"
                                className="pipeline-input"
                                value={st.scoreWeight}
                                onChange={e => updateStatus(realIdx, 'scoreWeight', parseInt(e.target.value) || 0)}
                                min={-100}
                                max={100}
                              />
                            </label>
                            <label className="pipeline-field">
                              <span className="pipeline-field-label">Color</span>
                              <StatusColorPicker
                                value={bgColor}
                                onChange={color => updateStatus(realIdx, 'color', color)}
                              />
                            </label>
                          </div>
                          {count > 0 && (
                            <div className="pipeline-card-count">
                              {count} issue{count !== 1 ? 's' : ''}
                            </div>
                          )}
                          <button
                            className="pipeline-card-delete"
                            onClick={() => deleteStatus(realIdx)}
                            title="Delete"
                          >
                            &times;
                          </button>
                        </div>
                      )
                    })}
                  </div>
                  {colIdx < sortedOrders.length - 1 && (
                    <div className="pipeline-arrow" />
                  )}
                </div>
              )
            })}
          </div>
        )}

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
