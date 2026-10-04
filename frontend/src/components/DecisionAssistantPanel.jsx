import React, { useState } from 'react'
import { Sparkles, Send, AlertTriangle, User } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { formatCurrency as formatCurrencyUtil, getCurrencySymbol } from '../utils/currency'
import { LANGUAGE_MAP } from '../config/languages'

function DecisionAssistantPanel() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'

  const QUICK_QUESTIONS = [
    { label: t('assistant.promptLaptop'), question: t('assistant.questionLaptop') },
    { label: t('assistant.promptVacation'), question: t('assistant.questionVacation') },
    { label: t('assistant.promptHealth'), question: t('assistant.questionHealth') },
    { label: t('assistant.promptGoals'), question: t('assistant.questionGoals') }
  ]

  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [lastUserQuestion, setLastUserQuestion] = useState('')
  const [result, setResult] = useState(null)

  const handleSelectSuggested = (q) => {
    setQuestion(q)
  }

  const handleSubmit = async (e) => {
    if (e) e.preventDefault()
    if (!question || !question.trim()) {
      setErrorMsg(t('assistant.enterQuestionError'))
      return
    }

    const currentQ = question.trim()
    setErrorMsg('')
    setLoading(true)
    setLastUserQuestion(currentQ)
    setQuestion('')

    const langCode = i18n.language || 'en'
    const langName = LANGUAGE_MAP[langCode]?.name || 'English'

    try {
      const res = await api.post('/decision-assistant', {
        question: currentQ,
        languageCode: langCode,
        languageName: langName
      })
      setResult(res.data)
    } catch (err) {
      setErrorMsg(err.response?.data?.message || t('assistant.guidanceFailedError'))
    } finally {
      setLoading(false)
    }
  }

  const formatCurrency = (val) => {
    if (val == null) return '—'
    const num = Number(val)
    return isNaN(num) ? '—' : formatCurrencyUtil(num, currency, 2, i18n.language)
  }

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">
            <Sparkles size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('assistant.title')}
          </h3>
          <span className="chart-subtitle">
            {t('assistant.subtitle')}
          </span>
        </div>

        <div className="chip chip-neutral" style={{ fontSize: '11px' }}>
          {t('whatIf.zeroDbMutation')}
        </div>
      </div>

      <div className="chat-panel">
        {/* Suggested Question Chips Row */}
        <div className="chat-suggested-row">
          <span style={{ fontSize: '11.5px', color: 'var(--text-muted)', alignSelf: 'center', fontWeight: '600', marginRight: '4px' }}>
            {t('assistant.suggestions')}
          </span>
          {QUICK_QUESTIONS.map((item, idx) => (
            <button
              key={idx}
              type="button"
              className="chat-suggested-chip"
              onClick={() => handleSelectSuggested(item.question)}
            >
              {item.label}
            </button>
          ))}
        </div>

        {/* Error Alert if any */}
        {errorMsg && (
          <div className="alert alert-error" style={{ margin: '0' }}>
            <AlertTriangle size={15} />
            {errorMsg}
          </div>
        )}

        {/* Message Bubbles History */}
        <div className="chat-history">
          {/* Welcome Message Bubble from AI */}
          <div className="chat-msg-row">
            <div className="chat-avatar assistant">
              <Sparkles size={16} />
            </div>
            <div className="chat-bubble assistant">
              <div style={{ fontWeight: '600', fontSize: '13px', color: 'var(--accent-light)', marginBottom: '4px' }}>
                {t('assistant.decisionEngine')}
              </div>
              <p style={{ margin: 0, fontSize: '13px', color: 'var(--text-primary)', lineHeight: '1.5' }}>
                {t('assistant.greeting', { name: user?.username || 'there' })}
              </p>
            </div>
          </div>

          {/* User's Submitted Question Bubble */}
          {lastUserQuestion && (
            <div className="chat-msg-row user">
              <div className="chat-bubble user">
                <p style={{ margin: 0, fontSize: '13.5px', color: 'var(--text-primary)' }}>
                  {lastUserQuestion}
                </p>
              </div>
              <div className="chat-avatar user">
                <User size={16} />
              </div>
            </div>
          )}

          {/* Loading State Bubble */}
          {loading && (
            <div className="chat-msg-row">
              <div className="chat-avatar assistant">
                <Sparkles size={16} />
              </div>
              <div className="chat-bubble assistant">
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <div className="chat-typing-indicator">
                    <span />
                    <span />
                    <span />
                  </div>
                  <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                    {t('assistant.evaluatingFeasibility')}
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* Assistant's Response Bubble */}
          {result && !loading && (
            <div className="chat-msg-row">
              <div className="chat-avatar assistant">
                <Sparkles size={16} />
              </div>
              <div className="chat-bubble assistant">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', flexWrap: 'wrap', gap: '6px' }}>
                  <span style={{ fontWeight: '700', fontSize: '13px', color: 'var(--accent-light)' }}>
                    {t('assistant.guidanceHeader')}
                  </span>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                    {result.assembledAt ? new Date(result.assembledAt).toLocaleTimeString() : t('assistant.justNow')}
                  </span>
                </div>

                {/* Formatted Guidance Text */}
                <div style={{ fontSize: '13.5px', lineHeight: '1.6', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>
                  {result.llmRecommendation}
                </div>

                {/* Compact Context Snapshot Pill Bar */}
                {result.financialSnapshot && (
                  <div
                    style={{
                      marginTop: '12px',
                      padding: '8px 12px',
                      background: 'var(--bg-primary)',
                      borderRadius: '8px',
                      border: '1px solid var(--border-subtle)',
                      display: 'flex',
                      gap: '12px',
                      flexWrap: 'wrap',
                      fontSize: '11.5px'
                    }}
                  >
                    <span style={{ color: 'var(--text-secondary)' }}>
                      {t('assistant.balance')} <strong style={{ color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>{formatCurrency(result.financialSnapshot.totalBalance)}</strong>
                    </span>
                    <span style={{ color: 'var(--text-secondary)' }}>
                      {t('assistant.income')} <strong style={{ color: 'var(--color-positive)', fontVariantNumeric: 'tabular-nums' }}>+{formatCurrency(result.financialSnapshot.monthlyIncome)}</strong>
                    </span>
                    <span style={{ color: 'var(--text-secondary)' }}>
                      {t('assistant.outflow')} <strong style={{ color: 'var(--color-negative)', fontVariantNumeric: 'tabular-nums' }}>-{formatCurrency(result.financialSnapshot.monthlyExpenses)}</strong>
                    </span>
                    <span style={{ color: 'var(--text-secondary)' }}>
                      {t('assistant.savingsRatio')} <strong style={{ color: 'var(--accent-light)', fontVariantNumeric: 'tabular-nums' }}>{result.financialSnapshot.savingsRatio}%</strong>
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Chat Input Bar */}
        <form onSubmit={handleSubmit} className="chat-input-bar">
          <input
            type="text"
            className="chat-input-field"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder={t('assistant.chatPlaceholder')}
            disabled={loading}
          />

          <button
            type="submit"
            className="btn-primary"
            disabled={loading || !question.trim()}
            style={{ height: '36px', padding: '0 16px', fontSize: '13px' }}
          >
            <Send size={15} />
            <span style={{ display: 'none' }}>{t('assistant.send')}</span>
          </button>
        </form>
      </div>
    </div>
  )
}

export default DecisionAssistantPanel
