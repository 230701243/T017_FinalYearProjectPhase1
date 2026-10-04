import React, { useState, useEffect } from 'react'
import { Target, Trash2, X, AlertTriangle, ArrowUpRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, getCurrencySymbol, toUsd } from '../utils/currency'

function SavingsGoalModal({ isOpen, onClose, onGoalsUpdated }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const symbol = getCurrencySymbol(currency)

  const [goals,        setGoals]        = useState([])
  const [loading,      setLoading]      = useState(false)
  const [name,         setName]         = useState('')
  const [targetAmount, setTargetAmount] = useState('')
  const [currentAmount,setCurrentAmount]= useState('0.00')
  const [targetDate,   setTargetDate]   = useState('')
  const [errorMsg,     setErrorMsg]     = useState('')
  const [submitting,   setSubmitting]   = useState(false)

  const fetchGoals = () => {
    setLoading(true)
    api.get('/goals')
      .then(res => setGoals(res.data))
      .catch(err => console.error('Failed to load savings goals', err))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (isOpen) fetchGoals()
  }, [isOpen])

  if (!isOpen) return null

  const handleAddGoal = async (e) => {
    e.preventDefault()
    if (!name || !targetAmount || Number(targetAmount) <= 0) {
      setErrorMsg(t('goals.validationError'))
      return
    }

    setErrorMsg('')
    setSubmitting(true)

    try {
      await api.post('/goals', {
        name: name.trim(),
        targetAmount: toUsd(parseFloat(targetAmount), currency),
        currentAmount: toUsd(parseFloat(currentAmount || 0), currency),
        targetDate: targetDate || null
      })

      setName('')
      setTargetAmount('')
      setCurrentAmount('0.00')
      setTargetDate('')
      fetchGoals()
      if (onGoalsUpdated) onGoalsUpdated()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || t('common.error'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (id) => {
    if (!window.confirm(t('goals.confirmDelete'))) return
    try {
      await api.delete(`/goals/${id}`)
      fetchGoals()
      if (onGoalsUpdated) onGoalsUpdated()
    } catch (err) {
      alert(t('goals.deleteFailed'))
    }
  }

  const handleDeposit = async (goal) => {
    const addAmt = window.prompt(t('goals.depositPrompt', { name: goal.name, symbol }), '100.00')
    if (!addAmt || isNaN(addAmt) || Number(addAmt) <= 0) return

    const addUsd = toUsd(parseFloat(addAmt), currency)
    const newCurrent = Number(goal.currentAmount) + addUsd
    try {
      await api.put(`/goals/${goal.id}`, {
        name: goal.name,
        targetAmount: goal.targetAmount,
        currentAmount: newCurrent
      })
      fetchGoals()
      if (onGoalsUpdated) onGoalsUpdated()
    } catch (err) {
      alert(t('goals.depositFailed'))
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: '600px' }}>
        <div className="modal-header">
          <div>
            <h2 className="modal-title">
              <Target size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
              {t('goals.managerTitle')}
            </h2>
            <p style={{ fontSize: '12.5px', color: 'var(--text-muted)', margin: '2px 0 0' }}>
              {t('goals.managerSubtitle')}
            </p>
          </div>
          <button className="btn-close" onClick={onClose} aria-label={t('common.close')}>
            <X size={18} />
          </button>
        </div>

        {/* Add Goal Form */}
        <form onSubmit={handleAddGoal} style={{ margin: '16px 0', padding: '16px', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
          <h4 style={{ fontSize: '13px', fontWeight: '700', marginBottom: '12px', color: 'var(--text-primary)' }}>
            {t('goals.createTargetHeader')}
          </h4>

          {errorMsg && (
            <div className="alert alert-error" style={{ marginBottom: '12px' }}>
              <AlertTriangle size={15} />
              {errorMsg}
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '10px', marginBottom: '12px' }}>
            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('goals.goalNameLabel')}</label>
              <input
                type="text"
                className="input-field"
                placeholder={t('goals.goalNamePlaceholder')}
                value={name}
                onChange={e => setName(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
                required
              />
            </div>

            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('goals.targetAmountLabel', { symbol })}</label>
              <input
                type="number"
                step="0.01"
                className="input-field"
                placeholder="5000.00"
                value={targetAmount}
                onChange={e => setTargetAmount(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
                required
              />
            </div>

            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('goals.initialSavedLabel', { symbol })}</label>
              <input
                type="number"
                step="0.01"
                className="input-field"
                placeholder="500.00"
                value={currentAmount}
                onChange={e => setCurrentAmount(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
              />
            </div>

            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('goals.targetDateLabel')}</label>
              <input
                type="date"
                className="input-field"
                value={targetDate}
                onChange={e => setTargetDate(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn-primary"
            disabled={submitting}
            style={{ width: '100%', minHeight: '36px', fontSize: '13px' }}
          >
            {submitting ? t('goals.savingGoal') : t('goals.saveGoal')}
          </button>
        </form>

        {/* Existing Goals List */}
        <div>
          <h4 style={{ fontSize: '13px', fontWeight: '700', marginBottom: '10px', color: 'var(--text-primary)' }}>
            {t('goals.activeGoalsHeader', { count: goals.length })}
          </h4>

          {loading ? (
            <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
              <span className="spinner" style={{ marginRight: '8px' }} /> {t('goals.loadingGoals')}
            </div>
          ) : goals.length === 0 ? (
            <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '12.5px' }}>
              {t('goals.noGoalsCreatedYet')}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '220px', overflowY: 'auto' }}>
              {goals.map(g => (
                <div
                  key={g.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 14px',
                    background: 'var(--bg-tertiary)',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border-subtle)'
                  }}
                >
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: '600', fontSize: '13px' }}>{g.name}</div>
                    <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                      {t('goals.savedProgressSummary', {
                        current: formatCurrency(g.currentAmount, currency),
                        target: formatCurrency(g.targetAmount, currency),
                        percent: g.progressPercentage,
                        date: g.estimatedCompletionDate || '—'
                      })}
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button
                      className="btn-secondary"
                      onClick={() => handleDeposit(g)}
                      style={{ fontSize: '11.5px', padding: '4px 10px', minHeight: '28px' }}
                      title={t('goals.depositBtn')}
                    >
                      <ArrowUpRight size={13} style={{ color: 'var(--color-positive)' }} /> {t('goals.depositBtn')}
                    </button>

                    <button
                      onClick={() => handleDelete(g.id)}
                      style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '4px' }}
                      title={t('common.delete')}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="modal-footer" style={{ marginTop: '20px' }}>
          <button className="btn-secondary" onClick={onClose} style={{ width: '100%', minHeight: '38px' }}>
            {t('common.done')}
          </button>
        </div>
      </div>
    </div>
  )
}

export default SavingsGoalModal
