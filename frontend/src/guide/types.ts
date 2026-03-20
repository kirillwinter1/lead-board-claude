import { ReactNode } from 'react'

export type GuideLang = 'ru' | 'en'

export interface GuideSectionData {
  id: string
  titleRu: string
  titleEn: string
  contentRu: ReactNode
  contentEn: ReactNode
}

export interface GuideScreenLink {
  label: string
  path: string
  descriptionRu: string
  descriptionEn: string
}

export interface GuideChecklistItem {
  textRu: string
  textEn: string
}

export interface GuideSidebarItem {
  id: string
  titleRu: string
  titleEn: string
  children?: GuideSidebarItem[]
}
