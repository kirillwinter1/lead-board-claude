import { useState, FormEvent } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

interface AuditModalProps {
  isOpen: boolean
  onClose: () => void
}

interface FormData {
  name: string
  company: string
  role: string
  contact: string
}

export function AuditModal({ isOpen, onClose }: AuditModalProps) {
  const [formData, setFormData] = useState<FormData>({
    name: '',
    company: '',
    role: '',
    contact: ''
  })
  const [errors, setErrors] = useState<Partial<FormData>>({})
  const [submitted, setSubmitted] = useState(false)
  const [loading, setLoading] = useState(false)

  const validate = (): boolean => {
    const newErrors: Partial<FormData> = {}

    if (!formData.name.trim()) {
      newErrors.name = 'Укажите имя'
    }

    if (!formData.contact.trim()) {
      newErrors.contact = 'Укажите Telegram или email'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()

    if (!validate()) return

    setLoading(true)

    try {
      const response = await fetch('/api/audit-requests', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: formData.name,
          company: formData.company,
          role: formData.role,
          contact: formData.contact,
        }),
      })

      if (!response.ok) {
        throw new Error('Failed to submit')
      }

      setSubmitted(true)
    } catch (error) {
      console.error('Error submitting audit request:', error)
      setSubmitted(true)
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setFormData({ name: '', company: '', role: '', contact: '' })
    setErrors({})
    setSubmitted(false)
    onClose()
  }

  const handleChange = (field: keyof FormData) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    setFormData(prev => ({ ...prev, [field]: e.target.value }))
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }))
    }
  }

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="modal-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={handleClose}
        >
          <motion.div
            className="modal-content"
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            onClick={e => e.stopPropagation()}
          >
            <button className="modal-close" onClick={handleClose}>
              &times;
            </button>

            {submitted ? (
              <div className="modal-success">
                <div className="modal-success-icon">✓</div>
                <h3>Заявка отправлена</h3>
                <p>Свяжемся с вами в течение 24 часов для согласования времени разбора.</p>
                <button
                  className="landing-btn landing-btn-primary"
                  onClick={handleClose}
                >
                  Закрыть
                </button>
              </div>
            ) : (
              <>
                <h2 className="modal-title">Запросить разбор</h2>
                <p className="modal-subtitle">
                  30 минут — разберём вашу ситуацию и покажем, как сделать сроки предсказуемыми
                </p>

                <form className="modal-form" onSubmit={handleSubmit}>
                  <div className="form-group">
                    <label className="form-label">
                      Имя <span className="required">*</span>
                    </label>
                    <input
                      type="text"
                      className={`form-input ${errors.name ? 'error' : ''}`}
                      placeholder="Как к вам обращаться"
                      value={formData.name}
                      onChange={handleChange('name')}
                    />
                    {errors.name && <span className="form-error">{errors.name}</span>}
                  </div>

                  <div className="form-group">
                    <label className="form-label">Компания</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="Название компании"
                      value={formData.company}
                      onChange={handleChange('company')}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">Роль</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="Ваша роль в компании"
                      value={formData.role}
                      onChange={handleChange('role')}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">
                      Telegram, телефон или Email <span className="required">*</span>
                    </label>
                    <input
                      type="text"
                      className={`form-input ${errors.contact ? 'error' : ''}`}
                      placeholder="@username"
                      value={formData.contact}
                      onChange={handleChange('contact')}
                    />
                    {errors.contact && <span className="form-error">{errors.contact}</span>}
                  </div>

                  <button
                    type="submit"
                    className="landing-btn landing-btn-primary landing-btn-large modal-submit"
                    disabled={loading}
                  >
                    {loading ? 'Отправка...' : 'Отправить заявку'}
                  </button>
                  <p className="modal-response-note">
                    Ответим в течение 1 рабочего дня
                  </p>
                </form>
              </>
            )}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
