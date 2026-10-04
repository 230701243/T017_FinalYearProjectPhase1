import React, { useState, useEffect } from 'react'
import { Sliders, AlertTriangle, Sparkles, Check, CheckCircle2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { formatCurrency, getCurrencySymbol, toUsd, convertUsd, renderLocalizedMessage } from '../utils/currency'

function WhatIfSimulatorPanel() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const symbol = getCurrencySymbol(currency)

  const [amount,          setAmount]          = useState('50000.00')
  const [description,     setDescription]     = useState('Workstation Upgrade')
  const [delayMonths,     setDelayMonths]     = useState(3)
  const [emiAmount,       setEmiAmount]       = useState('9500.00')
  const [emiTenureMonths, setEmiTenureMonths] = useState(6)
  const [simResult,       setSimResult]       = useState(null)
  const [loading,         setLoading]         = useState(false)
  const [errorMsg,        setErrorMsg]        = useState('')

  useEffect(() => {
    api.get('/dashboard/what-if/active')
      .then(res => {
        if (res.data && res.data.buyNow) {
          setSimResult(res.data)
        }
      })
      .catch(() => {})
  }, [])

  const handleRunMultiSimulation = async (e) => {
    e.preventDefault()
    if (!amount || Number(amount) <= 0 || !description) {
      setErrorMsg('Please enter a valid purchase amount and description.')
      return
    }

    if (!emiAmount || Number(emiAmount) <= 0) {
      setErrorMsg('Please enter a valid positive monthly EMI amount.')
      return
    }

    setErrorMsg('')
    setLoading(true)

    try {
      const res = await api.post('/dashboard/what-if/multi-scenario', {
        amount: toUsd(parseFloat(amount), currency),
        description: description.trim(),
        delayMonths: parseInt(delayMonths) || 3,
        emiTenureMonths: parseInt(emiTenureMonths) || 6,
        emiMonthlyAmount: toUsd(parseFloat(emiAmount), currency)
      })
      setSimResult(res.data)
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Multi-scenario simulation failed.')
    } finally {
      setLoading(false)
    }
  }

  // Determine which scenario is marked Recommended
  const winningScenario = simResult?.winningScenario || ''
  const isBuyNowRecommended = winningScenario === 'BUY_NOW'
  const isWaitRecommended = winningScenario === 'BUY_IN_N_MONTHS' || winningScenario === 'BUY_LATER'
  const isEmiRecommended = winningScenario === 'BUY_VIA_EMI'

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <Sliders size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('whatIf.title')}
          </h3>
          <span className="chart-subtitle">
            {t('whatIf.subtitle')}
          </span>
        </div>
        <div className="chip chip-neutral" style={{ fontSize: '11px' }}>
          {t('whatIf.zeroDbMutation')}
        </div>
      </div>

      {/* ── Tidy Form Row ── */}
      <form
        onSubmit={handleRunMultiSimulation}
        style={{
          margin: '0 0 20px',
          padding: '16px',
          background: 'var(--surface-raised)',
          borderRadius: 'var(--radius-lg)',
          border: '1px solid var(--border-subtle)'
        }}
      >
        {errorMsg && (
          <div className="alert alert-error" style={{ marginBottom: '14px' }}>
            <AlertTriangle size={15} />
            {errorMsg}
          </div>
        )}

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
            gap: '12px',
            alignItems: 'end'
          }}
        >
          <div>
            <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              {t('whatIf.costLabel', { symbol })}
            </label>
            <input
              type="number"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="e.g. 50000"
              required
              className="input-field"
              style={{ height: '38px', fontSize: '13px' }}
            />
          </div>

          <div style={{ minWidth: 0 }}>
            <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              {t('whatIf.itemDescription')}
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Workstation Upgrade"
              required
              className="input-field"
              style={{ height: '38px', fontSize: '13px' }}
            />
          </div>

          <div>
            <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              {t('whatIf.delayMonthsLabel')}
            </label>
            <input
              type="number"
              min="1"
              max="12"
              value={delayMonths}
              onChange={(e) => setDelayMonths(e.target.value)}
              className="input-field"
              style={{ height: '38px', fontSize: '13px' }}
            />
          </div>

          <div>
            <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              {t('whatIf.emiAmountLabel', { symbol })}
            </label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              value={emiAmount}
              onChange={(e) => setEmiAmount(e.target.value)}
              placeholder="e.g. 9500"
              required
              className="input-field"
              style={{ height: '38px', fontSize: '13px' }}
            />
          </div>

          <div>
            <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              {t('whatIf.emiTenureLabel')}
            </label>
            <input
              type="number"
              min="1"
              max="60"
              step="1"
              value={emiTenureMonths}
              onChange={(e) => setEmiTenureMonths(e.target.value)}
              placeholder="6"
              className="input-field"
              style={{ height: '38px', fontSize: '13px' }}
            />
          </div>

          <button
            type="submit"
            className="btn-primary"
            disabled={loading}
            style={{ height: '38px', padding: '0 18px', fontSize: '13px', whiteSpace: 'nowrap' }}
          >
            {loading ? t('whatIf.running') : t('whatIf.runSimulation')}
          </button>
        </div>
      </form>

      {/* ── Three Scenario Cards Side by Side ── */}
      {simResult ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
          {/* Recommendation Banner */}
          {simResult.recommendationText && (
            <div
              style={{
                padding: '14px 18px',
                borderRadius: 'var(--radius-md)',
                background: 'var(--surface-raised)',
                border: '1px solid var(--accent-border)',
                color: 'var(--text-primary)',
                fontSize: '13px',
                display: 'flex',
                alignItems: 'center',
                gap: '10px'
              }}
            >
              <div className="icon-tile icon-tile-pos" style={{ width: '28px', height: '28px', minWidth: '28px', minHeight: '28px' }}>
                <Sparkles size={16} />
              </div>
              <div style={{ flex: 1, lineHeight: '1.45' }}>
                <strong style={{ color: 'var(--accent-light)' }}>Best Strategy ({winningScenario.replace(/_/g, ' ')}):</strong>{' '}
                {simResult.recommendationMessage
                  ? renderLocalizedMessage(simResult.recommendationMessage, t, i18n.language, simResult.recommendationText)
                  : simResult.recommendationText}
              </div>
            </div>
          )}

          {/* ── Scenario Comparison Bar Chart ── */}
          {(() => {
            const buyNowBalUsd = simResult.buyNow?.projectionState?.month3EndingBalance
            const waitBalUsd = simResult.buyInNMonths?.projectionState?.month3EndingBalance
            const emiBalUsd = simResult.buyViaEMI?.projectionState?.month3EndingBalance

            const buyNowFormatted = formatCurrency(buyNowBalUsd, currency)
            const waitFormatted = formatCurrency(waitBalUsd, currency)
            const emiFormatted = formatCurrency(emiBalUsd, currency)

            const chartBars = [
              {
                id: 'buyNow',
                label: `⚡ ${t('whatIf.buyNow', 'Buy Now')}`,
                subLabel: t('whatIf.buyNowSubtitle', 'Immediate Purchase'),
                valUsd: Number(buyNowBalUsd) || 0,
                formatted: buyNowFormatted,
                color: '#f87171'
              },
              {
                id: 'wait',
                label: `⏳ ${t('whatIf.waitNMonths', { months: simResult.delayMonths || 3 })}`,
                subLabel: t('whatIf.waitSubtitle', 'Deferred Purchase'),
                valUsd: Number(waitBalUsd) || 0,
                formatted: waitFormatted,
                color: '#38bdf8'
              },
              {
                id: 'emi',
                label: `💳 ${t('whatIf.buyViaEmi', 'Buy via EMI')} (${simResult.emiTenureMonths || 6}m)`,
                subLabel: 'Installment Plan',
                valUsd: Number(emiBalUsd) || 0,
                formatted: emiFormatted,
                color: '#a855f7'
              }
            ]

            let minValUsd = 0.0
            let maxValUsd = 0.0
            chartBars.forEach(b => {
              if (b.valUsd < minValUsd) minValUsd = b.valUsd
              if (b.valUsd > maxValUsd) maxValUsd = b.valUsd
            })

            const yMinUsd = minValUsd < 0 ? minValUsd * 1.15 : 0.0
            const yMaxUsd = maxValUsd > 0 ? maxValUsd * 1.25 : 1000.0
            const rangeUsd = yMaxUsd - yMinUsd <= 0 ? 100.0 : yMaxUsd - yMinUsd

            const svgWidth = 780
            const svgHeight = 340
            const plotLeft = 95
            const plotRight = 730
            const plotTop = 65
            const plotBottom = 265
            const plotWidth = plotRight - plotLeft
            const plotHeight = plotBottom - plotTop

            const barWidth = 110
            const barCenters = [
              plotLeft + plotWidth / 6,
              plotLeft + plotWidth / 2,
              plotLeft + (5 * plotWidth) / 6
            ]

            const ticks = [0, 1, 2, 3, 4].map(i => {
              const tickValUsd = yMinUsd + (rangeUsd * i / 4.0)
              const tickY = plotBottom - ((tickValUsd - yMinUsd) / rangeUsd * plotHeight)
              return {
                val: tickValUsd,
                y: tickY,
                formatted: formatCurrency(tickValUsd, currency, 0)
              }
            })

            const chartTitle = t('chart.scenarioComparison', 'Scenario Comparison')
            const yAxisLabel = t('chart.month3EndingBalance', 'Projected Balance ({{symbol}})', { symbol })

            return (
              <div
                className="scenario-chart-wrapper"
                id="what-if-scenario-comparison-chart"
                style={{
                  background: '#0f172a',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--border-subtle)',
                  padding: '16px 20px',
                  position: 'relative'
                }}
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox={`0 0 ${svgWidth} ${svgHeight}`}
                  style={{
                    width: '100%',
                    height: 'auto',
                    maxWidth: `${svgWidth}px`,
                    display: 'block',
                    margin: '0 auto',
                    fontFamily: 'inherit'
                  }}
                  role="img"
                  aria-label="Scenario Comparison Chart"
                >
                  {/* Chart Title & Subtitle */}
                  <text
                    x={plotLeft}
                    y={30}
                    fill="#f1f5f9"
                    fontSize={16}
                    fontWeight={800}
                    style={{ fontFamily: 'inherit' }}
                  >
                    {chartTitle}
                  </text>
                  <text
                    x={plotLeft}
                    y={48}
                    fill="#94a3b8"
                    fontSize={11.5}
                    style={{ fontFamily: 'inherit' }}
                  >
                    {simResult.scenarioDescription ? `Simulated: "${simResult.scenarioDescription}" (${formatCurrency(simResult.totalAmount, currency)})` : ''}
                  </text>

                  {/* Y-axis Label (Rotated) */}
                  <text
                    x={24}
                    y={plotTop + plotHeight / 2}
                    fill="#94a3b8"
                    fontSize={12}
                    fontWeight={600}
                    textAnchor="middle"
                    transform={`rotate(-90 24 ${plotTop + plotHeight / 2})`}
                    style={{ fontFamily: 'inherit' }}
                  >
                    {yAxisLabel}
                  </text>

                  {/* Horizontal Gridlines & Y-axis Value Ticks */}
                  {ticks.map((tick, idx) => (
                    <g key={idx}>
                      <line
                        x1={plotLeft}
                        y1={tick.y}
                        x2={plotRight}
                        y2={tick.y}
                        stroke="rgba(255,255,255,0.08)"
                        strokeWidth={1}
                      />
                      <text
                        x={plotLeft - 10}
                        y={tick.y + 4}
                        fill="#94a3b8"
                        fontSize={11}
                        textAnchor="end"
                        style={{ fontFamily: 'inherit', fontVariantNumeric: 'tabular-nums' }}
                      >
                        {tick.formatted}
                      </text>
                    </g>
                  ))}

                  {/* Bars & Labels */}
                  {chartBars.map((bar, idx) => {
                    const cx = barCenters[idx]
                    const barH = Math.max(4, ((bar.valUsd - yMinUsd) / rangeUsd) * plotHeight)
                    const barY = plotBottom - barH
                    const barX = cx - (barWidth / 2)

                    return (
                      <g key={bar.id} className="scenario-bar-group">
                        {/* Bar Rectangle */}
                        <rect
                          x={barX}
                          y={barY}
                          width={barWidth}
                          height={barH}
                          rx={6}
                          fill={bar.color}
                          fillOpacity={0.9}
                          stroke={bar.color}
                          strokeWidth={1}
                        />

                        {/* Exact Value Label Above Each Bar */}
                        <text
                          x={cx}
                          y={barY - 10}
                          fill="#ffffff"
                          fontSize={13}
                          fontWeight={800}
                          textAnchor="middle"
                          style={{ fontFamily: 'inherit', fontVariantNumeric: 'tabular-nums' }}
                          data-scenario={bar.id}
                          className="scenario-bar-value"
                        >
                          {bar.formatted}
                        </text>

                        {/* Scenario Title Below Bar */}
                        <text
                          x={cx}
                          y={plotBottom + 24}
                          fill="#f1f5f9"
                          fontSize={13}
                          fontWeight={700}
                          textAnchor="middle"
                          style={{ fontFamily: 'inherit' }}
                        >
                          {bar.label}
                        </text>

                        {/* Subtitle Below Scenario Title */}
                        <text
                          x={cx}
                          y={plotBottom + 40}
                          fill="#94a3b8"
                          fontSize={11}
                          textAnchor="middle"
                          style={{ fontFamily: 'inherit' }}
                        >
                          {bar.subLabel}
                        </text>
                      </g>
                    )
                  })}
                </svg>
              </div>
            )
          })()}

          {/* Three Side-by-Side Scenario Cards */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: '16px'
            }}
          >
            {/* ── SCENARIO 1: BUY NOW ── */}
            <div
              style={{
                padding: '20px',
                background: isBuyNowRecommended ? 'rgba(79, 140, 255, 0.06)' : 'var(--surface-raised)',
                borderRadius: 'var(--radius-lg)',
                border: isBuyNowRecommended ? '2px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
                position: 'relative'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                <div>
                  <div style={{ fontWeight: '700', fontSize: '14px', color: 'var(--text-primary)' }}>
                    {t('whatIf.buyNow')}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                    {t('whatIf.buyNowSubtitle')}
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                  {isBuyNowRecommended && (
                    <span className="chip chip-info" style={{ fontWeight: '700', fontSize: '10.5px' }}>
                      <Sparkles size={12} /> {t('whatIf.recommended')}
                    </span>
                  )}
                  <span className="chip chip-neutral" style={{ fontSize: '10px' }}>
                    Full Deduction
                  </span>
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  {t('whatIf.projectedEndingBalance')}
                </div>
                <div
                  style={{
                    fontSize: '22px',
                    fontWeight: '800',
                    color: Number(simResult.buyNow?.projectionState?.month3EndingBalance || 0) >= 0 ? 'var(--text-primary)' : 'var(--color-negative)',
                    fontVariantNumeric: 'tabular-nums',
                    marginTop: '2px'
                  }}
                >
                  {formatCurrency(simResult.buyNow?.projectionState?.month3EndingBalance, currency)}
                </div>
                <div style={{ fontSize: '11.5px', color: 'var(--color-negative)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' }}>
                  {t('whatIf.impact', { amount: formatCurrency(simResult.buyNow?.diff?.month3EndingBalanceImpact, currency) })}
                </div>
              </div>

              <div style={{ marginTop: 'auto', paddingTop: '10px', borderTop: '1px solid var(--border-subtle)', fontSize: '12px', color: 'var(--text-muted)', display: 'flex', justifyContent: 'space-between' }}>
                <span>{t('whatIf.riskAssessment')}</span>
                <strong style={{ color: 'var(--text-primary)' }}>
                  {simResult.buyNow?.projectionState?.riskLevel} ({simResult.buyNow?.projectionState?.totalRiskScore} pts)
                </strong>
              </div>
            </div>

            {/* ── SCENARIO 2: WAIT ── */}
            <div
              style={{
                padding: '20px',
                background: isWaitRecommended ? 'rgba(79, 140, 255, 0.06)' : 'var(--surface-raised)',
                borderRadius: 'var(--radius-lg)',
                border: isWaitRecommended ? '2px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
                position: 'relative'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                <div>
                  <div style={{ fontWeight: '700', fontSize: '14px', color: 'var(--text-primary)' }}>
                    {t('whatIf.waitNMonths', { months: simResult.delayMonths || 3 })}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                    {t('whatIf.waitSubtitle')}
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                  {isWaitRecommended && (
                    <span className="chip chip-info" style={{ fontWeight: '700', fontSize: '10.5px' }}>
                      <Sparkles size={12} /> {t('whatIf.recommended')}
                    </span>
                  )}
                  <span className="chip chip-neutral" style={{ fontSize: '10px' }}>
                    Deferred {simResult.delayMonths || 3} Mos
                  </span>
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  {t('whatIf.projectedEndingBalance')}
                </div>
                <div
                  style={{
                    fontSize: '22px',
                    fontWeight: '800',
                    color: 'var(--text-primary)',
                    fontVariantNumeric: 'tabular-nums',
                    marginTop: '2px'
                  }}
                >
                  {formatCurrency(simResult.buyInNMonths?.projectionState?.month3EndingBalance, currency)}
                </div>
                <div style={{ fontSize: '11.5px', color: 'var(--text-muted)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' }}>
                  {t('whatIf.impact', { amount: formatCurrency(simResult.buyInNMonths?.diff?.month3EndingBalanceImpact, currency) })}
                </div>
              </div>

              <div style={{ marginTop: 'auto', paddingTop: '10px', borderTop: '1px solid var(--border-subtle)', fontSize: '12px', color: 'var(--text-muted)', display: 'flex', justifyContent: 'space-between' }}>
                <span>{t('whatIf.riskAssessment')}</span>
                <strong style={{ color: 'var(--text-primary)' }}>
                  {simResult.buyInNMonths?.projectionState?.riskLevel} ({simResult.buyInNMonths?.projectionState?.totalRiskScore} pts)
                </strong>
              </div>
            </div>

            {/* ── SCENARIO 3: EMI ── */}
            <div
              style={{
                padding: '20px',
                background: isEmiRecommended ? 'rgba(79, 140, 255, 0.06)' : 'var(--surface-raised)',
                borderRadius: 'var(--radius-lg)',
                border: isEmiRecommended ? '2px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
                position: 'relative'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                <div>
                  <div style={{ fontWeight: '700', fontSize: '14px', color: 'var(--text-primary)' }}>
                    {t('whatIf.buyViaEmi')}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                    {t('whatIf.emiSubtitle', { amount: formatCurrency(simResult.emiMonthlyAmount, currency) })}
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                  {isEmiRecommended && (
                    <span className="chip chip-info" style={{ fontWeight: '700', fontSize: '10.5px' }}>
                      <Sparkles size={12} /> {t('whatIf.recommended')}
                    </span>
                  )}
                  <span
                    className={simResult.emiEvaluation?.feasible ? 'chip chip-pos' : 'chip chip-neg'}
                    style={{ fontSize: '10px' }}
                  >
                    {simResult.emiEvaluation?.feasible ? t('whatIf.affordable') : t('whatIf.limitExceeded')}
                  </span>
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  {t('whatIf.projectedEndingBalance')}
                </div>
                <div
                  style={{
                    fontSize: '22px',
                    fontWeight: '800',
                    color: 'var(--text-primary)',
                    fontVariantNumeric: 'tabular-nums',
                    marginTop: '2px'
                  }}
                >
                  {formatCurrency(simResult.buyViaEMI?.projectionState?.month3EndingBalance, currency)}
                </div>
                <div style={{ fontSize: '11.5px', color: 'var(--color-positive)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' }}>
                  {t('whatIf.impact', { amount: formatCurrency(simResult.buyViaEMI?.diff?.month3EndingBalanceImpact, currency) })}
                </div>
              </div>

              {simResult.emiEvaluation && (
                <div style={{ marginTop: 'auto', paddingTop: '10px', borderTop: '1px solid var(--border-subtle)', fontSize: '11.5px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--text-muted)' }}>{t('whatIf.emiRatio')}</span>
                    <span style={{ color: Number(simResult.emiEvaluation.rawNumbers?.emiToSalaryRatio) <= 0.30 ? 'var(--color-positive)' : 'var(--color-negative)', fontWeight: '700', fontVariantNumeric: 'tabular-nums' }}>
                      {(Number(simResult.emiEvaluation.rawNumbers?.emiToSalaryRatio || 0) * 100).toFixed(1)}% (Max 30%)
                    </span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--text-muted)' }}>{t('whatIf.riskAssessment')}</span>
                    <strong style={{ color: 'var(--text-primary)' }}>
                      {simResult.buyViaEMI?.projectionState?.riskLevel} ({simResult.buyViaEMI?.projectionState?.totalRiskScore} pts)
                    </strong>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className="empty-state">
          <div className="empty-state-icon">
            <Sliders size={24} />
          </div>
          <div className="empty-state-title">Compare Purchase Strategies</div>
          <p className="empty-state-desc">
            Enter a proposed expense and click Simulate to evaluate whether you should buy immediately, wait a few months, or finance via monthly installments.
          </p>
        </div>
      )}
    </div>
  )
}

export default WhatIfSimulatorPanel
