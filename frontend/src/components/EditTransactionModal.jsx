import { useState, useEffect } from 'react'
import { Edit3, X, AlertTriangle, AlertCircle, Clock } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { convertUsd, STATIC_RATES, getCurrencySymbol, getCategoryDisplayName } from '../utils/currency'

export const EXPENSE_CATEGORIES = [
  'Food & Dining',
  'Utilities & Bills',
  'Shopping & Tech',
  'Transportation',
  'Entertainment',
  'Subscriptions',
  'Healthcare',
  'Insurance',
  'Investments',
  'Uncategorized'
]

export const INCOME_CATEGORIES = [
  'Income & Salary',
  'Freelance & Business',
  'Investments',
  'Uncategorized'
]

function EditTransactionModal({ isOpen, onClose, transaction, type = 'expense', onSaved }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const currencyCode = user?.preferredCurrency || 'USD'
  const currencySymbol = getCurrencySymbol(currencyCode)

  const [amount, setAmount] = useState('')
  const [initialPrefAmountStr, setInitialPrefAmountStr] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('UPI')
  const [source, setSource] = useState('Salary')
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loading, setLoading] = useState(false)

  // Keyboard shortcut: close on Escape
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  useEffect(() => {
    if (transaction) {
      const rawUsd = Number(transaction.amount || 0)
      const converted = convertUsd(rawUsd, currencyCode)
      // Format to at least 2 decimals without unnecessary trailing zeros if integer
      const formatted = Number.isInteger(converted)
        ? converted.toString()
        : Number(converted.toFixed(2)).toString()

      setAmount(formatted)
      setInitialPrefAmountStr(formatted)
      setDescription(transaction.description || '')
      setCategory(transaction.category || 'Uncategorized')
      setPaymentMethod(transaction.paymentMethod || 'UPI')
      setSource(transaction.source || 'Salary')
      setError('')
      setFieldErrors({})
    }
  }, [transaction, currencyCode, isOpen])

  if (!isOpen || !transaction) return null

  const categories = type === 'expense' ? EXPENSE_CATEGORIES : INCOME_CATEGORIES

  const validateForm = () => {
    const errors = {}
    const numAmount = parseFloat(amount)
    if (!amount || isNaN(numAmount) || numAmount <= 0) {
      errors.amount = t('modals.enterValidAmount')
    }
    if (!description.trim()) {
      errors.description = t('modals.descriptionRequired')
    }
    if (!category) {
      errors.category = t('modals.categoryRequired')
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!validateForm()) {
      return
    }

    const numAmount = parseFloat(amount)

    // Determine if the user modified the displayed amount
    const originalPrefNum = parseFloat(initialPrefAmountStr)
    const isAmountChanged = Math.abs(numAmount - originalPrefNum) > 0.0001

    let finalUsdAmount = transaction.amount
    if (isAmountChanged) {
      // Input-side conversion: convert from preferred currency to USD with high precision
      const rate = STATIC_RATES[currencyCode]?.rate || 1.0
      const highPrecisionUsd = numAmount / rate
      finalUsdAmount = Number(highPrecisionUsd.toFixed(8))
    }

    setLoading(true)
    try {
      const payload = {
        amount: finalUsdAmount,
        description: description.trim(),
        category,
        amountModified: isAmountChanged,
        ...(type === 'expense' ? { paymentMethod } : { source })
      }

      // Endpoint: PUT /api/transactions/{id} for expense, PUT /api/income/{id} for income
      const endpoint = type === 'expense'
        ? `/transactions/${transaction.id}`
        : `/income/${transaction.id}`

      const res = await api.put(endpoint, payload)
      if (onSaved) {
        onSaved(res.data)
      }
      onClose()
    } catch (err) {
      setError(err.response?.data?.message || `Failed to update ${type}. Please try again.`)
    } finally {
      setLoading(false)
    }
  }

  const formattedTimestamp = transaction.createdAt
    ? new Date(transaction.createdAt).toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    : 'N/A'

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true" aria-labelledby="edit-modal-title">
      <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: '480px' }}>
        <div className="modal-header">
          <h2 id="edit-modal-title" className="modal-title">
            <Edit3 size={18} strokeWidth={2} style={{ color: 'var(--accent-primary)' }} />
            {type === 'expense' ? t('modals.editExpenseTitle', { id: transaction.id }) : t('modals.editIncomeTitle', { id: transaction.id })}
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
            <label className="field-label" htmlFor="edit-tx-amount">
              <span>{t('modals.amountLabel', { symbol: currencySymbol, code: currencyCode })}</span>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 400 }}>{t('modals.storedInUsd')}</span>
            </label>
            <input
              id="edit-tx-amount"
              type="number"
              step="any"
              min="0.01"
              value={amount}
              onChange={(e) => {
                setAmount(e.target.value)
                if (fieldErrors.amount) setFieldErrors(prev => ({ ...prev, amount: null }))
              }}
              placeholder={`e.g. ${currencyCode === 'INR' ? '2500.00' : '25.00'}`}
              className={fieldErrors.amount ? 'input-has-error' : ''}
              required
              autoFocus
            />
            {fieldErrors.amount ? (
              <div className="field-error">
                <AlertCircle size={13} />
                <span>{fieldErrors.amount}</span>
              </div>
            ) : (
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                {t('modals.displayedInPrefCurrency', { code: currencyCode })}
              </span>
            )}
          </div>

          {/* Field 2: Description */}
          <div className="form-group">
            <label className="field-label" htmlFor="edit-tx-desc">{t('modals.descriptionLabel')}</label>
            <input
              id="edit-tx-desc"
              type="text"
              value={description}
              onChange={(e) => {
                setDescription(e.target.value)
                if (fieldErrors.description) setFieldErrors(prev => ({ ...prev, description: null }))
              }}
              placeholder="e.g. Grocery shopping"
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

          {/* Field 3: Category */}
          <div className="form-group">
            <label className="field-label" htmlFor="edit-tx-category">{t('modals.categoryLabel')}</label>
            <select
              id="edit-tx-category"
              value={category}
              onChange={(e) => {
                setCategory(e.target.value)
                if (fieldErrors.category) setFieldErrors(prev => ({ ...prev, category: null }))
              }}
              required
              className={`form-select ${fieldErrors.category ? 'input-has-error' : ''}`}
            >
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {getCategoryDisplayName(cat, t)}
                </option>
              ))}
            </select>
            {fieldErrors.category ? (
              <div className="field-error">
                <AlertCircle size={13} />
                <span>{fieldErrors.category}</span>
              </div>
            ) : (
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                {t('modals.manualCategoryHint')}
              </span>
            )}
          </div>

          {/* Field 4: Payment Method (Expense) or Source (Income) */}
          {type === 'expense' ? (
            <div className="form-group">
              <label className="field-label" htmlFor="edit-tx-payment">{t('modals.paymentMethod')}</label>
              <select
                id="edit-tx-payment"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value)}
                className="form-select"
              >
                <option value="UPI">UPI</option>
                <option value="Cash">Cash</option>
                <option value="Credit">Credit Card</option>
              </select>
            </div>
          ) : (
            <div className="form-group">
              <label className="field-label" htmlFor="edit-tx-source">{t('modals.incomeSource')}</label>
              <select
                id="edit-tx-source"
                value={source}
                onChange={(e) => setSource(e.target.value)}
                className="form-select"
              >
                <option value="Salary">Salary</option>
                <option value="Freelance">Freelance</option>
                <option value="Investment">Investment</option>
                <option value="Other">Other</option>
              </select>
            </div>
          )}

          {/* Read-only Auto-recorded Timestamp */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            fontSize: '12px',
            color: 'var(--text-muted)',
            padding: '8px 12px',
            background: 'var(--bg-tertiary)',
            borderRadius: 'var(--radius-sm)',
            marginBottom: '14px'
          }}>
            <Clock size={13} />
            <span>{t('modals.originalTimestamp', { timestamp: formattedTimestamp })}</span>
          </div>

          <div className="modal-actions">
            <button
              id="cancel-edit-btn"
              type="button"
              className="btn-secondary"
              onClick={onClose}
              disabled={loading}
            >
              {t('common.cancel')}
            </button>
            <button
              id="save-edit-btn"
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

export default EditTransactionModal
