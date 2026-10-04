import React, { useState } from 'react'
import { TrendingUp, TrendingDown, Minus, Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { formatCurrency } from '../utils/currency'

function HistoricalTrendChart({ history = [], momTrendPercentage = 0, trendDirection = 'STABLE' }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const [hoveredIdx, setHoveredIdx] = useState(null)

  if (!history || history.length === 0) {
    return (
      <div className="chart-card">
        <div className="chart-header">
          <h3 className="chart-title">
            <TrendingUp size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('charts.historicalTitle')}
          </h3>
        </div>
        <p style={{ color: 'var(--text-muted)', fontSize: '13px', textAlign: 'center', padding: '30px 0' }}>
          {t('charts.noHistoricalData')}
        </p>
      </div>
    )
  }

  // Find max value for bar scaling (ensure minimum scale)
  const maxVal = Math.max(...history.map(item => Number(item.totalAmount) || 0), 100)
  const totalHistoricalSum = history.reduce((sum, item) => sum + (Number(item.totalAmount) || 0), 0)
  const avgMonthlySpend = history.length > 0 ? totalHistoricalSum / history.length : 0

  let TrendIcon = Minus
  let badgeClass = 'badge-neutral'
  if (trendDirection === 'DOWN') {
    TrendIcon = TrendingDown
    badgeClass = 'badge-financial-pos' // Spending down is positive for personal finance
  } else if (trendDirection === 'UP') {
    TrendIcon = TrendingUp
    badgeClass = 'badge-financial-neg' // Spending up is negative
  }

  const activeHoveredItem = hoveredIdx !== null ? history[hoveredIdx] : null

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <TrendingUp size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('charts.historicalTitle')}
          </h3>
          <span className="chart-subtitle">{t('charts.historicalSubtitle')}</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div className={badgeClass}>
            <TrendIcon size={13} strokeWidth={2} />
            <span>{momTrendPercentage >= 0 ? `+${momTrendPercentage}%` : `${momTrendPercentage}%`} MoM</span>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {t('charts.sixMonthAverage')}
            </div>
            <div style={{ fontSize: '15px', fontWeight: '700', color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(avgMonthlySpend, currency)}
            </div>
          </div>
        </div>
      </div>

      {/* Bar & Trend Line Visualization Container */}
      <div className="history-chart-container" style={{ position: 'relative' }}>
        {/* Floating Tooltip in User's Currency */}
        {activeHoveredItem && (
          <div style={{
            position: 'absolute',
            top: '-10px',
            left: '50%',
            transform: 'translateX(-50%)',
            background: 'var(--surface-raised)',
            border: '1px solid var(--accent-border)',
            borderRadius: '8px',
            padding: '6px 14px',
            fontSize: '12.5px',
            color: 'var(--text-primary)',
            boxShadow: '0 8px 20px rgba(0,0,0,0.4)',
            zIndex: 15,
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            animation: 'fadeIn 120ms ease'
          }}>
            <span style={{ color: 'var(--text-muted)' }}>{activeHoveredItem.month || 'Month'}:</span>
            <strong style={{ color: 'var(--accent-light)', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(Number(activeHoveredItem.totalAmount) || 0, currency)}
            </strong>
          </div>
        )}

        <div className="history-bars-grid" style={{ height: '200px', alignItems: 'flex-end', paddingTop: '20px' }}>
          {history.map((item, idx) => {
            const amount = Number(item.totalAmount) || 0
            const heightPct = Math.min(Math.max((amount / maxVal) * 100, 5), 100)
            const isCurrentMonth = idx === history.length - 1
            const isHovered = hoveredIdx === idx

            return (
              <div
                key={item.yearMonth || idx}
                className="history-col"
                onMouseEnter={() => setHoveredIdx(idx)}
                onMouseLeave={() => setHoveredIdx(null)}
                style={{ cursor: 'pointer' }}
              >
                <div
                  className="history-col-amount"
                  style={{
                    color: isCurrentMonth ? 'var(--accent-light)' : 'var(--text-secondary)',
                    fontWeight: isCurrentMonth || isHovered ? '700' : '500',
                    transition: 'color 150ms ease'
                  }}
                >
                  {formatCurrency(amount, currency, 0)}
                </div>

                <div
                  className="history-bar-track"
                  style={{
                    height: '130px',
                    borderRadius: '8px 8px 2px 2px',
                    background: 'var(--surface-raised)',
                    border: isHovered ? '1px solid var(--accent-border)' : '1px solid var(--border-subtle)',
                    transition: 'border-color 150ms ease',
                    overflow: 'hidden'
                  }}
                >
                  <div
                    className="history-bar-fill"
                    style={{
                      height: `${heightPct}%`,
                      background: isCurrentMonth
                        ? 'linear-gradient(180deg, var(--accent-primary) 0%, rgba(79, 140, 255, 0.7) 100%)'
                        : isHovered
                        ? 'linear-gradient(180deg, #70A3FF 0%, rgba(79, 140, 255, 0.4) 100%)'
                        : 'linear-gradient(180deg, rgba(79, 140, 255, 0.3) 0%, rgba(79, 140, 255, 0.1) 100%)',
                      borderTop: isCurrentMonth ? '2px solid var(--accent-light)' : '1px solid rgba(79, 140, 255, 0.5)',
                      borderRadius: '8px 8px 0 0',
                      transition: 'all 200ms ease'
                    }}
                  />
                </div>

                <div
                  className="history-col-label"
                  style={{
                    fontWeight: isCurrentMonth || isHovered ? '700' : '500',
                    color: isCurrentMonth ? 'var(--accent-light)' : isHovered ? 'var(--text-primary)' : 'var(--text-muted)',
                    transition: 'color 150ms ease'
                  }}
                >
                  {item.month ? item.month.split(' ')[0] : ''}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Chart Legend in User's Currency */}
      <div style={{
        marginTop: '16px',
        paddingTop: '14px',
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px',
        fontSize: '12px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)' }}>
            <span style={{ width: '10px', height: '10px', borderRadius: '3px', background: 'var(--accent-primary)', display: 'inline-block' }} />
            <span>{t('charts.currentPeriod', { amount: formatCurrency(Number(history[history.length - 1]?.totalAmount || 0), currency) })}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)' }}>
            <span style={{ width: '10px', height: '10px', borderRadius: '3px', background: 'rgba(79, 140, 255, 0.3)', border: '1px solid rgba(79, 140, 255, 0.5)', display: 'inline-block' }} />
            <span>{t('charts.priorMonths')}</span>
          </div>
        </div>

        <div style={{ color: 'var(--text-muted)', fontSize: '11.5px', display: 'flex', alignItems: 'center', gap: '4px' }}>
          <Info size={13} />
          <span>{t('charts.hoverAnyBar', { currency })}</span>
        </div>
      </div>
    </div>
  )
}

export default HistoricalTrendChart
