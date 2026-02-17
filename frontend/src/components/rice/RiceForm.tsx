import { useEffect, useState } from 'react'
import { riceApi, RiceTemplate, RiceTemplateListItem, RiceAssessment, RiceAssessmentRequest, AssessmentAnswerEntry, RiceCriteria } from '../../api/rice'

interface RiceFormProps {
  issueKey: string
  onSaved?: () => void
}

type Selections = Record<number, number[]> // criteriaId -> optionIds

const PARAM_ORDER = ['REACH', 'IMPACT', 'CONFIDENCE', 'EFFORT']
const PARAM_LABELS: Record<string, string> = {
  REACH: 'Reach (охват)',
  IMPACT: 'Impact (влияние)',
  CONFIDENCE: 'Confidence (уверенность)',
  EFFORT: 'Effort (затраты)',
}

function groupByParameter(criteria: RiceCriteria[]): Record<string, RiceCriteria[]> {
  const groups: Record<string, RiceCriteria[]> = {}
  for (const c of criteria) {
    if (!groups[c.parameter]) groups[c.parameter] = []
    groups[c.parameter].push(c)
  }
  return groups
}

function computePreview(template: RiceTemplate, selections: Selections): { reach: number; impact: number; confidence: number | null; effort: number | null; score: number | null } {
  let reach = 0, impact = 0
  let confidence: number | null = null
  let effort: number | null = null

  for (const criteria of template.criteria) {
    const selected = selections[criteria.id] || []
    const selectedScores = criteria.options
      .filter(o => selected.includes(o.id))
      .map(o => o.score)

    const sum = selectedScores.reduce((a, b) => a + b, 0)

    switch (criteria.parameter) {
      case 'REACH': reach += sum; break
      case 'IMPACT': impact += sum; break
      case 'CONFIDENCE': if (selectedScores.length > 0) confidence = selectedScores[0]; break
      case 'EFFORT': if (selectedScores.length > 0) effort = selectedScores[0]; break
    }
  }

  let score: number | null = null
  if (confidence != null && effort != null && effort > 0) {
    score = (reach * impact * confidence) / effort
  }

  return { reach, impact, confidence, effort, score }
}

