import { useGuideLang } from '../../components/guide/GuideLanguageContext'
import { GuideSectionData } from '../types'

function RulesContent() {
  const { t } = useGuideLang()
  return (
    <>
      {/* Task Hierarchy */}
      <h3>{t('Иерархия задач', 'Task Hierarchy')}</h3>
      <pre>{`Project
├── Epic (${t('фича, команда A', 'feature, team A')})
│   └── Story / Bug
│       └── Subtask [SA]
│       └── Subtask [DEV]
│       └── Subtask [QA]
└── Epic (${t('фича, команда B', 'feature, team B')})
    └── Story / Bug
        └── Subtask [DEV]
        └── Subtask [QA]`}</pre>
      <p>
        {t(
          'Project - верхний уровень, объединяет эпики. Epic - фича для одной команды. Один Epic не может делаться несколькими командами одновременно. Story/Bug - единица работы внутри эпика. Subtask - конкретное действие по роли конвейера.',
          'Project is the top level, grouping epics. Epic is a feature for one team. One Epic cannot be worked on by multiple teams at the same time. Story/Bug is a work unit inside an epic. Subtask is a specific action per pipeline role.'
        )}
      </p>

      {/* Estimation Rules */}
      <h3>{t('Оценки и логирование', 'Estimates and time logging')}</h3>
      <p>
        {t(
          'Lead Board использует оценки в человеко-днях (1 ч/д = 8 часов) для расчёта прогресса и прогнозирования сроков. На этапе декомпозиции команда оценивает каждый Subtask по своей роли. В конце рабочего дня исполнители логируют потраченное время, и на основании этого строится прогресс. Оценки могут меняться по ходу работы, это нормально.',
          'Lead Board uses estimates in person-days (1 pd = 8 hours) to calculate progress and forecast deadlines. During decomposition the team estimates each Subtask per role. At the end of the workday, team members log time spent, and progress is built from that. Estimates can change during the work, that is normal.'
        )}
      </p>
      <table className="guide-table">
        <thead>
          <tr>
            <th>{t('Правило', 'Rule')}</th>
            <th>{t('Описание', 'Description')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{t('Оценки только в Subtask', 'Estimates only in Subtask')}</td>
            <td>{t('Story и Epic агрегируют оценки из своих Subtask', 'Story and Epic aggregate estimates from their Subtasks')}</td>
          </tr>
          <tr>
            <td>{t('Subtask = роль', 'Subtask = role')}</td>
            <td>{t('Каждый Subtask привязан к роли конвейера (SA, DEV, QA)', 'Each Subtask is tied to a pipeline role (SA, DEV, QA)')}</td>
          </tr>
          <tr>
            <td>{t('Исполнитель в Subtask', 'Assignee in Subtask')}</td>
            <td>{t('Исполнитель назначается на Subtask, не на Story или Epic', 'Assignee is set on Subtask, not on Story or Epic')}</td>
          </tr>
          <tr>
            <td>{t('Логирование времени', 'Time logging')}</td>
            <td>{t('Worklog пишется в Subtask. Прогресс = залогировано / оценка', 'Worklog goes into Subtask. Progress = logged / estimate')}</td>
          </tr>
        </tbody>
      </table>

    </>
  )
}

const rulesContent = <RulesContent />

export const rulesSection: GuideSectionData = {
  id: 'rules',
  titleRu: 'Правила процесса',
  titleEn: 'Process Rules',
  contentRu: rulesContent,
  contentEn: rulesContent,
}
