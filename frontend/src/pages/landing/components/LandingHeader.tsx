import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import logoIcon from '../../../icons/logo.png'

interface LandingHeaderProps {
  onRequestAudit: () => void
}

export function LandingHeader({ onRequestAudit }: LandingHeaderProps) {
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20)
    }
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const scrollToSection = (e: React.MouseEvent<HTMLAnchorElement>, sectionId: string) => {
    e.preventDefault()
    const element = document.getElementById(sectionId)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }

  return (
    <header className={`landing-header ${scrolled ? 'scrolled' : ''}`}>
      <div className="landing-header-inner">
        <Link to="/" className="landing-logo">
          <img src={logoIcon} alt="OneLane" className="landing-logo-icon" />
        </Link>
        <nav className="landing-nav">
          <a href="#problem" className="landing-nav-link" onClick={(e) => scrollToSection(e, 'problem')}>Проблема</a>
          <a href="#method" className="landing-nav-link" onClick={(e) => scrollToSection(e, 'method')}>Демо</a>
          <a href="#audit" className="landing-nav-link" onClick={(e) => scrollToSection(e, 'audit')}>Аудит и пилот</a>
        </nav>
        <div className="landing-header-actions">
          <button
            onClick={onRequestAudit}
            className="landing-btn landing-btn-primary"
          >
            Запросить разбор
          </button>
        </div>
      </div>
    </header>
  )
}
