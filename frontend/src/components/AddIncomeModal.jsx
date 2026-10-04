import { useState, useEffect } from 'react'
import { ArrowUpRight, X, AlertTriangle, AlertCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { toUsd, getCurrencySymbol } from '../utils/currency'

function AddIncomeModal({ isOpen, onClose, onIncomeAdded }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const currencySymbol = getCurrencySymbol(user?.preferredCurrency)
  const currencyCode = user?.preferredCurrency || 'USD'

  const [amount,      setAmount]      = useState('')
  const [description, setDescription] = useState('')
  const [source,      setSource]      = useState('Salary')
  const [error,       setError]       = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loading,     setLoading]     = useState(false)

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  const validate = () => {
    const errors = {}
    const numAmount = parseFloat(amount)
    if (!amount || isNaN(numAmount) || numAmount <= 0) {
      errors.amount = t('modals.enterValidAmount')
    }
    if (!description.trim()) {
      errors.description = t('modals.descriptionRequired')
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!validate()) return

    setLoading(true)
    try {
      // Convert entered amount to standard USD for backend persistence
      const numAmount = parseFloat(amount)
      const usdAmount = toUsd(numAmount, currencyCode)
      const payload = {
        amount: usdAmount,
        description: description.trim(),
        source,
      }
      const res = await api.post('/incomes', payload)

      // Reset form & notify parent
      setAmount('')
      setDescription('')
      setSource('Salary')
      setFieldErrors({})
      onIncomeAdded(res.data)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message || t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true" aria-labelledby="income-modal-title">
      <div className="modal-card" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2 id="income-modal-title" className="modal-title">
            <ArrowUpRight size={18} strokeWidth={2} style={{ color: 'var(--color-positive)' }} />
            {t('modals.addIncomeTitle')}
          </h2>
          <button className="btn-close" onClick={onClose} aria-label={t('common.close')}>
            <X size={18} />
          </button>
        </div>

        {error && (
          <div className="alert alert-error" role="alert" style={{ marginBottom: '16px' }}>
            <AlertTriangle size={15} />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form" noValidate>
          {/* Field 1: Amount */}
          <div className="form-group">
            <label className="field-label" htmlFor="income-amount">
              <span>{t('modals.amountLabel', { symbol: currencySymbol, code: currencyCode })}</span>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 400 }}>{t('modals.convertsToUsd')}</span>
            </label>
            <input
              id="income-amount"
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(e) => {
                setAmount(e.target.value)
                if (fieldErrors.amount) setFieldErrors(prev => ({ ...prev, amount: null }))
              }}
              placeholder={`e.g. ${currencyCode === 'INR' ? '50000.00' : '5000.00'}`}
              className={fieldErrors.amount ? 'input-has-error' : ''}
              required
              autoFocus
            />
            {fieldErrors.amount && (
              <div className="field-error">
                <AlertCircle size={13} />
                <span>{fieldErrors.amount}</span>
              </div>
            )}
          </div>

          {/* Field 2: Description */}
          <div className="form-group">
            <label className="field-label" htmlFor="income-desc">{t('modals.descriptionLabel')}</label>
            <input
              id="income-desc"
              type="text"
              value={description}
              onChange={(e) => {
                setDescription(e.target.value)
                if (fieldErrors.description) setFieldErrors(prev => ({ ...prev, description: null }))
              }}
              placeholder="e.g. Monthly salary payout"
              className={fieldErrors.description ? 'input-has-error' : ''}
              required
            />
            {fieldErrors.description && (
              <div className="field-error">
                <AlertCircle size={13} />
                <span>{fieldErrors.description}</span>
              </div>
            )}
          </div>

          {/* Field 3: Source */}
          <div className="form-group">
            <label className="field-label" htmlFor="income-source">{t('modals.incomeSource')}</label>
            <select
              id="income-source"
              value={source}
              onChange={(e) => setSource(e.target.value)}
              required
              className="form-select"
            >
              <option value="Salary">Salary</option>
              <option value="Freelance">Freelance</option>
              <option value="Investment">Investment</option>
              <option value="Other">Other</option>
            </select>
          </div>

          <div className="modal-actions">
            <button
              type="button"
              className="btn-secondary"
              onClick={onClose}
              disabled={loading}
            >
              {t('common.cancel')}
            </button>
            <button
              id="submit-income-btn"
              type="submit"
              className="btn-primary"
              disabled={loading}
            >
              {loading ? <span className="spinner" /> : t('common.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default AddIncomeModal

