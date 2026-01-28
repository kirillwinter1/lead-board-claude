import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import logoIcon from '../../../icons/logo.png'

export function LandingHeader() {
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20)
    }
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const scrollToWaitlist = () => {
    const ctaSection = document.getElementById('waitlist')
    if (ctaSection) {
      ctaSection.scrollIntoView({ behavior: 'smooth' })
    }
  }

  return (
    <header className={`landing-header ${scrolled ? 'scrolled' : ''}`}>
      <div className="landing-header-inner">
        <Link to="/landing" className="landing-logo">
          <img src={logoIcon} alt="Lead Board" className="landing-logo-icon" />
          Lead Board
        </Link>
        <div className="landing-header-actions">
          <Link to="/" className="landing-btn landing-btn-ghost">
            Войти
          </Link>
          <button
            onClick={scrollToWaitlist}
            className="landing-btn landing-btn-primary"
          >
            Попробовать бесплатно
          </button>
        </div>
      </div>
    </header>
  )
}
