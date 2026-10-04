import React, { useState } from 'react'
import { Lightbulb, ShieldCheck, AlertTriangle, ChevronDown, ChevronUp, ExternalLink } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, renderLocalizedMessage, getCategoryDisplayName } from '../utils/currency'

function RecommendationPanel({ recommendationData, loading }) {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const [showAll, setShowAll] = useState(false)

  if (loading) {
    return (
      <div className="chart-card skeleton-card">
        <div className="skeleton skeleton-title" />
        <div className="skeleton" style={{ height: '60px', borderRadius: '12px' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div className="skeleton" style={{ height: '70px', borderRadius: '10px' }} />
          <div className="skeleton" style={{ height: '70px', borderRadius: '10px' }} />
          <div className="skeleton" style={{ height: '70px', borderRadius: '10px' }} />
        </div>
      </div>
    )
  }

  if (!recommendationData) {
    return (
      <div className="chart-card">
        <div className="chart-header">
          <h3 className="chart-title">
            <Lightbulb size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('recommendations.healthTitle')}
          </h3>
        </div>
        <div className="empty-state">
          <div className="empty-state-icon">
            <Lightbulb size={24} />
          </div>
          <div className="empty-state-title">{t('recommendations.noRecsTitle')}</div>
          <p className="empty-state-desc">
            {t('recommendations.noRecsDesc')}
          </p>
        </div>
      </div>
    )
  }

  const {
    statusLevel = 'SAFE',
    primaryRecommendation = '',
    primaryMessage = null,
    reserveThreshold = 0,
    isBelowReserveThreshold = false,
    recommendationsList = []
  } = recommendationData

  let badgeClass = 'chip chip-pos'
  let StatusIcon = ShieldCheck
  let statusText = t('recommendations.safeStatus')

  if (statusLevel === 'WARNING') {
    badgeClass = 'chip chip-warn'
    StatusIcon = AlertTriangle
    statusText = t('recommendations.warningStatus')
  } else if (statusLevel === 'CRITICAL') {
    badgeClass = 'chip chip-neg'
    StatusIcon = AlertTriangle
    statusText = t('recommendations.criticalAlert')
  }

  // Resolve primary message if available
  const rawPrimary = primaryMessage
    ? renderLocalizedMessage(primaryMessage, t, i18n.language, primaryRecommendation)
    : (primaryRecommendation || '')
  const cleanPrimary = rawPrimary.replace(/^[🟢⚠️🔴🚨💡]\s*/u, '')

  const recList = Array.isArray(recommendationsList) ? recommendationsList : []
  const visibleList = showAll ? recList : recList.slice(0, 5)
  const hasMore = recList.length > 5

  const getCategoryChipClass = (cat) => {
    switch ((cat || '').toLowerCase()) {
      case 'insurance':
      case 'health':
        return 'chip chip-info'
      case 'pension':
      case 'retirement':
        return 'chip chip-warn'
      case 'debt':
        return 'chip chip-neg'
      case 'savings':
      default:
        return 'chip chip-pos'
    }
  }

  const getPriorityChipClass = (p) => {
    if (p === 'HIGH') return 'chip chip-neg'
    if (p === 'MEDIUM') return 'chip chip-warn'
    return 'chip chip-neutral'
  }

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <Lightbulb size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('recommendations.title')}
          </h3>
          <span className="chart-subtitle">
            {t('recommendations.subtitle')}
          </span>
        </div>

        <div className={badgeClass}>
          <StatusIcon size={14} strokeWidth={2} />
          {statusText}
        </div>
      </div>

      {/* Primary Highlight Banner */}
      {cleanPrimary && (
        <div
          style={{
            margin: '0 0 14px',
            padding: '14px 16px',
            background: 'var(--surface-raised)',
            borderRadius: '12px',
            border: '1px solid var(--border-subtle)',
            display: 'flex',
            alignItems: 'center',
            gap: '12px'
          }}
        >
          <div className="icon-tile icon-tile-warn" style={{ width: '32px', height: '32px', minWidth: '32px', minHeight: '32px' }}>
            <StatusIcon size={16} strokeWidth={2} />
          </div>
          <div style={{ color: 'var(--text-primary)', fontSize: '13.5px', lineHeight: '1.45', flex: 1 }}>
            {cleanPrimary}
          </div>
        </div>
      )}

      {/* Reserve Benchmark Status Callout */}
      <div
        style={{
          margin: '0 0 16px',
          padding: '10px 14px',
          background: 'var(--surface-raised)',
          borderRadius: '10px',
          border: '1px solid var(--border-subtle)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '8px',
          fontSize: '12.5px'
        }}
      >
        <span style={{ color: 'var(--text-secondary)' }}>
          {t('recommendations.emergencyReserveTarget', { amount: formatCurrency(reserveThreshold, currency, 2, i18n.language) })}
        </span>
        <span className={isBelowReserveThreshold ? 'chip chip-warn' : 'chip chip-pos'}>
          {isBelowReserveThreshold ? t('recommendations.reserveDeficit') : t('recommendations.reserveIntact')}
        </span>
      </div>

      {/* Compact Cards List (Top 5 only with "Show more") */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {visibleList.length === 0 ? (
          <div className="empty-state" style={{ padding: '20px 0' }}>
            <p className="empty-state-desc">
              {t('recommendations.allHealthy')}
            </p>
          </div>
        ) : (
          visibleList.map((rawRec, idx) => {
            const isStr = typeof rawRec === 'string'
            const rawCategory = !isStr && rawRec?.category ? rawRec.category : 'Savings'
            const categoryDisplayName = getCategoryDisplayName(rawCategory, t)

            const title = !isStr && rawRec?.titleMessage
              ? renderLocalizedMessage(rawRec.titleMessage, t, i18n.language, rawRec.title)
              : (!isStr && rawRec?.title ? rawRec.title : t('recommendations.title', 'Financial Recommendation'))

            const detail = !isStr && rawRec?.detailMessage
              ? renderLocalizedMessage(rawRec.detailMessage, t, i18n.language, rawRec.detail)
              : (isStr ? rawRec : (rawRec?.detail || ''))

            const actionableStep = !isStr && rawRec?.actionableStepMessage
              ? renderLocalizedMessage(rawRec.actionableStepMessage, t, i18n.language, rawRec.actionableStep)
              : (isStr ? rawRec : (rawRec?.actionableStep || detail))

            const rawPriority = (!isStr && rawRec?.priority) ? rawRec.priority : 'MEDIUM'
            const priorityLabel = t(`recommendations.priority.${rawPriority.toLowerCase()}`, rawPriority)
            const officialUrl = !isStr ? rawRec?.officialUrl : null

            return (
              <div
                key={idx}
                style={{
                  padding: '12px 14px',
                  background: 'var(--surface-raised)',
                  borderRadius: '10px',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '6px',
                  transition: 'var(--transition)'
                }}
              >
                {/* Header: Category Tag, Priority Chip, Title */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: '0' }}>
                    <span className={getCategoryChipClass(rawCategory)} style={{ fontSize: '10.5px' }}>
                      {categoryDisplayName}
                    </span>
                    <span style={{ fontWeight: '600', fontSize: '13px', color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {title}
                    </span>
                  </div>

                  <span className={getPriorityChipClass(rawPriority)} style={{ fontSize: '10px' }}>
                    {priorityLabel}
                  </span>
                </div>

                {/* One-Line Detail */}
                {detail && (
                  <p
                    style={{
                      margin: 0,
                      fontSize: '12.5px',
                      color: 'var(--text-secondary)',
                      lineHeight: '1.4',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap'
                    }}
                    title={detail}
                  >
                    {detail}
                  </p>
                )}

                {/* Action Line */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '8px', flexWrap: 'wrap', marginTop: '2px' }}>
                  {actionableStep && (
                    <span style={{ fontSize: '12px', color: 'var(--accent-light)', fontWeight: '500' }}>
                      {t('recommendations.actionLabel', { step: actionableStep })}
                    </span>
                  )}

                  {officialUrl && (
                    <a
                      href={officialUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                        fontSize: '11.5px',
                        color: 'var(--text-muted)',
                        textDecoration: 'none'
                      }}
                      onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--accent-light)' }}
                      onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--text-muted)' }}
                    >
                      <span>{t('recommendations.officialSource')}</span>
                      <ExternalLink size={11} />
                    </a>
                  )}
                </div>
              </div>
            )
          })
        )}
      </div>

      {/* "Show more" / "Show less" Toggle Button */}
      {hasMore && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '14px' }}>
          <button
            className="btn-secondary"
            onClick={() => setShowAll(prev => !prev)}
            style={{ fontSize: '12px', padding: '6px 16px' }}
          >
            {showAll ? (
              <>
                <ChevronUp size={14} /> {t('recommendations.showLess')}
              </>
            ) : (
              <>
                <ChevronDown size={14} /> {t('recommendations.showMore', { count: recList.length - 5 })}
              </>
            )}
          </button>
        </div>
      )}
    </div>
  )
}

export default RecommendationPanel
