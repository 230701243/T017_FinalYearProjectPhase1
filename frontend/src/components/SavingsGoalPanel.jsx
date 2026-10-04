import React from 'react'
import { Target, Plus, Calendar, CheckCircle2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, renderLocalizedMessage } from '../utils/currency'

function SavingsGoalPanel({ goals = [], loading, onOpenGoalsModal }) {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'

  if (loading) {
    return (
      <div className="chart-card skeleton-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="skeleton skeleton-title" />
          <div className="skeleton" style={{ width: '110px', height: '32px', borderRadius: '8px' }} />
        </div>
        <div className="goal-cards-grid">
          <div className="skeleton" style={{ height: '180px', borderRadius: '14px' }} />
          <div className="skeleton" style={{ height: '180px', borderRadius: '14px' }} />
        </div>
      </div>
    )
  }

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <Target size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('goals.title')}
          </h3>
          <span className="chart-subtitle">
            {t('goals.subtitle')}
          </span>
        </div>

        <button
          className="btn-secondary"
          onClick={onOpenGoalsModal}
          style={{ fontSize: '13px', padding: '8px 16px' }}
        >
          <Plus size={15} /> {t('goals.manageGoals', { count: goals.length })}
        </button>
      </div>

      {goals.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">
            <Target size={24} />
          </div>
          <div className="empty-state-title">{t('goals.noGoalsTitle')}</div>
          <p className="empty-state-desc">
            {t('goals.noGoalsDesc')}
          </p>
          <button
            className="btn-primary"
            onClick={onOpenGoalsModal}
            style={{ marginTop: '6px' }}
          >
            <Plus size={16} /> {t('goals.createFirstGoal')}
          </button>
        </div>
      ) : (
        <div className="goal-cards-grid">
          {goals.map((g) => {
            const pct = Math.min(Math.max(Number(g.progressPercentage) || 0, 0), 100)
            const isCompleted = pct >= 100

            // Circular progress ring parameters
            const r = 32
            const strokeW = 6
            const circ = 2 * Math.PI * r // ~201
            const strokeDashoffset = circ - (pct / 100) * circ

            return (
              <div key={g.id} className="goal-card">
                {/* Header: Name and Status */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                  <div>
                    <h4 style={{ fontSize: '15px', fontWeight: '700', color: 'var(--text-primary)', margin: 0 }}>
                      {g.name}
                    </h4>
                    <span style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px', display: 'block' }}>
                      {t('goals.targetLabel', { amount: formatCurrency(g.targetAmount, currency, 2, i18n.language) })}
                    </span>
                  </div>

                  <span className={isCompleted ? 'chip chip-pos' : 'chip chip-info'} style={{ fontSize: '10.5px' }}>
                    {isCompleted ? t('goals.achieved') : t('goals.inProgress')}
                  </span>
                </div>

                {/* Progress Visual: Circular Ring + Percentage in Large Text */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', margin: '4px 0' }}>
                  <div style={{ position: 'relative', width: '76px', height: '76px', flexShrink: 0 }}>
                    <svg viewBox="0 0 76 76" style={{ width: '100%', height: '100%', transform: 'rotate(-90deg)' }}>
                      {/* Background Track */}
                      <circle
                        cx="38"
                        cy="38"
                        r={r}
                        fill="transparent"
                        stroke="var(--bg-primary)"
                        strokeWidth={strokeW}
                      />
                      {/* Progress Arc */}
                      <circle
                        cx="38"
                        cy="38"
                        r={r}
                        fill="transparent"
                        stroke={isCompleted ? 'var(--color-positive)' : 'var(--accent-primary)'}
                        strokeWidth={strokeW}
                        strokeDasharray={circ}
                        strokeDashoffset={strokeDashoffset}
                        strokeLinecap="round"
                        style={{ transition: 'stroke-dashoffset 0.5s ease' }}
                      />
                    </svg>

                    <div
                      style={{
                        position: 'absolute',
                        inset: 0,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '11px',
                        fontWeight: '700',
                        color: 'var(--text-secondary)'
                      }}
                    >
                      {pct >= 100 ? <CheckCircle2 size={18} style={{ color: 'var(--color-positive)' }} /> : `${Math.round(pct)}%`}
                    </div>
                  </div>

                  {/* Percentage in Large Text + Current Amount */}
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <div className="goal-pct-large">
                      {pct}%
                    </div>
                    <div style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-primary)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' }}>
                      {t('goals.savedAmount', { amount: formatCurrency(g.currentAmount, currency, 2, i18n.language) })}
                    </div>
                  </div>
                </div>

                {/* Remaining Amount & Estimated Completion Date */}
                <div
                  style={{
                    borderTop: '1px solid var(--border-subtle)',
                    paddingTop: '12px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '6px',
                    fontSize: '12px'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-secondary)' }}>
                    <span>{t('goals.remainingToGoal')}</span>
                    <strong style={{ color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
                      {formatCurrency(g.remainingAmount, currency, 2, i18n.language)}
                    </strong>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', color: 'var(--text-muted)' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <Calendar size={13} /> {t('goals.targetCompletion', { date: '' })}
                    </span>
                    <span style={{ color: 'var(--accent-light)', fontWeight: '600' }}>
                      {g.estimatedCompletionMessage
                        ? renderLocalizedMessage(g.estimatedCompletionMessage, t, i18n.language, g.estimatedCompletionDate)
                        : (g.estimatedCompletionDate || t('goals.pacingCalculation'))}
                    </span>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default SavingsGoalPanel
