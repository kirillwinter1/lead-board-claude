import { Link, useParams } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { competencyApi, TeamCompetencyMatrix, BusFactorAlert, CompetencyLevel } from '../api/competency'
import { CompetencyRating, LEVEL_COLORS } from '../components/competency/CompetencyRating'
import './TeamsPage.css'

export function TeamCompetencyPage() {
  const { teamId } = useParams<{ teamId: string }>()
  const [matrix, setMatrix] = useState<TeamCompetencyMatrix | null>(null)
  const [busFactor, setBusFactor] = useState<BusFactorAlert[]>([])
  const [loading, setLoading] = useState(true)

  const loadData = async () => {
    if (!teamId) return
    setLoading(true)
    try {
      const [m, bf] = await Promise.all([
        competencyApi.getTeamMatrix(Number(teamId)),
        competencyApi.getBusFactor(Number(teamId)),
      ])
      setMatrix(m)
      setBusFactor(bf)
    } catch {
      /* ignore */
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [teamId])

  const handleLevelChange = async (memberId: number, componentName: string, level: number) => {
    try {
      await competencyApi.updateMember(memberId, [{ componentName, level }])
      loadData()
    } catch {
      /* ignore */
    }
  }

  const getLevel = (competencies: CompetencyLevel[], componentName: string): number => {
    return competencies.find(c => c.componentName === componentName)?.level ?? 0
  }

  const severityStyle = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return { background: '#ffebe6', color: '#bf2600', border: '1px solid #ff8f73' }
      case 'WARNING': return { background: '#fffae6', color: '#ff8b00', border: '1px solid #ffe380' }
      default: return { background: '#e3fcef', color: '#006644', border: '1px solid #79f2c0' }
    }
  }

  const severityIcon = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return '\u26A0'
      case 'WARNING': return '\u26A0'
      default: return '\u2713'
    }
  }

  if (loading) {
    return (
      <main className="main-content">
        <div className="page-header">
          <div className="page-header-left">
            <Link to={`/board/teams/${teamId}`} className="back-link">&larr; Назад к команде</Link>
            <h1>Матрица компетенций</h1>
          </div>
        </div>
        <div style={{ padding: 40, textAlign: 'center', color: '#6b778c' }}>Загрузка...</div>
      </main>
    )
  }

  if (!matrix) {
    return (
      <main className="main-content">
        <div className="page-header">
          <div className="page-header-left">
            <Link to={`/board/teams/${teamId}`} className="back-link">&larr; Назад к команде</Link>
            <h1>Матрица компетенций</h1>
          </div>
        </div>
        <div style={{ padding: 40, textAlign: 'center', color: '#de350b' }}>Ошибка загрузки</div>
      </main>
    )
  }

  const criticalAlerts = busFactor.filter(b => b.severity !== 'OK')

  return (
    <main className="main-content">
      <div className="page-header">
        <div className="page-header-left">
          <Link to={`/board/teams/${teamId}`} className="back-link">&larr; Назад к команде</Link>
          <h1>Матрица компетенций</h1>
        </div>
      </div>

      {/* Bus Factor Alerts */}
      {criticalAlerts.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
          {criticalAlerts.map(alert => (
            <div
              key={alert.componentName}
              style={{
                ...severityStyle(alert.severity),
                padding: '6px 12px',
                borderRadius: 6,
                fontSize: 13,
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <span>{severityIcon(alert.severity)}</span>
              <span>{alert.componentName}</span>
              <span style={{ opacity: 0.7 }}>
                {alert.severity === 'CRITICAL' ? 'нет экспертов' : `1 эксперт: ${alert.experts[0]}`}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Matrix Table */}
      {matrix.components.length === 0 ? (
        <div style={{
          background: 'white', border: '1px solid #dfe1e6', borderRadius: 8,
          padding: 40, textAlign: 'center', color: '#6b778c',
        }}>
          Нет компонентов. Синхронизируйте проект с Jira, чтобы компоненты появились.
        </div>
      ) : (
        <div style={{ background: 'white', border: '1px solid #dfe1e6', borderRadius: 8, overflow: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: matrix.components.length * 100 + 200 }}>
            <thead>
              <tr>
                <th style={{
                  padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 600,
                  color: '#6b778c', textTransform: 'uppercase', letterSpacing: 0.5,
                  background: '#fafbfc', borderBottom: '1px solid #ebecf0',
                  position: 'sticky', left: 0, zIndex: 2,
                }}>
                  Участник
                </th>
                {matrix.components.map(comp => (
                  <th key={comp} style={{
                    padding: '10px 12px', textAlign: 'center', fontSize: 11, fontWeight: 600,
                    color: '#6b778c', background: '#fafbfc', borderBottom: '1px solid #ebecf0',
                    whiteSpace: 'nowrap',
                  }}>
                    {comp}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {matrix.members.map(member => (
                <tr key={member.memberId} style={{ borderBottom: '1px solid #f4f5f7' }}>
                  <td style={{
                    padding: '10px 16px', fontWeight: 500, fontSize: 13, color: '#172b4d',
                    position: 'sticky', left: 0, background: 'white', zIndex: 1,
                    whiteSpace: 'nowrap',
                  }}>
                    <Link to={`/board/teams/${teamId}/member/${member.memberId}`}
                      style={{ color: '#0052cc', textDecoration: 'none' }}>
                      {member.displayName}
                    </Link>
                  </td>
                  {matrix.components.map(comp => {
                    const level = getLevel(member.competencies, comp)
                    return (
                      <td key={comp} style={{ padding: '8px 12px', textAlign: 'center' }}>
                        <CompetencyRating
                          level={level}
                          onChange={l => handleLevelChange(member.memberId, comp, l)}
                        />
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Legend */}
      <div style={{
        display: 'flex', gap: 16, marginTop: 12, padding: '8px 0',
        fontSize: 12, color: '#6b778c',
      }}>
        {[1, 2, 3, 4, 5].map(level => (
          <div key={level} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{
              width: 10, height: 10, borderRadius: '50%',
              background: LEVEL_COLORS[level], display: 'inline-block',
            }} />
            {level} — {level === 1 ? 'No exp' : level === 2 ? 'Beginner' : level === 3 ? 'Competent' : level === 4 ? 'Proficient' : 'Expert'}
          </div>
        ))}
      </div>
    </main>
  )
}
