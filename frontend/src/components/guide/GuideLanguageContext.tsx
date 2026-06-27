import { createContext, useContext, useState, useCallback, ReactNode } from 'react'
import { GuideLang } from '../../guide/types'

interface GuideLanguageContextValue {
  lang: GuideLang
  toggleLang: () => void
  t: (ru: string, en: string) => string
}

const GuideLanguageContext = createContext<GuideLanguageContextValue>({
  lang: 'ru',
  toggleLang: () => {},
  t: (ru) => ru,
})

const STORAGE_KEY = 'guide-lang'

export function GuideLanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<GuideLang>(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'en' ? 'en' : 'ru'
  })

  const toggleLang = useCallback(() => {
    setLang(prev => {
      const next = prev === 'ru' ? 'en' : 'ru'
      localStorage.setItem(STORAGE_KEY, next)
      return next
    })
  }, [])

  const t = useCallback((ru: string, en: string) => lang === 'ru' ? ru : en, [lang])

  return (
    <GuideLanguageContext.Provider value={{ lang, toggleLang, t }}>
      {children}
    </GuideLanguageContext.Provider>
  )
}

export function useGuideLang() {
  return useContext(GuideLanguageContext)
}
