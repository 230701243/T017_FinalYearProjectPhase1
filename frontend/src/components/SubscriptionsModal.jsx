import React, { useState, useEffect } from 'react'
import { Repeat, Trash2, X, AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, toUsd, getCurrencySymbol, getCategoryDisplayName } from '../utils/currency'

function SubscriptionsModal({ isOpen, onClose, onSubscriptionsUpdated }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const currencySymbol = getCurrencySymbol(currency)

  const [subscriptions, setSubscriptions] = useState([])
  const [loading,       setLoading]       = useState(false)
  const [name,          setName]          = useState('')
  const [amount,        setAmount]        = useState('')
  const [category,      setCategory]      = useState('Utilities & Bills')
  const [dueDay,        setDueDay]        = useState('1')
  const [errorMsg,      setErrorMsg]      = useState('')
  const [submitting,    setSubmitting]    = useState(false)

  const fetchSubscriptions = () => {
    setLoading(true)
    api.get('/subscriptions')
      .then(res => setSubscriptions(res.data))
      .catch(err => console.error('Failed to load subscriptions', err))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (isOpen) {
      fetchSubscriptions()
    }
  }, [isOpen])

  if (!isOpen) return null

  const handleAddSubscription = async (e) => {
    e.preventDefault()
    if (!name || !amount || Number(amount) <= 0) {
      setErrorMsg(t('subscriptions.validationError'))
      return
    }

    setErrorMsg('')
    setSubmitting(true)

    try {
      await api.post('/subscriptions', {
        name: name.trim(),
        amount: toUsd(parseFloat(amount), currency),
        category,
        dueDay: parseInt(dueDay, 10),
        billingCycle: 'MONTHLY',
        isActive: true
      })

      setName('')
      setAmount('')
      fetchSubscriptions()
      if (onSubscriptionsUpdated) onSubscriptionsUpdated()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || t('common.error'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (id) => {
    if (!window.confirm(t('subscriptions.confirmDelete'))) return
    try {
      await api.delete(`/subscriptions/${id}`)
      fetchSubscriptions()
      if (onSubscriptionsUpdated) onSubscriptionsUpdated()
    } catch (err) {
      alert(t('subscriptions.deleteFailed'))
    }
  }

  const totalMonthlyCommitment = subscriptions
    .filter(s => s.active)
    .reduce((acc, curr) => acc + (Number(curr.amount) || 0), 0)

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: '600px' }}>
        <div className="modal-header">
          <div>
            <h2 className="modal-title">
              <Repeat size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
              {t('subscriptions.title')}
            </h2>
            <p style={{ fontSize: '12.5px', color: 'var(--text-muted)', margin: '2px 0 0' }}>
              {t('subscriptions.subtitle')}
            </p>
          </div>
          <button className="btn-close" onClick={onClose} aria-label={t('common.close')}>
            <X size={18} />
          </button>
        </div>

        {/* Add New Subscription Form */}
        <form onSubmit={handleAddSubscription} style={{ margin: '16px 0', padding: '16px', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
          <h4 style={{ fontSize: '13px', fontWeight: '700', marginBottom: '12px', color: 'var(--text-primary)' }}>
            {t('subscriptions.addCommitmentHeader')}
          </h4>

          {errorMsg && (
            <div className="alert alert-error" style={{ marginBottom: '12px' }}>
              <AlertTriangle size={15} />
              {errorMsg}
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '12px' }}>
            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('subscriptions.nameLabel')}</label>
              <input
                type="text"
                className="input-field"
                placeholder={t('subscriptions.namePlaceholder')}
                value={name}
                onChange={e => setName(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
                required
              />
            </div>

            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('subscriptions.monthlyAmountLabel', { symbol: currencySymbol })}</label>
              <input
                type="number"
                step="0.01"
                className="input-field"
                placeholder="50.00"
                value={amount}
                onChange={e => setAmount(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
                required
              />
            </div>

            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('subscriptions.categoryLabel')}</label>
              <select
                className="input-field"
                value={category}
                onChange={e => setCategory(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
              >
                <option value="Utilities & Bills">{getCategoryDisplayName('Utilities & Bills', t)}</option>
                <option value="Shopping & Tech">{getCategoryDisplayName('Shopping & Tech', t)}</option>
                <option value="Food & Dining">{getCategoryDisplayName('Food & Dining', t)}</option>
                <option value="Entertainment">{getCategoryDisplayName('Entertainment', t)}</option>
                <option value="Transportation">{getCategoryDisplayName('Transportation', t)}</option>
                <option value="Other">{getCategoryDisplayName('Other', t)}</option>
              </select>
            </div>

            <div>
              <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('subscriptions.dueDayLabel')}</label>
              <input
                type="number"
                min="1"
                max="31"
                className="input-field"
                value={dueDay}
                onChange={e => setDueDay(e.target.value)}
                style={{ height: '36px', fontSize: '13px' }}
                required
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn-primary"
            disabled={submitting}
            style={{ width: '100%', minHeight: '36px', fontSize: '13px' }}
          >
            {submitting ? t('subscriptions.savingCommitment') : t('subscriptions.addCommitmentBtn')}
          </button>
        </form>

        {/* Existing Subscriptions List */}
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
            <h4 style={{ fontSize: '13px', fontWeight: '700', color: 'var(--text-primary)' }}>
              {t('subscriptions.activeCommitmentsHeader', { count: subscriptions.length })}
            </h4>
            <span style={{ fontSize: '13px', fontWeight: '700', color: 'var(--text-primary)' }}>
              {t('subscriptions.totalMonthly', { amount: formatCurrency(totalMonthlyCommitment, currency) })}
            </span>
          </div>

          {loading ? (
            <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
              <span className="spinner" style={{ marginRight: '8px' }} /> {t('subscriptions.loadingCommitments')}
            </div>
          ) : subscriptions.length === 0 ? (
            <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '12.5px' }}>
              {t('subscriptions.noSubscriptionsYet')}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '200px', overflowY: 'auto' }}>
              {subscriptions.map(sub => (
                <div
                  key={sub.id}
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
                  <div>
                    <div style={{ fontWeight: '600', fontSize: '13px' }}>{sub.name}</div>
                    <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                      {t('subscriptions.dueOnDaySummary', { category: getCategoryDisplayName(sub.category, t), day: sub.dueDay })}
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={{ fontWeight: '700', color: 'var(--color-negative)', fontSize: '13.5px' }}>
                      {t('subscriptions.perMonth', { amount: formatCurrency(sub.amount, currency) })}
                    </span>
                    <button
                      onClick={() => handleDelete(sub.id)}
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

export default SubscriptionsModal
