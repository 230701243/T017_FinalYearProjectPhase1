import React from 'react'
import { TrendingUp, ArrowUpRight, ArrowDownRight, Minus, Repeat, AlertCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, renderLocalizedMessage, getCategoryDisplayName } from '../utils/currency'

function ForecastPanel({ forecastData, loading, onOpenSubscriptions }) {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'

  if (loading) {
    return (
      <div className="chart-card skeleton-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="skeleton skeleton-title" />
          <div className="skeleton" style={{ width: '130px', height: '32px', borderRadius: '8px' }} />
        </div>
        <div className="skeleton" style={{ height: '140px', borderRadius: '12px' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div className="skeleton" style={{ height: '42px', borderRadius: '6px' }} />
          <div className="skeleton" style={{ height: '42px', borderRadius: '6px' }} />
          <div className="skeleton" style={{ height: '42px', borderRadius: '6px' }} />
        </div>
      </div>
    )
  }

  if (!forecastData || !forecastData.categoryForecasts || forecastData.categoryForecasts.length === 0) {
    return (
      <div className="chart-card">
        <div className="chart-header">
          <div>
            <h3 className="chart-title">
              <TrendingUp size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
              {t('forecast.defaultTitle')}
            </h3>
            <span className="chart-subtitle">{t('forecast.subtitle')}</span>
          </div>

          <button
            className="btn-secondary"
            onClick={onOpenSubscriptions}
            style={{ fontSize: '13px', padding: '8px 16px' }}
          >
            <Repeat size={14} /> {t('topbar.subscriptionsAndEmis')}
          </button>
        </div>

        <div className="empty-state">
          <div className="empty-state-icon">
            <TrendingUp size={24} />
          </div>
          <div className="empty-state-title">{t('forecast.noDataTitle')}</div>
          <p className="empty-state-desc">
            {t('forecast.noDataDesc')}
          </p>
          <button
            className="btn-secondary"
            onClick={onOpenSubscriptions}
            style={{ marginTop: '6px' }}
          >
            <Repeat size={14} /> {t('forecast.manageSubscriptions')}
          </button>
        </div>

        {disclaimerText && (
          <div className="alert alert-error" style={{ marginTop: '16px' }}>
            <AlertCircle size={15} />
            <span><strong>{t('forecast.notice')}</strong> {disclaimerText}</span>
          </div>
        )}
      </div>
    )
  }

  const {
    nextMonthName,
    variableProjectedExpense = 0,
    recurringCommitmentsTotal = 0,
    totalProjectedExpense = 0,
    projectedNetCashFlow = 0,
    categoryForecasts = []
  } = forecastData

  const disclaimerText = forecastData?.disclaimerMessage
    ? renderLocalizedMessage(forecastData.disclaimerMessage, t, i18n.language, forecastData.disclaimer)
    : forecastData?.disclaimer

  const isNetPositive = Number(projectedNetCashFlow) >= 0

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <TrendingUp size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('forecast.title', { month: nextMonthName })}
          </h3>
          <span className="chart-subtitle">
            {t('forecast.subtitle')}
          </span>
        </div>

        <button
          className="btn-secondary"
          onClick={onOpenSubscriptions}
          style={{ fontSize: '13px', padding: '8px 16px' }}
        >
          <Repeat size={14} /> {t('forecast.subscriptionsBtn', { amount: formatCurrency(recurringCommitmentsTotal, currency, 2, i18n.language) })}
        </button>
      </div>

      {/* ── Dominant Projected Net Flow Hero Banner ── */}
      <div
        style={{
          margin: '0 0 20px',
          padding: '24px',
          background: 'var(--surface-raised)',
          borderRadius: 'var(--radius-lg)',
          border: '1px solid var(--border-subtle)',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
          <div>
            <div style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {t('forecast.projectedNetFlowHeader', { month: nextMonthName })}
            </div>
            {/* Dominant Figure */}
            <div
              style={{
                fontSize: '34px',
                fontWeight: '800',
                color: isNetPositive ? 'var(--color-positive)' : 'var(--color-negative)',
                letterSpacing: '-0.025em',
                lineHeight: '1.15',
                marginTop: '4px',
                fontVariantNumeric: 'tabular-nums'
              }}
            >
              {isNetPositive ? `+${formatCurrency(projectedNetCashFlow, currency, 2, i18n.language)}` : formatCurrency(projectedNetCashFlow, currency, 2, i18n.language)}
            </div>
            <div style={{ fontSize: '12.5px', color: 'var(--text-muted)', marginTop: '4px' }}>
              {isNetPositive ? t('forecast.anticipatedSurplus') : t('forecast.anticipatedDeficit')}
            </div>
          </div>

          <div className={isNetPositive ? 'chip chip-pos' : 'chip chip-neg'} style={{ padding: '6px 12px', fontSize: '12px' }}>
            {isNetPositive ? <ArrowUpRight size={14} /> : <ArrowDownRight size={14} />}
            {isNetPositive ? t('forecast.surplusProjected') : t('forecast.deficitProjected')}
          </div>
        </div>

        {/* Subordinate Metric Hierarchy Row */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: '12px',
            paddingTop: '16px',
            borderTop: '1px solid var(--border-subtle)'
          }}
        >
          <div>
            <span style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{t('forecast.variableSpendingTrend')}</span>
            <div style={{ fontSize: '16px', fontWeight: '700', color: 'var(--text-primary)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(variableProjectedExpense, currency, 2, i18n.language)}
            </div>
          </div>

          <div>
            <span style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{t('forecast.fixedSubscriptions')}</span>
            <div style={{ fontSize: '16px', fontWeight: '700', color: 'var(--text-primary)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(recurringCommitmentsTotal, currency, 2, i18n.language)}
            </div>
          </div>

          <div>
            <span style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{t('forecast.totalForecastOutflow')}</span>
            <div style={{ fontSize: '16px', fontWeight: '700', color: 'var(--color-negative)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(totalProjectedExpense, currency, 2, i18n.language)}
            </div>
          </div>
        </div>
      </div>

      {/* ── Category Trend Rows ── */}
      <div>
        <div style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '10px' }}>
          {t('forecast.categoryProjectionsHeader')}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {categoryForecasts.map((item) => {
            const isUp = item.trendText === 'Trending Up'
            const isDown = item.trendText === 'Trending Down'
            const categoryDisplayName = getCategoryDisplayName(item.category, t)
            const trendLabel = item.trendMessage
              ? renderLocalizedMessage(item.trendMessage, t, i18n.language, item.trendText)
              : item.trendText

            return (
              <div
                key={item.category}
                className="trend-list-row"
              >
                {/* Category Name with small trend arrow */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0 }}>
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      color: isUp ? 'var(--color-negative)' : isDown ? 'var(--color-positive)' : 'var(--text-muted)',
                      flexShrink: 0
                    }}
                    title={trendLabel}
                  >
                    {isUp ? (
                      <ArrowUpRight size={16} strokeWidth={2.5} />
                    ) : isDown ? (
                      <ArrowDownRight size={16} strokeWidth={2.5} />
                    ) : (
                      <Minus size={16} strokeWidth={2} />
                    )}
                  </span>

                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: '600', fontSize: '13.5px', color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {categoryDisplayName}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                      {t('forecast.slope')} <code style={{ color: 'var(--text-secondary)' }}>m = {item.slope > 0 ? `+${item.slope}` : item.slope}</code>
                    </div>
                  </div>
                </div>

                {/* Projected amount */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0 }}>
                  <span style={{ fontWeight: '700', fontSize: '14.5px', color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
                    {formatCurrency(item.projectedAmount, currency, 2, i18n.language)}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Disclaimer Notice */}
      {disclaimerText && (
        <div style={{ marginTop: '16px', padding: '10px 14px', background: 'var(--surface-raised)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', color: 'var(--text-muted)', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertCircle size={15} style={{ flexShrink: 0 }} />
          <span><strong>{t('forecast.notice')}</strong> {disclaimerText}</span>
        </div>
      )}
    </div>
  )
}

export default ForecastPanel
