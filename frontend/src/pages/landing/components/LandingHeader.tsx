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

  return (
    <header className={`landing-header ${scrolled ? 'scrolled' : ''}`}>
      <div className="landing-header-inner">
        <Link to="/" className="landing-logo">
          <img src={logoIcon} alt="Lead Board" className="landing-logo-icon" />
          Lead Board
        </Link>
        <nav className="landing-nav">
          <a href="#problem" className="landing-nav-link">Проблема</a>
          <a href="#method" className="landing-nav-link">Метод</a>
          <a href="#audit" className="landing-nav-link">Аудит</a>
          <a href="#pilot" className="landing-nav-link">Пилот</a>
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