export function RiceForm({ issueKey, onSaved }: RiceFormProps) {
  const [templates, setTemplates] = useState<RiceTemplateListItem[]>([])
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null)
  const [template, setTemplate] = useState<RiceTemplate | null>(null)
  const [assessment, setAssessment] = useState<RiceAssessment | null>(null)
  const [selections, setSelections] = useState<Selections>({})
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(true)

  // Load templates and existing assessment
  useEffect(() => {
    setLoading(true)
    Promise.all([
      riceApi.listTemplates(),
      riceApi.getAssessment(issueKey),
    ]).then(([tmplList, existing]) => {
      setTemplates(tmplList)
      setAssessment(existing)

      // Pick template: existing assessment's template or first available
      const tmplId = existing?.templateId ?? (tmplList.length > 0 ? tmplList[0].id : null)
      setSelectedTemplateId(tmplId)
    }).finally(() => setLoading(false))
  }, [issueKey])

  // Load full template when selection changes
  useEffect(() => {
    if (selectedTemplateId == null) return
    riceApi.getTemplate(selectedTemplateId).then(t => {
      setTemplate(t)
      // Initialize selections from existing assessment or empty
      if (assessment && assessment.templateId === selectedTemplateId) {
        const sel: Selections = {}
        for (const a of assessment.answers) {
          sel[a.criteriaId] = a.selectedOptionIds
        }
        setSelections(sel)
      } else {
        setSelections({})
      }
    })
  }, [selectedTemplateId, assessment])

  const handleSelect = (criteriaId: number, optionId: number, selectionType: string) => {
    setSelections(prev => {
      const current = prev[criteriaId] || []
      if (selectionType === 'SINGLE') {
        return { ...prev, [criteriaId]: [optionId] }
      } else {
        // MULTI: toggle
        const next = current.includes(optionId)
          ? current.filter(id => id !== optionId)
          : [...current, optionId]
        return { ...prev, [criteriaId]: next }
      }
    })
  }

  const handleSave = async () => {
    if (!template) return
    setSaving(true)
    try {
      const answers: AssessmentAnswerEntry[] = Object.entries(selections)
        .filter(([, ids]) => ids.length > 0)
        .map(([criteriaId, optionIds]) => ({
          criteriaId: Number(criteriaId),
          optionIds,
        }))

      const request: RiceAssessmentRequest = {
        issueKey,
        templateId: template.id,
        effortManual: null,
        answers,
      }

      const result = await riceApi.saveAssessment(request)
      setAssessment(result)
      onSaved?.()
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <div style={{ padding: 12, color: '#6B778C', fontSize: 13 }}>Loading RICE...</div>
  }

  if (!template) {
    return <div style={{ padding: 12, color: '#97A0AF', fontSize: 13 }}>No RICE templates available</div>
  }

  const grouped = groupByParameter(template.criteria)
  const preview = computePreview(template, selections)

  return (
    <div style={{
      borderTop: '1px solid #EBECF0',
      marginTop: 8,
      paddingTop: 12,
    }}>
      {/* Header with template selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: '#172B4D' }}>RICE Scoring</span>
        {templates.length > 1 && (
          <select
            value={selectedTemplateId ?? ''}
            onChange={e => setSelectedTemplateId(Number(e.target.value))}
            style={{ fontSize: 12, padding: '3px 6px', border: '1px solid #DFE1E6', borderRadius: 3 }}
          >
            {templates.map(t => (
              <option key={t.id} value={t.id}>{t.name}</option>
            ))}
          </select>
        )}
        {!templates.length || templates.length === 1 ? (
          <span style={{ fontSize: 12, color: '#6B778C' }}>
            {template.name}
          </span>
        ) : null}
      </div>

      {/* Parameters */}
      {PARAM_ORDER.map(param => {
        const criteria = grouped[param]
        if (!criteria || criteria.length === 0) return null

        // Sum for this parameter
        let paramSum = 0
        for (const c of criteria) {
          const sel = selections[c.id] || []
          paramSum += c.options
            .filter(o => sel.includes(o.id))
            .reduce((a, b) => a + b.score, 0)
        }

        return (
          <div key={param} style={{ marginBottom: 12 }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 6,
            }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: '#42526E', textTransform: 'uppercase' }}>
                {PARAM_LABELS[param] || param}
              </span>
              <span style={{ fontSize: 12, color: '#0052CC', fontWeight: 600 }}>
                {param === 'CONFIDENCE' || param === 'EFFORT'
                  ? (paramSum > 0 ? paramSum : '—')
                  : `\u03A3 = ${paramSum}`
                }
              </span>
            </div>

            <div style={{
              background: '#fff',
              border: '1px solid #EBECF0',
              borderRadius: 4,
              padding: '8px 12px',
            }}>
              {criteria.map(c => (
                <div key={c.id} style={{ marginBottom: criteria.indexOf(c) < criteria.length - 1 ? 8 : 0 }}>
                  <div style={{ fontSize: 12, color: '#172B4D', fontWeight: 500, marginBottom: 4 }}>
                    {c.name}
                    {c.description && (
                      <span style={{ color: '#97A0AF', fontWeight: 400 }}> — {c.description}</span>
                    )}
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {c.options.map(opt => {
                      const isSelected = (selections[c.id] || []).includes(opt.id)
                      return (
                        <label
                          key={opt.id}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 12,
                            color: isSelected ? '#0052CC' : '#42526E',
                            cursor: 'pointer',
                            padding: '3px 8px',
                            borderRadius: 3,
                            background: isSelected ? '#DEEBFF' : '#F4F5F7',
                            border: isSelected ? '1px solid #B3D4FF' : '1px solid transparent',
                            transition: 'all 0.1s',
                          }}
                        >
                          <input
                            type={c.selectionType === 'MULTI' ? 'checkbox' : 'radio'}
                            name={`criteria-${c.id}`}
                            checked={isSelected}
                            onChange={() => handleSelect(c.id, opt.id, c.selectionType)}
                            style={{ display: 'none' }}
                          />
                          <span>{opt.label}</span>
                          <span style={{ color: isSelected ? '#0052CC' : '#97A0AF', fontWeight: 600 }}>
                            +{opt.score}
                          </span>
                        </label>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )
      })}

      {/* Score preview + Save */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '10px 0',
        borderTop: '2px solid #DFE1E6',
        marginTop: 4,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span style={{ fontSize: 13, color: '#172B4D' }}>
            RICE Score = ({preview.reach} × {preview.impact} × {preview.confidence ?? '?'}) / {preview.effort ?? '?'}
            = <strong style={{ fontSize: 15, color: preview.score != null ? '#0052CC' : '#97A0AF' }}>
              {preview.score != null ? preview.score.toFixed(1) : '—'}
            </strong>
          </span>
          {assessment?.normalizedScore != null && (
            <span style={{ fontSize: 12, color: '#6B778C' }}>
              Normalized: <strong>{assessment.normalizedScore.toFixed(0)}/100</strong>
            </span>
          )}
        </div>
        <button
          onClick={handleSave}
          disabled={saving || preview.score == null}
          style={{
            padding: '6px 16px',
            fontSize: 13,
            fontWeight: 600,
            background: saving || preview.score == null ? '#DFE1E6' : '#0052CC',
            color: saving || preview.score == null ? '#6B778C' : '#fff',
            border: 'none',
            borderRadius: 3,
            cursor: saving || preview.score == null ? 'not-allowed' : 'pointer',
          }}
        >
          {saving ? 'Saving...' : 'Save'}
        </button>
      </div>
    </div>
  )
}
