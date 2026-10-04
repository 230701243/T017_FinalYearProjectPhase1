import React, { useState, useRef, useEffect } from 'react'
import { Sparkles, Send, AlertTriangle, User, RotateCcw, Bot } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { formatCurrency as formatCurrencyUtil } from '../utils/currency'
import { LANGUAGE_MAP } from '../config/languages'

function AiAssistantView() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const currency = user?.preferredCurrency || 'USD'
  const messagesEndRef = useRef(null)
  const inputRef = useRef(null)

  const QUICK_QUESTIONS = [
    { label: t('assistant.promptLaptop'), question: t('assistant.questionLaptop') },
    { label: t('assistant.promptVacation'), question: t('assistant.questionVacation') },
    { label: t('assistant.promptHealth'), question: t('assistant.questionHealth') },
    { label: t('assistant.promptGoals'), question: t('assistant.questionGoals') }
  ]

  const createInitialMessage = () => ({
    id: 'welcome',
    sender: 'assistant',
    title: t('assistant.decisionEngine'),
    text: t('assistant.greeting', { name: user?.username || 'there' }),
    timestamp: new Date()
  })

  const [messages, setMessages] = useState([createInitialMessage()])
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, loading])

  const handleSelectSuggested = (q) => {
    setQuestion(q)
    if (inputRef.current) {
      inputRef.current.focus()
    }
  }

  const handleResetSession = () => {
    setMessages([createInitialMessage()])
    setErrorMsg('')
    setQuestion('')
  }

  const handleSubmit = async (e) => {
    if (e) e.preventDefault()
    if (!question || !question.trim()) {
      setErrorMsg(t('assistant.enterQuestionError'))
      return
    }

    const currentQ = question.trim()
    setErrorMsg('')
    setQuestion('')

    // Append user message to session conversation history
    const userMsg = {
      id: Date.now() + '-user',
      sender: 'user',
      text: currentQ,
      timestamp: new Date()
    }
    setMessages(prev => [...prev, userMsg])
    setLoading(true)

    const langCode = i18n.language || 'en'
    const langName = LANGUAGE_MAP[langCode]?.name || 'English'

    try {
      const res = await api.post('/decision-assistant', {
        question: currentQ,
        languageCode: langCode,
        languageName: langName
      })

      const assistantMsg = {
        id: Date.now() + '-assistant',
        sender: 'assistant',
        title: t('assistant.guidanceHeader'),
        text: res.data.llmRecommendation,
        snapshot: res.data.financialSnapshot,
        assembledAt: res.data.assembledAt || new Date().toISOString(),
        timestamp: new Date()
      }
      setMessages(prev => [...prev, assistantMsg])
    } catch (err) {
      const errMsg = err.response?.data?.message || t('assistant.guidanceFailedError')
      setErrorMsg(errMsg)
      const errorBubble = {
        id: Date.now() + '-error',
        sender: 'assistant',
        title: t('assistant.guidanceHeader'),
        text: errMsg,
        isError: true,
        timestamp: new Date()
      }
      setMessages(prev => [...prev, errorBubble])
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
    <div className="ai-assistant-page-container">
      <div className="chart-card ai-assistant-main-card">
        {/* Header Bar */}
        <div className="chart-header" style={{ paddingBottom: '14px', borderBottom: '1px solid var(--border-subtle)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div className="icon-tile" style={{ width: '40px', height: '40px', borderRadius: '12px', background: 'var(--accent-subtle)', color: 'var(--accent-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Sparkles size={22} strokeWidth={2} />
            </div>
            <div>
              <h2 className="chart-title" style={{ fontSize: '18px', fontWeight: '700', display: 'flex', alignItems: 'center', gap: '8px' }}>
                {t('assistant.title', 'AI Decision Assistant')}
                <span className="chip chip-info" style={{ fontSize: '11px', padding: '2px 8px' }}>
                  {t('assistant.liveSession')}
                </span>
              </h2>
              <span className="chart-subtitle" style={{ fontSize: '13px' }}>
                {t('assistant.subtitle', 'Chat-based financial guidance and purchase affordability assessments based on your raw financial snapshot')}
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button
              type="button"
              className="btn-secondary"
              onClick={handleResetSession}
              title={t('assistant.resetConversation')}
              style={{ padding: '6px 12px', fontSize: '12.5px', height: '32px' }}
            >
              <RotateCcw size={14} />
              <span>{t('assistant.reset')}</span>
            </button>
            <div className="chip chip-neutral" style={{ fontSize: '11px' }}>
              {t('whatIf.zeroDbMutation')}
            </div>
          </div>
        </div>

        {/* Chat Panel */}
        <div className="chat-panel" style={{ marginTop: '16px' }}>
          {/* Suggested Question Chips Row */}
          <div className="chat-suggested-row" style={{ padding: '4px 0' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)', alignSelf: 'center', fontWeight: '600', marginRight: '4px' }}>
              {t('assistant.suggestions')}
            </span>
            {QUICK_QUESTIONS.map((item, idx) => (
              <button
                key={idx}
                type="button"
                className="chat-suggested-chip"
                onClick={() => handleSelectSuggested(item.question)}
                title={item.question}
              >
                {item.label}
              </button>
            ))}
          </div>

          {/* Top Error Alert if any */}
          {errorMsg && (
            <div className="alert alert-error" style={{ margin: '4px 0' }}>
              <AlertTriangle size={15} />
              {errorMsg}
            </div>
          )}

          {/* Chat Messages History Container */}
          <div className="chat-history ai-assistant-chat-stream" style={{ minHeight: '420px', maxHeight: 'calc(100vh - 360px)', overflowY: 'auto' }}>
            {messages.map((msg) => {
              if (msg.sender === 'user') {
                return (
                  <div key={msg.id} className="chat-msg-row user">
                    <div className="chat-bubble user">
                      <p style={{ margin: 0, fontSize: '13.5px', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>
                        {msg.text}
                      </p>
                      <div style={{ textAlign: 'right', marginTop: '4px', fontSize: '10.5px', color: 'var(--text-muted)' }}>
                        {msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                      </div>
                    </div>
                    <div className="chat-avatar user">
                      <User size={16} />
                    </div>
                  </div>
                )
              }

              // Assistant message
              return (
                <div key={msg.id} className="chat-msg-row">
                  <div className="chat-avatar assistant">
                    <Sparkles size={16} />
                  </div>
                  <div className={`chat-bubble assistant${msg.isError ? ' bubble-error' : ''}`}>
                    {/* Header with Title and Timestamp */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', flexWrap: 'wrap', gap: '6px' }}>
                      <span style={{ fontWeight: '700', fontSize: '13px', color: msg.isError ? 'var(--color-negative)' : 'var(--accent-light)' }}>
                        {msg.title || t('assistant.guidanceHeader')}
                      </span>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        {msg.assembledAt
                          ? new Date(msg.assembledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                          : (msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : t('assistant.justNow'))}
                      </span>
                    </div>

                    {/* Guidance Content */}
                    <div style={{ fontSize: '13.5px', lineHeight: '1.6', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>
                      {msg.text}
                    </div>

                    {/* Raw Financial Numbers Snapshot Alongside Response */}
                    {msg.snapshot && (
                      <div
                        className="assistant-snapshot-bar"
                        style={{
                          marginTop: '12px',
                          padding: '10px 14px',
                          background: 'var(--bg-primary)',
                          borderRadius: '10px',
                          border: '1px solid var(--border-subtle)',
                          display: 'flex',
                          gap: '16px',
                          flexWrap: 'wrap',
                          fontSize: '12px'
                        }}
                      >
                        <span style={{ color: 'var(--text-secondary)' }}>
                          {t('assistant.balance')} <strong style={{ color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>{formatCurrency(msg.snapshot.totalBalance)}</strong>
                        </span>
                        <span style={{ color: 'var(--text-secondary)' }}>
                          {t('assistant.income')} <strong style={{ color: 'var(--color-positive)', fontVariantNumeric: 'tabular-nums' }}>+{formatCurrency(msg.snapshot.monthlyIncome)}</strong>
                        </span>
                        <span style={{ color: 'var(--text-secondary)' }}>
                          {t('assistant.outflow')} <strong style={{ color: 'var(--color-negative)', fontVariantNumeric: 'tabular-nums' }}>-{formatCurrency(msg.snapshot.monthlyExpenses)}</strong>
                        </span>
                        <span style={{ color: 'var(--text-secondary)' }}>
                          {t('assistant.savingsRatio')} <strong style={{ color: 'var(--accent-light)', fontVariantNumeric: 'tabular-nums' }}>{msg.snapshot.savingsRatio}%</strong>
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              )
            })}

            {/* Loading Indicator Bubble */}
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

            <div ref={messagesEndRef} />
          </div>

          {/* Chat Input Bar */}
          <form onSubmit={handleSubmit} className="chat-input-bar" style={{ marginTop: '8px' }}>
            <input
              ref={inputRef}
              type="text"
              className="chat-input-field"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder={t('assistant.chatPlaceholder')}
              disabled={loading}
              id="ai-assistant-input"
            />

            <button
              type="submit"
              className="btn-primary"
              disabled={loading || !question.trim()}
              style={{ height: '38px', padding: '0 18px', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px' }}
              id="ai-assistant-send-btn"
            >
              <span>{t('assistant.send')}</span>
              <Send size={15} />
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

export default AiAssistantView
