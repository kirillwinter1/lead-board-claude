import { Link } from 'react-router-dom'
import { useGuideLang } from '../../components/guide/GuideLanguageContext'
import { RoleCardFull } from '../../components/guide/RoleCard'
import { GuideSectionData } from '../types'

function RolesContent() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        {t(
          'Справочник по всем ролям процесса delivery. Роли конвейера (SA, DEV, QA) — примеры по умолчанию. Их можно настроить в ',
          'Reference for all delivery process roles. Pipeline roles (SA, DEV, QA) are default examples. They are configurable in '
        )}
        <Link to="/workflow">{t('Конфигурации Workflow', 'Workflow Config')}</Link>
        {t(
          '. Например: UX → SA → DEV → DEVOPS → QA.',
          '. For example: UX → SA → DEV → DEVOPS → QA.'
        )}
      </p>

      {/* Product Owner */}
      <RoleCardFull
        id="roles-po"
        nameRu="Product Owner (PO)"
        nameEn="Product Owner (PO)"
        responsibilityRu="Бизнес-ценность, приоритеты, scope, приёмка результата"
        responsibilityEn="Business value, priorities, scope, result acceptance"
        stages={[
          {
            stageRu: 'Идея',
            stageEn: 'Idea',
            actionsRu: 'Создаёт Epic, заполняет RICE, описывает бизнес-ценность',
            actionsEn: 'Creates Epic, fills RICE, describes business value',
          },
          {
            stageRu: 'БТ (BRD)',
            stageEn: 'BRD',
            actionsRu: 'Пишет бизнес-требования, определяет критерии приёмки',
            actionsEn: 'Writes business requirements, defines acceptance criteria',
          },
          {
            stageRu: 'Грязные оценки',
            stageEn: 'Rough Estimates',
            actionsRu: 'Проверяет прогноз сроков, принимает решение о приоритете',
            actionsEn: 'Reviews deadline forecast, makes priority decision',
          },
          {
            stageRu: 'Планирование',
            stageEn: 'Planning',
            actionsRu: 'Приоритизирует Stories по бизнес-ценности',
            actionsEn: 'Prioritizes Stories by business value',
          },
          {
            stageRu: 'Приёмка',
            stageEn: 'Acceptance',
            actionsRu: 'Проверяет результат по критериям приёмки из BRD',
            actionsEn: 'Verifies result against BRD acceptance criteria',
          },
        ]}
        keyScreens={[
          { label: 'Board', path: '/', descriptionRu: 'Доска', descriptionEn: 'Board' },
          { label: 'Projects', path: '/projects', descriptionRu: 'Проекты', descriptionEn: 'Projects' },
          { label: 'Quarterly Planning', path: '/quarterly-planning', descriptionRu: 'Квартальное планирование', descriptionEn: 'Quarterly planning' },
        ]}
      />

      {/* Delivery Manager */}
      <RoleCardFull
        id="roles-dm"
        nameRu="Delivery Manager (DM)"
        nameEn="Delivery Manager (DM)"
        responsibilityRu="Здоровье процесса, кросс-командная координация, метрики, ретроспективы"
        responsibilityEn="Process health, cross-team coordination, metrics, retrospectives"
        stages={[
          {
            stageRu: 'Идея',
            stageEn: 'Idea',
            actionsRu: 'Проверяет соответствие roadmap',
            actionsEn: 'Checks roadmap fit',
          },
          {
            stageRu: 'Планирование',
            stageEn: 'Planning',
            actionsRu: 'Контролирует WIP-лимиты, баланс нагрузки между командами',
            actionsEn: 'Controls WIP limits, cross-team workload balance',
          },
          {
            stageRu: 'Разработка',
            stageEn: 'Development',
            actionsRu: 'Мониторит throughput, точность прогноза',
            actionsEn: 'Monitors throughput, forecast accuracy',
          },
          {
            stageRu: 'Готово',
            stageEn: 'Done',
            actionsRu: 'Проводит ретроспективу, фиксирует факт vs прогноз',
            actionsEn: 'Facilitates retrospective, records actual vs forecast',
          },
        ]}
        keyScreens={[
          { label: 'Metrics', path: '/metrics', descriptionRu: 'Метрики', descriptionEn: 'Metrics' },
          { label: 'Board', path: '/', descriptionRu: 'Доска', descriptionEn: 'Board' },
          { label: 'Timeline', path: '/?view=timeline', descriptionRu: 'Таймлайн', descriptionEn: 'Timeline' },
          { label: 'Bug Metrics', path: '/bug-metrics', descriptionRu: 'Метрики багов', descriptionEn: 'Bug metrics' },
        ]}
      />

      {/* Team Lead */}
      <RoleCardFull
        id="roles-tl"
        nameRu="Team Lead (TL)"
        nameEn="Team Lead (TL)"
        responsibilityRu="Технические решения, декомпозиция, поток команды, качество"
        responsibilityEn="Technical decisions, decomposition, team flow, quality"
        stages={[
          {
            stageRu: 'Идея',
            stageEn: 'Idea',
            actionsRu: 'Оценивает техническую реализуемость',
            actionsEn: 'Assesses technical feasibility',
          },
          {
            stageRu: 'БТ (BRD)',
            stageEn: 'BRD',
            actionsRu: 'Подтверждает реализуемость',
            actionsEn: 'Confirms feasibility',
          },
          {
            stageRu: 'Грязные оценки',
            stageEn: 'Rough Estimates',
            actionsRu: 'Координирует сбор оценок от представителей ролей',
            actionsEn: 'Coordinates estimate collection from role representatives',
          },
          {
            stageRu: 'Планирование',
            stageEn: 'Planning',
            actionsRu: 'Декомпозирует Epic → Stories → Subtasks',
            actionsEn: 'Decomposes Epic → Stories → Subtasks',
          },
          {
            stageRu: 'Разработка',
            stageEn: 'Development',
            actionsRu: 'Мониторит DSR, блокеры, помогает команде',
            actionsEn: 'Monitors DSR, blockers, helps the team',
          },
          {
            stageRu: 'E2E',
            stageEn: 'E2E',
            actionsRu: 'Решает о необходимости, готовит тестовое окружение',
            actionsEn: 'Decides necessity, prepares test environment',
          },
          {
            stageRu: 'Приёмка',
            stageEn: 'Acceptance',
            actionsRu: 'Демонстрирует результат PO',
            actionsEn: 'Demonstrates result to PO',
          },
          {
            stageRu: 'Готово',
            stageEn: 'Done',
            actionsRu: 'Переводит Epic в Done, закрывает задачу',
            actionsEn: 'Moves Epic to Done, closes the task',
          },
        ]}
        keyScreens={[
          { label: 'Board', path: '/', descriptionRu: 'Доска', descriptionEn: 'Board' },
          { label: 'Data Quality', path: '/data-quality', descriptionRu: 'Качество данных', descriptionEn: 'Data quality' },
          { label: 'Timeline', path: '/?view=timeline', descriptionRu: 'Таймлайн', descriptionEn: 'Timeline' },
          { label: 'Team Members', path: '/teams/1', descriptionRu: 'Участники команды', descriptionEn: 'Team members' },
        ]}
      />

      {/* Pipeline Role Executors */}
      <RoleCardFull
        id="roles-executors"
        nameRu="Исполнители ролей конвейера"
        nameEn="Pipeline Role Executors"
        responsibilityRu="Выполняют работу в своей фазе конвейера, оценивают, логируют время"
        responsibilityEn="Execute work in their pipeline phase, estimate, log time"
        stages={[
          {
            stageRu: 'БТ (BRD)',
            stageEn: 'BRD',
            actionsRu: 'Участвуют в проработке требований',
            actionsEn: 'Participate in requirements elaboration',
          },
          {
            stageRu: 'Грязные оценки',
            stageEn: 'Rough Estimates',
            actionsRu: 'Дают грязные оценки по своей роли',
            actionsEn: 'Give rough estimates for their role',
          },
          {
            stageRu: 'Планирование',
            stageEn: 'Planning',
            actionsRu: 'Создают Subtask, оценивают через Planning Poker',
            actionsEn: 'Create Subtasks, estimate via Planning Poker',
          },
          {
            stageRu: 'Разработка',
            stageEn: 'Development',
            actionsRu: 'Берут задачи, переводят статусы, логируют время',
            actionsEn: 'Take tasks, move statuses, log time',
          },
        ]}
        keyScreens={[
          { label: 'Board', path: '/', descriptionRu: 'Доска', descriptionEn: 'Board' },
          { label: 'Member Profile', path: '/teams/1/member/1', descriptionRu: 'Профиль участника', descriptionEn: 'Member profile' },
          { label: 'Planning Poker', path: '/poker', descriptionRu: 'Покер планирования', descriptionEn: 'Planning Poker' },
        ]}
      />

      <p style={{ marginTop: 12, fontStyle: 'italic', color: 'var(--color-text-secondary, #666)' }}>
        {t(
          'Конкретные роли настраиваются в ',
          'Specific roles are configurable in '
        )}
        <Link to="/workflow">{t('Конфигурации Workflow', 'Workflow Config')}</Link>
        {t(
          '. По умолчанию: SA → DEV → QA. Возможный вариант: UX → SA → DEV → DEVOPS → QA.',
          '. Default: SA → DEV → QA. Possible variant: UX → SA → DEV → DEVOPS → QA.'
        )}
      </p>

      {/* DevOps */}
      <RoleCardFull
        id="roles-devops"
        nameRu="DevOps"
        nameEn="DevOps"
        responsibilityRu="Деплой, инфраструктура, мониторинг, откат"
        responsibilityEn="Deployment, infrastructure, monitoring, rollback"
        stages={[
          {
            stageRu: 'Готово',
            stageEn: 'Done',
            actionsRu: 'Деплоит в продакшен, мониторит, откатывает при необходимости',
            actionsEn: 'Deploys to production, monitors, rolls back if needed',
          },
        ]}
        keyScreens={[
          { label: 'Grafana / CI/CD', path: '/', descriptionRu: 'Внешние системы мониторинга и деплоя', descriptionEn: 'External monitoring and deployment systems' },
        ]}
      />

      <p style={{ marginTop: 12, fontStyle: 'italic', color: 'var(--color-text-secondary, #666)' }}>
        {t(
          'Ключевые экраны DevOps — внешние: Grafana (мониторинг), CI/CD (деплой), серверные логи.',
          'Key DevOps screens are external: Grafana (monitoring), CI/CD (deployment), server logs.'
        )}
      </p>
    </>
  )
}

const rolesContent = <RolesContent />

export const rolesSection: GuideSectionData = {
  id: 'roles',
  titleRu: 'Роли',
  titleEn: 'Roles',
  contentRu: rolesContent,
  contentEn: rolesContent,
}
