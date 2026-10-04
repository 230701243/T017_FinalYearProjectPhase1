import React, { useState } from 'react'
import { PieChart } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, getCategoryDisplayName } from '../utils/currency'

const CATEGORY_PALETTE = [
  '#4F8CFF', // Brand Accent Blue
  '#22C55E', // Semantic Green
  '#F59E0B', // Amber
  '#A855F7', // Purple
  '#06B6D4', // Cyan
  '#EC4899', // Pink
  '#E11D48', // Crimson
  '#10B981', // Emerald
  '#6366F1', // Indigo
  '#F97316'  // Orange
]

function CategoryChart({ breakdown = [], totalExpenses = 0 }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const [hoveredCategory, setHoveredCategory] = useState(null)

  if (!breakdown || breakdown.length === 0) {
    return (
      <div className="chart-card">
        <div className="chart-header">
          <h3 className="chart-title">
            <PieChart size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('charts.categoryTitle')}
          </h3>
        </div>
        <p style={{ color: 'var(--text-muted)', fontSize: '13px', textAlign: 'center', padding: '30px 0' }}>
          {t('charts.noExpenseCategory')}
        </p>
      </div>
    )
  }

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <PieChart size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('charts.categoryTitle')}
          </h3>
          <span className="chart-subtitle">{t('charts.categorySubtitle', { count: breakdown.length })}</span>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            {t('charts.monthlyExpenseTotal')}
          </div>
          <div style={{ fontSize: '16px', fontWeight: '700', color: 'var(--color-negative)', fontVariantNumeric: 'tabular-nums' }}>
            {formatCurrency(totalExpenses, currency)}
          </div>
        </div>
      </div>

      {/* Stacked Composition Bar with Rounded Ends */}
      <div style={{ marginBottom: '22px' }}>
        <div style={{
          display: 'flex',
          height: '14px',
          width: '100%',
          borderRadius: '9999px',
          overflow: 'hidden',
          background: 'var(--surface-raised)',
          border: '1px solid var(--border-subtle)',
          padding: '2px',
          gap: '2px'
        }}>
          {breakdown.map((item, idx) => {
            const color = CATEGORY_PALETTE[idx % CATEGORY_PALETTE.length]
            const isHovered = hoveredCategory === item.category
            return (
              <div
                key={item.category}
                onMouseEnter={() => setHoveredCategory(item.category)}
                onMouseLeave={() => setHoveredCategory(null)}
                style={{
                  width: `${Math.max(item.percentage, 2)}%`,
                  background: color,
                  borderRadius: '9999px',
                  opacity: hoveredCategory && !isHovered ? 0.35 : 1,
                  transform: isHovered ? 'scaleY(1.2)' : 'none',
                  transition: 'all 150ms ease',
                  cursor: 'pointer'
                }}
                title={`${item.category}: ${formatCurrency(item.amount, currency)} (${item.percentage}%)`}
              />
            )
          })}
        </div>
      </div>

      {/* Category Rows with Rounded Individual Progress Bars & Hover Tooltip */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {breakdown.map((item, idx) => {
          const color = CATEGORY_PALETTE[idx % CATEGORY_PALETTE.length]
          const isHovered = hoveredCategory === item.category

          return (
            <div
              key={item.category}
              onMouseEnter={() => setHoveredCategory(item.category)}
              onMouseLeave={() => setHoveredCategory(null)}
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
                padding: '8px 12px',
                borderRadius: '10px',
                background: isHovered ? 'var(--surface-raised)' : 'transparent',
                border: isHovered ? '1px solid var(--border-subtle)' : '1px solid transparent',
                transition: 'all 150ms ease',
                position: 'relative'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '13px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: color, display: 'inline-block' }} />
                  <span style={{ fontWeight: '600', color: 'var(--text-primary)' }}>{getCategoryDisplayName(item.category, t)}</span>
                </div>
                <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                  <span style={{ fontWeight: '600', color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
                    {formatCurrency(item.amount, currency)}
                  </span>
                  <span style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-muted)', minWidth: '44px', textAlign: 'right' }}>
                    {item.percentage}%
                  </span>
                </div>
              </div>

              {/* Progress Bar Track with Rounded Bar */}
              <div style={{ width: '100%', height: '8px', background: 'var(--surface-raised)', borderRadius: '9999px', overflow: 'hidden' }}>
                <div
                  style={{
                    height: '100%',
                    width: `${Math.min(Math.max(item.percentage, 2), 100)}%`,
                    background: color,
                    borderRadius: '9999px',
                    transition: 'width 200ms ease'
                  }}
                />
              </div>

              {/* Floating Tooltip in User Currency on Hover */}
              {isHovered && (
                <div style={{
                  fontSize: '11.5px',
                  color: 'var(--text-secondary)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  marginTop: '2px'
                }}>
                  <span>{t('charts.monthlySpend', { amount: formatCurrency(item.amount, currency) })}</span>
                  <span>{t('charts.contribution', { percent: item.percentage })}</span>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {/* Chart Legend in User's Currency */}
      <div style={{
        marginTop: '20px',
        paddingTop: '16px',
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex',
        flexWrap: 'wrap',
        gap: '12px 18px',
        fontSize: '12px'
      }}>
        {breakdown.map((item, idx) => (
          <div
            key={item.category}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              color: 'var(--text-secondary)',
              cursor: 'pointer'
            }}
            onMouseEnter={() => setHoveredCategory(item.category)}
            onMouseLeave={() => setHoveredCategory(null)}
          >
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: CATEGORY_PALETTE[idx % CATEGORY_PALETTE.length] }} />
            <span>{getCategoryDisplayName(item.category, t)}:</span>
            <strong style={{ color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(item.amount, currency)}
            </strong>
          </div>
        ))}
      </div>
    </div>
  )
}

export default CategoryChart
