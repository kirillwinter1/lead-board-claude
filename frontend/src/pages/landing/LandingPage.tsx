import { useState } from 'react'
import { LandingHeader } from './components/LandingHeader'
import { AuditModal } from './components/AuditModal'
import { HeroSection } from './sections/HeroSection'
import { ICPSection } from './sections/ICPSection'
import { ProblemSection } from './sections/ProblemSection'
import { MethodSection } from './sections/MethodSection'
import { BaselineSection } from './sections/BaselineSection'
import { AuditSection } from './sections/AuditSection'
import { PilotSection } from './sections/PilotSection'
import { DifferentiationSection } from './sections/DifferentiationSection'
import { CaseSection } from './sections/CaseSection'
import { FounderSection } from './sections/FounderSection'
import './LandingPage.css'

export function LandingPage() {
  const [isModalOpen, setIsModalOpen] = useState(false)

  const openModal = () => setIsModalOpen(true)
  const closeModal = () => setIsModalOpen(false)

  return (
    <div className="landing-page landing-page-dense">
      <LandingHeader onRequestAudit={openModal} />
      <HeroSection onRequestAudit={openModal} />
      <ICPSection />
      <ProblemSection />
      <MethodSection onRequestDemo={openModal} />
      <BaselineSection />
      <AuditSection onRequestAudit={openModal} />
      <PilotSection />
      <DifferentiationSection />
      <CaseSection onRequestAudit={openModal} />
      <FounderSection />
      <footer className="landing-footer">
        <p className="landing-footer-text">
          Lead Board {new Date().getFullYear()}
        </p>
      </footer>
      <AuditModal isOpen={isModalOpen} onClose={closeModal} />
    </div>
  )
}
