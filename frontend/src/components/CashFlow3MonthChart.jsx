import React, { useState } from 'react'
import { Calendar, ArrowUpRight, ArrowDownRight, Layers, TrendingUp, Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, getCurrencySymbol } from '../utils/currency'

function CashFlow3MonthChart({ cashFlowData, loading, onOpenAddTransaction }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const symbol = getCurrencySymbol(currency)
  const [hoveredPoint, setHoveredPoint] = useState(null)

  if (loading) {
    return (
      <div className="chart-card skeleton-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="skeleton skeleton-title" />
          <div className="skeleton" style={{ width: '120px', height: '24px' }} />
        </div>
        <div className="skeleton" style={{ width: '100%', height: '240px', borderRadius: '12px' }} />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px' }}>
          <div className="skeleton" style={{ height: '90px', borderRadius: '10px' }} />
          <div className="skeleton" style={{ height: '90px', borderRadius: '10px' }} />
          <div className="skeleton" style={{ height: '90px', borderRadius: '10px' }} />
        </div>
      </div>
    )
  }

  if (!cashFlowData || !cashFlowData.projections || cashFlowData.projections.length === 0) {
    return (
      <div className="chart-card">
        <div className="chart-header">
          <h3 className="chart-title">
            <Layers size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('charts.cashFlowTitle')}
          </h3>
        </div>
        <div className="empty-state">
          <div className="empty-state-icon">
            <TrendingUp size={24} />
          </div>
          <div className="empty-state-title">{t('charts.noCashFlowDataTitle')}</div>
          <p className="empty-state-desc">
            {t('charts.noCashFlowDataDesc')}
          </p>
          {onOpenAddTransaction && (
            <button className="btn-primary" onClick={onOpenAddTransaction} style={{ marginTop: '8px' }}>
              <Plus size={16} /> {t('charts.addFirstTransaction')}
            </button>
          )}
        </div>
      </div>
    )
  }

  const { startingBalance, projections, recurringBills } = cashFlowData

  // 1. Calculate Minimum Emergency Reserve Threshold (3 Months Outflow Benchmark)
  const firstMonthExpenses = projections[0]?.forecastedExpenses || 0
  const monthlyOutflow = Number(recurringBills || 0) + Number(firstMonthExpenses)
  const reserveThreshold = monthlyOutflow * 3

  // 2. Prepare Points for Line Chart
  const points = [
    { label: 'Current', val: Number(startingBalance) || 0, isCurrent: true },
    ...projections.map(p => ({
      label: p.monthName,
      val: Number(p.endBalance) || 0,
      netFlow: p.netCashFlow,
      income: p.expectedIncome,
      expenses: p.forecastedExpenses,
      bills: p.recurringBills
    }))
  ]

  const vals = points.map(p => p.val).concat([reserveThreshold])
  const minVal = Math.min(0, ...vals)
  const maxVal = Math.max(...vals)
  const yMin = minVal < 0 ? minVal * 1.15 : 0
  const yMax = maxVal > 0 ? maxVal * 1.25 : 1000
  const yRange = yMax - yMin

  const svgW = 750
  const svgH = 260
  const pLeft = 80
  const pRight = 710
  const pTop = 45
  const pBottom = 215
  const pWidth = pRight - pLeft
  const pHeight = pBottom - pTop

  const coords = points.map((p, i) => {
    const x = pLeft + (i * (pWidth / (points.length - 1)))
    const y = pBottom - ((p.val - yMin) / yRange) * pHeight
    return { ...p, x, y, index: i }
  })

  const reserveY = pBottom - ((reserveThreshold - yMin) / yRange) * pHeight

  // Smooth cubic Bezier spline path generator
  const createSmoothAreaPath = (pts) => {
    if (pts.length < 2) return ''
    let d = `M ${pts[0].x} ${pts[0].y}`
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i]
      const p1 = pts[i + 1]
      const cx1 = p0.x + (p1.x - p0.x) / 2
      const cy1 = p0.y
      const cx2 = p0.x + (p1.x - p0.x) / 2
      const cy2 = p1.y
      d += ` C ${cx1} ${cy1}, ${cx2} ${cy2}, ${p1.x} ${p1.y}`
    }
    return d
  }

  const curveLine = createSmoothAreaPath(coords)
  const areaPath = `${curveLine} L ${coords[coords.length - 1].x} ${pBottom} L ${coords[0].x} ${pBottom} Z`

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <TrendingUp size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('charts.cashFlowTitle')}
          </h3>
          <span className="chart-subtitle">
            {t('charts.cashFlowSubtitle')}
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          {/* Amber Reserve Indicator Chip */}
          <div className="chip chip-warn" style={{ fontVariantNumeric: 'tabular-nums' }}>
            {t('charts.minReserve', { amount: formatCurrency(reserveThreshold, currency) })}
          </div>

          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {t('charts.endingTarget')}
            </div>
            <div style={{ fontSize: '18px', fontWeight: '700', color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(projections[projections.length - 1].endBalance, currency)}
            </div>
          </div>
        </div>
      </div>

      {/* ── Cash-Flow Projection Smooth Area Chart ── */}
      <div style={{ position: 'relative', margin: '16px 0', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', padding: '16px 12px 12px 12px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
        <svg viewBox={`0 0 ${svgW} ${svgH}`} style={{ width: '100%', height: 'auto', display: 'block', overflow: 'hidden' }}>
          <defs>
            <linearGradient id="cashFlowAreaGrad" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stopColor="var(--accent-primary)" stopOpacity="0.32" />
              <stop offset="100%" stopColor="var(--accent-primary)" stopOpacity="0.0" />
            </linearGradient>
            <filter id="pointGlow" x="-30%" y="-30%" width="160%" height="160%">
              <feDropShadow dx="0" dy="0" stdDeviation="3" floodColor="var(--accent-primary)" floodOpacity="0.6" />
            </filter>
          </defs>

          {/* Legend */}
          <line x1={pRight - 280} y1="18" x2={pRight - 260} y2="18" stroke="var(--accent-primary)" strokeWidth="3" />
          <circle cx={pRight - 270} cy="18" r="3" fill="var(--accent-primary)" stroke="#0B1220" strokeWidth="1.5" />
          <text x={pRight - 252} y="22" fill="var(--text-secondary)" fontSize="11" fontWeight="600">{t('charts.projectedBalance')}</text>

          <line x1={pRight - 140} y1="18" x2={pRight - 115} y2="18" stroke="var(--color-warning)" strokeWidth="2" strokeDasharray="5,3" />
          <text x={pRight - 108} y="22" fill="var(--color-warning)" fontSize="11" fontWeight="600">{t('charts.minReserve', { amount: '' }).replace(':', '').trim()}</text>

          {/* Y-axis Label */}
          <text x="22" y={pTop + pHeight / 2} fill="var(--text-muted)" fontSize="11" fontWeight="600" textAnchor="middle" transform={`rotate(-90 22 ${pTop + pHeight / 2})`}>
            {t('charts.balanceAxis', { symbol })}
          </text>

          {/* Subtle Gridlines */}
          {[0, 1, 2, 3, 4].map(idx => {
            const tickVal = yMin + (yRange * idx) / 4
            const tickY = pBottom - ((tickVal - yMin) / yRange) * pHeight
            return (
              <g key={idx}>
                <line x1={pLeft} y1={tickY} x2={pRight} y2={tickY} stroke="rgba(255,255,255,0.04)" strokeWidth="1" />
                <text x={pLeft - 8} y={tickY + 4} fill="var(--text-muted)" fontSize="10.5" textAnchor="end" style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {symbol}{Math.round(tickVal).toLocaleString()}
                </text>
              </g>
            )
          })}

          {/* Dashed Amber Minimum Reserve Reference Line */}
          {reserveY >= pTop && reserveY <= pBottom && (
            <g>
              <line
                x1={pLeft}
                y1={reserveY}
                x2={pRight}
                y2={reserveY}
                stroke="var(--color-warning)"
                strokeWidth="1.75"
                strokeDasharray="6,4"
              />
              <rect
                x={pRight - 128}
                y={reserveY - 18}
                width="128"
                height="16"
                rx="4"
                fill="var(--bg-tertiary)"
                stroke="var(--color-warning-border)"
                strokeWidth="1"
              />
              <text
                x={pRight - 6}
                y={reserveY - 6}
                fill="var(--color-warning)"
                fontSize="9.5"
                fontWeight="700"
                textAnchor="end"
                letterSpacing="0.02em"
              >
                {t('charts.minReserve', { amount: formatCurrency(reserveThreshold, currency, 0) })}
              </text>
            </g>
          )}

          {/* Smooth Area Gradient Fill */}
          <path
            d={areaPath}
            fill="url(#cashFlowAreaGrad)"
          />

          {/* Smooth Curve Stroke */}
          <path
            d={curveLine}
            fill="none"
            stroke="var(--accent-primary)"
            strokeWidth="3.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Rounded Data Points with tooltips */}
          {coords.map((c, i) => {
            const isHovered = hoveredPoint?.index === i
            return (
              <g
                key={i}
                style={{ cursor: 'pointer' }}
                onMouseEnter={() => setHoveredPoint(c)}
                onMouseLeave={() => setHoveredPoint(null)}
              >
                {/* Invisible hover capture target */}
                <circle cx={c.x} cy={c.y} r="16" fill="transparent" />

                {/* Outer halo on hover */}
                {isHovered && (
                  <circle
                    cx={c.x}
                    cy={c.y}
                    r="9"
                    fill="var(--accent-subtle)"
                    stroke="var(--accent-primary)"
                    strokeWidth="1.5"
                  />
                )}

                {/* Solid rounded point */}
                <circle
                  cx={c.x}
                  cy={c.y}
                  r={isHovered ? 6 : 5}
                  fill="var(--accent-primary)"
                  stroke="var(--bg-primary)"
                  strokeWidth="2.5"
                  filter={isHovered ? 'url(#pointGlow)' : undefined}
                />

                {/* Primary Data Label above point */}
                <text
                  x={c.x}
                  y={c.y - 12}
                  fill="var(--text-primary)"
                  fontSize="11"
                  fontWeight="700"
                  textAnchor="middle"
                  style={{ fontVariantNumeric: 'tabular-nums' }}
                >
                  {formatCurrency(c.val, currency, 0)}
                </text>

                {/* X-axis Label below axis */}
                <text
                  x={c.x}
                  y={pBottom + 20}
                  fill="var(--text-secondary)"
                  fontSize="11.5"
                  fontWeight="600"
                  textAnchor="middle"
                >
                  {c.label}
                </text>
              </g>
            )
          })}
        </svg>

        {/* Floating Interactive Tooltip using User's Currency */}
        {hoveredPoint && (
          <div
            style={{
              position: 'absolute',
              top: '12px',
              left: `${Math.min(Math.max((hoveredPoint.x / svgW) * 100, 15), 85)}%`,
              transform: 'translateX(-50%)',
              background: 'var(--surface-raised)',
              border: '1px solid var(--accent-border)',
              borderRadius: '8px',
              padding: '8px 12px',
              boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
              zIndex: 10,
              pointerEvents: 'none',
              minWidth: '180px',
              textAlign: 'center'
            }}
          >
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: '600' }}>
              {t('charts.projection', { month: hoveredPoint.label })}
            </div>
            <div style={{ fontSize: '15px', fontWeight: '800', color: 'var(--text-primary)', margin: '2px 0', fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(hoveredPoint.val, currency)}
            </div>
            <div style={{ fontSize: '11px', color: hoveredPoint.val >= reserveThreshold ? 'var(--color-positive)' : 'var(--color-warning)' }}>
              {hoveredPoint.val >= reserveThreshold
                ? t('charts.aboveReserve', { amount: formatCurrency(hoveredPoint.val - reserveThreshold, currency) })
                : t('charts.belowReserve', { amount: formatCurrency(reserveThreshold - hoveredPoint.val, currency) })}
            </div>
          </div>
        )}
      </div>

      {/* Milestone Cards Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginTop: '16px' }}>
        {projections.map((p, idx) => {
          const netPositive = Number(p.netCashFlow) >= 0

          return (
            <div
              key={p.monthName}
              style={{
                background: 'var(--surface-raised)',
                borderRadius: 'var(--radius-lg)',
                border: idx === 0 ? '1px solid var(--accent-border)' : '1px solid var(--border-subtle)',
                padding: '16px',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '13px', fontWeight: '700', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Calendar size={14} style={{ color: 'var(--text-muted)' }} />
                  {p.monthName}
                </span>
                <span className={netPositive ? 'chip chip-pos' : 'chip chip-neg'} style={{ fontSize: '10.5px' }}>
                  {netPositive ? (
                    <><ArrowUpRight size={12} /> {t('charts.surplus')}</>
                  ) : (
                    <><ArrowDownRight size={12} /> {t('charts.deficit')}</>
                  )}
                </span>
              </div>

              {/* Breakdown */}
              <div style={{ fontSize: '12px', display: 'flex', flexDirection: 'column', gap: '6px', background: 'var(--bg-primary)', padding: '10px 12px', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-muted)' }}>{t('charts.start')}</span>
                  <span style={{ fontWeight: '500', fontVariantNumeric: 'tabular-nums' }}>{formatCurrency(p.startBalance, currency)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>{t('charts.income')}</span>
                  <span style={{ fontWeight: '600', color: 'var(--color-positive)', fontVariantNumeric: 'tabular-nums' }}>+{formatCurrency(p.expectedIncome, currency)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>{t('charts.expenses')}</span>
                  <span style={{ fontWeight: '600', color: 'var(--color-negative)', fontVariantNumeric: 'tabular-nums' }}>-{formatCurrency(p.forecastedExpenses, currency)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>{t('charts.recurring')}</span>
                  <span style={{ fontWeight: '600', color: 'var(--color-negative)', fontVariantNumeric: 'tabular-nums' }}>-{formatCurrency(p.recurringBills, currency)}</span>
                </div>
              </div>

              {/* Net Cash Flow & End Balance */}
              <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('charts.netFlow')}</div>
                  <div style={{ fontSize: '13px', fontWeight: '700', color: netPositive ? 'var(--color-positive)' : 'var(--color-negative)', fontVariantNumeric: 'tabular-nums' }}>
                    {netPositive ? `+${formatCurrency(p.netCashFlow, currency)}` : formatCurrency(p.netCashFlow, currency)}
                  </div>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t('charts.ending')}</div>
                  <div style={{ fontSize: '14px', fontWeight: '700', color: Number(p.endBalance) >= 0 ? 'var(--text-primary)' : 'var(--color-negative)', fontVariantNumeric: 'tabular-nums' }}>
                    {formatCurrency(p.endBalance, currency)}
                  </div>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default CashFlow3MonthChart
