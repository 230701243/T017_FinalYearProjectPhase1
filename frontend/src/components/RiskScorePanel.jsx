import React from 'react'
import { ShieldCheck, ShieldAlert, AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { renderLocalizedMessage } from '../utils/currency'

function RiskScorePanel({ riskData, loading }) {
  const { t, i18n } = useTranslation()

  if (loading) {
    return (
      <div className="chart-card skeleton-card">
        <div className="skeleton skeleton-title" />
        <div style={{ display: 'flex', justifyContent: 'center', margin: '20px 0' }}>
          <div className="skeleton" style={{ width: '200px', height: '110px', borderRadius: '100px 100px 0 0' }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div className="skeleton" style={{ height: '40px', borderRadius: '6px' }} />
          <div className="skeleton" style={{ height: '40px', borderRadius: '6px' }} />
          <div className="skeleton" style={{ height: '40px', borderRadius: '6px' }} />
        </div>
      </div>
    )
  }

  if (!riskData) {
    return (
      <div className="chart-card">
        <div className="chart-header">
          <h3 className="chart-title">
            <ShieldCheck size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('risk.assessmentTitle')}
          </h3>
        </div>
        <div className="empty-state">
          <div className="empty-state-icon">
            <ShieldCheck size={24} />
          </div>
          <div className="empty-state-title">{t('risk.noMetricsTitle')}</div>
          <p className="empty-state-desc">
            {t('risk.noMetricsDesc')}
          </p>
        </div>
      </div>
    )
  }

  const { totalRiskScore = 0, riskLevel = 'LOW', contributingFactors = [] } = riskData

  let chipClass = 'chip chip-pos'
  let StatusIcon = ShieldCheck
  let scoreColor = 'var(--color-positive)'
  let riskText = t('risk.lowRisk')

  if (riskLevel === 'MEDIUM') {
    chipClass = 'chip chip-warn'
    StatusIcon = AlertTriangle
    scoreColor = 'var(--color-warning)'
    riskText = t('risk.mediumRisk')
  } else if (riskLevel === 'HIGH') {
    chipClass = 'chip chip-neg'
    StatusIcon = ShieldAlert
    scoreColor = 'var(--color-negative)'
    riskText = t('risk.highRisk')
  }

  // Semicircle calculations
  // Gauge radius = 80, center = (120, 110)
  const cx = 120
  const cy = 110
  const r = 80
  const strokeW = 12
  const arcLen = Math.PI * r // ~251.3

  // Angle in radians: score 0 -> PI (180deg, left), score 100 -> 0 (0deg, right)
  const clampedScore = Math.min(Math.max(totalRiskScore, 0), 100)
  const angleRad = Math.PI - (clampedScore / 100) * Math.PI

  // Needle end point
  const needleLength = r - 12
  const needleX = cx + needleLength * Math.cos(angleRad)
  const needleY = cy - needleLength * Math.sin(angleRad)

  // Segment stroke-dasharray values for 3 sections (Green: 0-33, Amber: 34-66, Red: 67-100)
  const thirdLen = arcLen / 3

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <ShieldCheck size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('risk.title')}
          </h3>
          <span className="chart-subtitle">{t('risk.subtitle')}</span>
        </div>

        <div className={chipClass} style={{ padding: '6px 14px', fontSize: '12px' }}>
          <StatusIcon size={14} strokeWidth={2} /> {riskText}
        </div>
      </div>

      {/* Semicircle Gauge Visual */}
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '10px 0 16px' }}>
        <div style={{ position: 'relative', width: '240px', height: '135px' }}>
          <svg viewBox="0 0 240 135" style={{ width: '100%', height: '100%', overflow: 'visible' }}>
            {/* Background Arc Track */}
            <path
              d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
              fill="none"
              stroke="var(--surface-raised)"
              strokeWidth={strokeW}
              strokeLinecap="round"
            />

            {/* Segment 1: Green (Safe, 0-33) */}
            <path
              d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
              fill="none"
              stroke="var(--color-positive)"
              strokeWidth={strokeW}
              strokeDasharray={`${thirdLen} ${arcLen}`}
              strokeDashoffset="0"
              strokeLinecap="round"
            />

            {/* Segment 2: Amber (Moderate, 34-66) */}
            <path
              d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
              fill="none"
              stroke="var(--color-warning)"
              strokeWidth={strokeW}
              strokeDasharray={`${thirdLen} ${arcLen}`}
              strokeDashoffset={`-${thirdLen}`}
            />

            {/* Segment 3: Red (High Risk, 67-100) */}
            <path
              d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
              fill="none"
              stroke="var(--color-negative)"
              strokeWidth={strokeW}
              strokeDasharray={`${thirdLen} ${arcLen}`}
              strokeDashoffset={`-${thirdLen * 2}`}
              strokeLinecap="round"
            />

            {/* Needle Pivot Base */}
            <circle cx={cx} cy={cy} r="6" fill="var(--text-primary)" />
            <circle cx={cx} cy={cy} r="2.5" fill="var(--bg-primary)" />

            {/* Indicator Needle */}
            <line
              x1={cx}
              y1={cy}
              x2={needleX}
              y2={needleY}
              stroke="var(--text-primary)"
              strokeWidth="3"
              strokeLinecap="round"
            />

            {/* Scale min / max text */}
            <text x={cx - r} y={cy + 18} fill="var(--text-muted)" fontSize="10.5" fontWeight="600" textAnchor="middle">0</text>
            <text x={cx} y={cy - r - 8} fill="var(--text-muted)" fontSize="10.5" fontWeight="600" textAnchor="middle">50</text>
            <text x={cx + r} y={cy + 18} fill="var(--text-muted)" fontSize="10.5" fontWeight="600" textAnchor="middle">100</text>
          </svg>

          {/* Centered Score in Gauge */}
          <div
            style={{
              position: 'absolute',
              bottom: '4px',
              left: '50%',
              transform: 'translateX(-50%)',
              textAlign: 'center'
            }}
          >
            <div style={{ fontSize: '32px', fontWeight: '800', color: scoreColor, lineHeight: '1', fontVariantNumeric: 'tabular-nums' }}>
              {totalRiskScore}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px', fontWeight: '600' }}>
              {t('risk.scoreOutOf100')}
            </div>
          </div>
        </div>
      </div>

      {/* Contributing Factors as a Compact List Underneath */}
      <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
        <div style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '10px' }}>
          {t('risk.factorsHeader')}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {contributingFactors.map((f) => {
            let factorChipClass = 'chip chip-pos'
            let tagText = t('risk.healthyPoints')

            if (f.status === 'MODERATE_RISK') {
              factorChipClass = 'chip chip-warn'
              tagText = t('risk.moderatePoints', { score: f.score })
            } else if (f.status === 'HIGH_RISK') {
              factorChipClass = 'chip chip-neg'
              tagText = t('risk.highRiskPoints', { score: f.score })
            }

            const factorTitle = f.factorMessage
              ? renderLocalizedMessage(f.factorMessage, t, i18n.language, f.factor)
              : (f.factor || '')
            const factorDesc = f.descriptionMessage
              ? renderLocalizedMessage(f.descriptionMessage, t, i18n.language, f.description)
              : (f.description || '')

            return (
              <div
                key={f.factor}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '10px 14px',
                  background: 'var(--surface-raised)',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border-subtle)',
                  gap: '12px',
                  flexWrap: 'wrap'
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', flex: 1, minWidth: '200px' }}>
                  <span style={{ fontWeight: '600', fontSize: '13px', color: 'var(--text-primary)' }}>
                    {factorTitle}
                  </span>
                  <span style={{ fontSize: '12px', color: 'var(--text-secondary)', lineHeight: '1.4' }}>
                    {factorDesc}
                  </span>
                </div>

                <span className={factorChipClass} style={{ fontSize: '11px', flexShrink: 0 }}>
                  {tagText}
                </span>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

export default RiskScorePanel
