import { LandingHeader } from './components/LandingHeader'
import { HeroSection } from './sections/HeroSection'
import { ProblemSection } from './sections/ProblemSection'
import { SolutionSection } from './sections/SolutionSection'
import { FeaturesSection } from './sections/FeaturesSection'
import { DemoSection } from './sections/DemoSection'
import { SocialProofSection } from './sections/SocialProofSection'
import { CTASection } from './sections/CTASection'
import './LandingPage.css'

export function LandingPage() {
  return (
    <div className="landing-page">
      <LandingHeader />
      <HeroSection />
      <ProblemSection />
      <SolutionSection />
      <FeaturesSection />
      <DemoSection />
      <SocialProofSection />
      <CTASection />
      <footer className="landing-footer">
        <p className="landing-footer-text">
          Lead Board {new Date().getFullYear()}
        </p>
      </footer>
    </div>
  )
}
