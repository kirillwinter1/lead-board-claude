import { GuideSidebarItem } from '../types'

export const guideSidebarItems: GuideSidebarItem[] = [
  { id: 'overview', titleRu: 'Обзор процесса', titleEn: 'Process Overview' },
  { id: 'rules', titleRu: 'Правила и ограничения', titleEn: 'Rules & Constraints' },
  {
    id: 'pipeline',
    titleRu: 'Конвейер',
    titleEn: 'Pipeline',
    children: [
      { id: 'pipeline-idea', titleRu: '1. Идея', titleEn: '1. Idea' },
      { id: 'pipeline-brd', titleRu: '2. Бизнес-требования', titleEn: '2. Business Requirements' },
      { id: 'pipeline-rough-estimates', titleRu: '3. Грязные оценки', titleEn: '3. Rough Estimates' },
      { id: 'pipeline-planning', titleRu: '4. Планирование', titleEn: '4. Planning' },
      { id: 'pipeline-development', titleRu: '5. Разработка', titleEn: '5. Development' },
      { id: 'pipeline-e2e', titleRu: '6. E2E', titleEn: '6. E2E' },
      { id: 'pipeline-acceptance', titleRu: '7. Приёмка', titleEn: '7. Acceptance' },
      { id: 'pipeline-done', titleRu: '8. Готово', titleEn: '8. Done' },
    ],
  },
  {
    id: 'roles',
    titleRu: 'Роли',
    titleEn: 'Roles',
    children: [
      { id: 'roles-po', titleRu: 'Product Owner', titleEn: 'Product Owner' },
      { id: 'roles-dm', titleRu: 'Delivery Manager', titleEn: 'Delivery Manager' },
      { id: 'roles-tl', titleRu: 'Team Lead', titleEn: 'Team Lead' },
      { id: 'roles-executors', titleRu: 'Исполнители', titleEn: 'Pipeline Executors' },
      { id: 'roles-devops', titleRu: 'DevOps', titleEn: 'DevOps' },
    ],
  },
]
