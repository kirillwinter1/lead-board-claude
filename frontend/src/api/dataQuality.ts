import axios from 'axios'

// ---- Fix preview / apply contract (F84) ----

/** A single field change that a fix will produce. */
export interface FixChange {
  issueKey: string
  summary: string
  /** Jira issue type name (e.g. "Story", "Epic") — drives the issue-type icon. */
  issueType: string
  field: string
  from: string
  to: string
  /** True when the change is applied only to the local DB (no Jira write). */
  local: boolean
}

export interface FixInputOption {
  value: string
  label: string
  /** Accent color (e.g. team color) — rendered as a dot in the select dropdown. */
  color?: string | null
}

/** A user-provided input required to compute/apply the fix. */
export interface FixInput {
  name: string
  type: 'select' | 'date' | 'number'
  label: string
  required: boolean
  defaultValue?: string | number
  min?: number
  step?: number
  options?: FixInputOption[]
}

/** An alternative way to resolve the violation (radio-selectable). */
export interface FixChoice {
  id: string
  label: string
  changes: FixChange[]
  inputs: FixInput[]
}

/** Preview of what applying a fix will do. */
export interface FixPreview {
  issueKey: string
  rule: string
  fixType: string
  title: string
  /** When false, the violation can no longer be fixed automatically. */
  applicable: boolean
  notApplicableReason: string | null
  /** Risky fixes (bulk / destructive) show a red warning + affected issues. */
  risky: boolean
  warning: string | null
  /**
   * OAUTH = changes made as the current user; BASIC = service account;
   * LOCAL = Lead Board-only fix (no Jira write, e.g. EPIC_NO_TEAM / TEAM_FIELD_UNMAPPED / RICE).
   */
  authMode: 'OAUTH' | 'BASIC' | 'LOCAL'
  changes: FixChange[]
  /** Display lines describing every issue that will be touched. */
  affectedIssues: string[]
  inputs: FixInput[]
  choices: FixChoice[]
}

export interface ApplyFixRequest {
  issueKey: string
  rule: string
  choiceId?: string
  params: Record<string, unknown>
}

export interface ApplyFixResult {
  success: boolean
  message: string
  updatedIssues: string[]
}

/** Fetch a preview of the fix for a given issue + rule. */
export async function getFixPreview(issueKey: string, rule: string): Promise<FixPreview> {
  const res = await axios.get<FixPreview>('/api/data-quality/fix-preview', {
    params: { issueKey, rule },
  })
  return res.data
}

/** Apply a fix. Non-2xx responses ({success:false, message}) surface via the thrown axios error. */
export async function applyFix(request: ApplyFixRequest): Promise<ApplyFixResult> {
  const res = await axios.post<ApplyFixResult>('/api/data-quality/fix', request)
  return res.data
}
