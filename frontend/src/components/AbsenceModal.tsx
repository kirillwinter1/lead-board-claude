import { useState, useEffect } from 'react'
import { Modal } from './Modal'
import { Absence, AbsenceType, CreateAbsenceRequest } from '../api/teams'

const ABSENCE_TYPE_LABELS: Record<AbsenceType, string> = {
  VACATION: 'Отпуск',
  SICK_LEAVE: 'Больничный',
  DAY_OFF: 'Отгул',
  OTHER: 'Другое',
}

const ABSENCE_TYPES: AbsenceType[] = ['VACATION', 'SICK_LEAVE', 'DAY_OFF', 'OTHER']

interface AbsenceModalProps {
  isOpen: boolean
  onClose: () => void
  onSave: (data: CreateAbsenceRequest) => Promise<void>
  onDelete?: () => Promise<void>
  absence?: Absence | null
}

export function AbsenceModal({ isOpen, onClose, onSave, onDelete, absence }: AbsenceModalProps) {
  const [absenceType, setAbsenceType] = useState<AbsenceType>('VACATION')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (absence) {
      setAbsenceType(absence.absenceType)
      setStartDate(absence.startDate)
      setEndDate(absence.endDate)
      setComment(absence.comment || '')
    } else {
      const today = new Date().toISOString().slice(0, 10)
      setAbsenceType('VACATION')
      setStartDate(today)
      setEndDate(today)
      setComment('')
    }
    setError(null)
  }, [absence, isOpen])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!startDate || !endDate) return
    setSaving(true)
    setError(null)
    try {
      await onSave({ absenceType, startDate, endDate, comment: comment || undefined })
      onClose()
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Failed to save')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!onDelete) return
    if (!confirm('Удалить это отсутствие?')) return
    setSaving(true)
    try {
      await onDelete()
      onClose()
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Failed to delete')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={absence ? 'Редактировать отсутствие' : 'Добавить отсутствие'}>
      <form onSubmit={handleSubmit} className="modal-form">
        {error && (
          <div style={{ color: '#de350b', fontSize: 13, marginBottom: 12, padding: '8px 12px', background: '#ffebe6', borderRadius: 4 }}>
            {error}
          </div>
        )}
        <div className="form-group">
          <label htmlFor="absenceType">Тип</label>
          <select
            id="absenceType"
            value={absenceType}
            onChange={e => setAbsenceType(e.target.value as AbsenceType)}
          >
            {ABSENCE_TYPES.map(t => (
              <option key={t} value={t}>{ABSENCE_TYPE_LABELS[t]}</option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="startDate">Начало</label>
            <input
              id="startDate"
              type="date"
              value={startDate}
              onChange={e => setStartDate(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="endDate">Конец</label>
            <input
              id="endDate"
              type="date"
              value={endDate}
              onChange={e => setEndDate(e.target.value)}
              required
            />
          </div>
        </div>
        <div className="form-group">
          <label htmlFor="comment">Комментарий</label>
          <input
            id="comment"
            type="text"
            value={comment}
            onChange={e => setComment(e.target.value)}
            placeholder="Необязательно"
            maxLength={500}
          />
        </div>
        <div className="modal-actions">
          {absence && onDelete && (
            <button
              type="button"
              className="btn btn-danger"
              onClick={handleDelete}
              disabled={saving}
              style={{ marginRight: 'auto' }}
            >
              Удалить
            </button>
          )}
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Отмена
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Сохранение...' : (absence ? 'Сохранить' : 'Добавить')}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export { ABSENCE_TYPE_LABELS }
