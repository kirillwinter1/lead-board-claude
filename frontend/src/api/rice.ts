import axios from 'axios'

export interface RiceCriteriaOption {
  id: number
  label: string
  description: string | null
  score: number
  sortOrder: number
}

export interface RiceCriteria {
  id: number
  parameter: string // REACH, IMPACT, CONFIDENCE, EFFORT
  name: string
  description: string | null
  selectionType: string // SINGLE, MULTI
  sortOrder: number
  options: RiceCriteriaOption[]
}

export interface RiceTemplate {
  id: number
  name: string
  code: string
  strategicWeight: number
  active: boolean
  criteria: RiceCriteria[]
}

export interface RiceTemplateListItem {
  id: number
  name: string
  code: string
  strategicWeight: number
  active: boolean
  criteriaCount: number
}

export interface RiceAssessmentAnswer {
  criteriaId: number
  criteriaName: string
  parameter: string
  selectedOptionIds: number[]
}

export interface RiceAssessment {
  id: number
  issueKey: string
  templateId: number
  templateName: string
  assessedByName: string | null
  totalReach: number | null
  totalImpact: number | null
  confidence: number | null
  effortManual: string | null
  effortAuto: number | null
  effectiveEffort: number | null
  riceScore: number | null
  normalizedScore: number | null
  answers: RiceAssessmentAnswer[]
}

export interface AssessmentAnswerEntry {
  criteriaId: number
  optionIds: number[]
}

export interface RiceAssessmentRequest {
  issueKey: string
  templateId: number
  effortManual: string | null
  answers: AssessmentAnswerEntry[]
}

export const riceApi = {
  listTemplates: () =>
    axios.get<RiceTemplateListItem[]>('/api/rice/templates').then(r => r.data),

  getTemplate: (id: number) =>
    axios.get<RiceTemplate>(`/api/rice/templates/${id}`).then(r => r.data),

  getAssessment: (issueKey: string) =>
    axios.get<RiceAssessment>(`/api/rice/assessments/${issueKey}`).then(r => r.data).catch(() => null),

  saveAssessment: (data: RiceAssessmentRequest) =>
    axios.post<RiceAssessment>('/api/rice/assessments', data).then(r => r.data),
}
