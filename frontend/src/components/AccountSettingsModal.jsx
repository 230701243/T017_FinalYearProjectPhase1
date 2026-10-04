import { useState, useEffect } from 'react'
import { Settings, User, Globe, Shield, X, CheckCircle2, AlertTriangle, AlertCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'
import LanguagePicker from './LanguagePicker'

const CURRENCY_FLAG_MAP = {
  USD: '🇺🇸',
  INR: '🇮🇳',
  EUR: '🇪🇺',
  GBP: '🇬🇧',
  JPY: '🇯🇵',
  CAD: '🇨🇦',
  AUD: '🇦🇺',
  SGD: '🇸🇬',
  AED: '🇦🇪',
  CHF: '🇨🇭',
  US: '🇺🇸',
  IN: '🇮🇳',
  EU: '🇪🇺',
  GB: '🇬🇧',
  JP: '🇯🇵',
  CA: '🇨🇦',
  AU: '🇦🇺',
  SG: '🇸🇬',
  AE: '🇦🇪',
  CH: '🇨🇭'
}

const DEFAULT_CURRENCIES = [
  { countryCode: 'US', currencyCode: 'USD', currencySymbol: '$', exchangeRateToUsd: 1.0, currencyName: 'US Dollar' },
  { countryCode: 'IN', currencyCode: 'INR', currencySymbol: '₹', exchangeRateToUsd: 83.5, currencyName: 'Indian Rupee' },
  { countryCode: 'EU', currencyCode: 'EUR', currencySymbol: '€', exchangeRateToUsd: 0.92, currencyName: 'Euro' },
  { countryCode: 'GB', currencyCode: 'GBP', currencySymbol: '£', exchangeRateToUsd: 0.79, currencyName: 'British Pound' },
  { countryCode: 'JP', currencyCode: 'JPY', currencySymbol: '¥', exchangeRateToUsd: 155.0, currencyName: 'Japanese Yen' },
  { countryCode: 'CA', currencyCode: 'CAD', currencySymbol: 'CA$', exchangeRateToUsd: 1.36, currencyName: 'Canadian Dollar' },
  { countryCode: 'AU', currencyCode: 'AUD', currencySymbol: 'A$', exchangeRateToUsd: 1.52, currencyName: 'Australian Dollar' },
  { countryCode: 'SG', currencyCode: 'SGD', currencySymbol: 'S$', exchangeRateToUsd: 1.35, currencyName: 'Singapore Dollar' },
  { countryCode: 'AE', currencyCode: 'AED', currencySymbol: 'د.إ', exchangeRateToUsd: 3.6725, currencyName: 'UAE Dirham' },
  { countryCode: 'CH', currencyCode: 'CHF', currencySymbol: 'CHF', exchangeRateToUsd: 0.90, currencyName: 'Swiss Franc' },
]

function AccountSettingsModal({ isOpen, onClose, onSettingsUpdated }) {
  const { t } = useTranslation()
  const { user, updateUser } = useAuth()
  const [country, setCountry] = useState(user?.country || 'US')
  const [preferredCurrency, setPreferredCurrency] = useState(user?.preferredCurrency || 'USD')
  const [hasHealthInsurance, setHasHealthInsurance] = useState(user?.hasHealthInsurance || false)
  const [hasLifeInsurance, setHasLifeInsurance] = useState(user?.hasLifeInsurance || false)
  const [currencies, setCurrencies] = useState(DEFAULT_CURRENCIES)
  const [saving, setSaving] = useState(false)
  const [statusMsg, setStatusMsg] = useState({ text: '', type: '' })
  const [fieldErrors, setFieldErrors] = useState({})

  // Keyboard shortcut: Escape closes modal
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  useEffect(() => {
    if (isOpen) {
      if (user?.preferredCurrency) {
        setPreferredCurrency(user.preferredCurrency)
      }
      if (user?.country) {
        setCountry(user.country)
      }
      if (user?.hasHealthInsurance !== undefined) {
        setHasHealthInsurance(Boolean(user.hasHealthInsurance))
      }
      if (user?.hasLifeInsurance !== undefined) {
        setHasLifeInsurance(Boolean(user.hasLifeInsurance))
      }

      // Fetch fresh profile & currencies from backend
      api.get('/currencies')
        .then(res => {
          if (Array.isArray(res.data) && res.data.length > 0) {
            setCurrencies(res.data)
          }
        })
        .catch(() => {})

      api.get('/user/profile')
        .then(res => {
          if (res.data) {
            setCountry(res.data.country || 'US')
            setPreferredCurrency(res.data.preferredCurrency || 'USD')
            setHasHealthInsurance(Boolean(res.data.hasHealthInsurance))
            setHasLifeInsurance(Boolean(res.data.hasLifeInsurance))
            updateUser({
              country: res.data.country || 'US',
              preferredCurrency: res.data.preferredCurrency || 'USD',
              hasHealthInsurance: Boolean(res.data.hasHealthInsurance),
              hasLifeInsurance: Boolean(res.data.hasLifeInsurance),
              email: res.data.email
            })
          }
        })
        .catch(() => {
          setCountry(user?.country || 'US')
          setPreferredCurrency(user?.preferredCurrency || 'USD')
          setHasHealthInsurance(Boolean(user?.hasHealthInsurance))
          setHasLifeInsurance(Boolean(user?.hasLifeInsurance))
        })
      setStatusMsg({ text: '', type: '' })
      setFieldErrors({})
    }
  }, [isOpen])

  if (!isOpen) return null

  const selectedCurrencyObj = currencies.find(c => c.currencyCode === preferredCurrency) || {
    currencyCode: 'USD',
    currencySymbol: '$',
    exchangeRateToUsd: 1.0,
    currencyName: 'US Dollar'
  }

  const handleCurrencyChange = (e) => {
    const code = e.target.value
    setPreferredCurrency(code)
    const match = currencies.find(c => c.currencyCode === code)
    if (match) {
      setCountry(match.countryCode)
    }
    if (fieldErrors.currency) setFieldErrors(prev => ({ ...prev, currency: null }))
  }

  const handleSave = async (e) => {
    e.preventDefault()
    const errors = {}
    if (!country || !country.trim()) {
      errors.country = 'Country code is required (e.g. US, IN).'
    }
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      return
    }

    setSaving(true)
    setStatusMsg({ text: '', type: '' })
    try {
      const res = await api.put('/user/profile', {
        country: country.trim().toUpperCase(),
        preferredCurrency: preferredCurrency.trim().toUpperCase(),
        hasHealthInsurance,
        hasLifeInsurance
      })
      updateUser({
        country: res.data.country,
        preferredCurrency: res.data.preferredCurrency,
        hasHealthInsurance: Boolean(res.data.hasHealthInsurance),
        hasLifeInsurance: Boolean(res.data.hasLifeInsurance)
      })
      if (onSettingsUpdated) {
        onSettingsUpdated(res.data.preferredCurrency)
      }
      setStatusMsg({ text: 'Preferences updated successfully.', type: 'success' })
      setTimeout(() => {
        onClose()
      }, 700)
    } catch (err) {
      setStatusMsg({
        text: err.response?.data?.message || 'Failed to update settings. Please try again.',
        type: 'error'
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true" aria-labelledby="settings-modal-title">
      <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: '520px' }}>
        <div className="modal-header">
          <h2 id="settings-modal-title" className="modal-title">
            <Settings size={18} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
            {t('modals.accountSettingsTitle')}
          </h2>
          <button onClick={onClose} className="btn-close" aria-label={t('common.close')}>
            <X size={18} />
          </button>
        </div>

        {statusMsg.text && (
          <div className={statusMsg.type === 'success' ? 'alert alert-success' : 'alert alert-error'}>
            {statusMsg.type === 'success' ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
            {statusMsg.text}
          </div>
        )}

        <form onSubmit={handleSave} noValidate>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', maxHeight: '68vh', overflowY: 'auto', paddingRight: '2px' }}>
            {/* GROUP 1: Signed-in User Identity */}
            <div className="settings-group">
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                <User size={13} />
                {t('settings.profileGroupTitle')}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>{user?.username}</div>
                  <div style={{ fontSize: '12.5px', color: 'var(--text-secondary)' }}>{user?.email || t('settings.activeAccount')}</div>
                </div>
                <span className="chip chip-info" style={{ fontSize: '11.5px' }}>{t('common.verified')}</span>
              </div>
            </div>

            {/* GROUP 2: Display & Localization */}
            <div className="settings-group">
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Globe size={13} />
                {t('settings.displayGroupTitle')}
              </div>

              {/* Language Selector */}
              <div className="form-group">
                <label className="field-label">
                  <span>{t('settings.languageLabel')}</span>
                </label>
                <LanguagePicker variant="topbar" id="modal-language-picker" />
              </div>

              {/* Currency Dropdown showing flag or symbol, code and name in a clean list */}
              <div className="form-group">
                <label className="field-label" htmlFor="settings-currency">
                  <span>{t('settings.currencyLabel')}</span>
                  <span style={{ fontSize: '11.5px', color: 'var(--accent-light)', fontWeight: 500 }}>
                    {t('settings.currencyActive', { code: selectedCurrencyObj.currencyCode })}
                  </span>
                </label>
                <select
                  id="settings-currency"
                  value={preferredCurrency}
                  onChange={handleCurrencyChange}
                  className="currency-select-clean"
                >
                  {currencies.map(c => {
                    const flag = CURRENCY_FLAG_MAP[c.currencyCode] || CURRENCY_FLAG_MAP[c.countryCode] || '🌐'
                    return (
                      <option key={c.currencyCode} value={c.currencyCode}>
                        {flag}  {c.currencySymbol}  {c.currencyCode} — {c.currencyName || c.countryCode}
                      </option>
                    )
                  })}
                </select>
              </div>

              {/* Real-Time Conversion Preview Chip */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 14px',
                background: 'var(--bg-primary)',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--border-subtle)',
                fontSize: '12.5px'
              }}>
                <span style={{ color: 'var(--text-secondary)' }}>{t('settings.liveConversionNotice')}</span>
                <span style={{ fontWeight: '600', color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
                  {t('settings.liveConversionRate', { rate: `${selectedCurrencyObj.currencySymbol}${selectedCurrencyObj.exchangeRateToUsd ?? 1.0}`, code: selectedCurrencyObj.currencyCode })}
                </span>
              </div>

              {/* Country Code */}
              <div className="form-group">
                <label className="field-label" htmlFor="settings-country">{t('settings.countryLabel')}</label>
                <input
                  id="settings-country"
                  type="text"
                  maxLength={4}
                  value={country}
                  onChange={e => {
                    setCountry(e.target.value.toUpperCase())
                    if (fieldErrors.country) setFieldErrors(prev => ({ ...prev, country: null }))
                  }}
                  placeholder="US"
                  className={`input-field ${fieldErrors.country ? 'input-has-error' : ''}`}
                />
                {fieldErrors.country && (
                  <div className="field-error">
                    <AlertCircle size={13} />
                    <span>{fieldErrors.country}</span>
                  </div>
                )}
              </div>
            </div>

            {/* GROUP 3: Coverage & Risk Profile */}
            <div className="settings-group">
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Shield size={13} />
                {t('settings.insuranceGroupTitle')}
              </div>
              <label style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '13px', color: 'var(--text-primary)', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  id="checkbox-health-insurance"
                  checked={hasHealthInsurance}
                  onChange={e => setHasHealthInsurance(e.target.checked)}
                  style={{ width: '16px', height: '16px', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
                />
                <span>{t('settings.healthInsuranceLabel')}</span>
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '13px', color: 'var(--text-primary)', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  id="checkbox-life-insurance"
                  checked={hasLifeInsurance}
                  onChange={e => setHasLifeInsurance(e.target.checked)}
                  style={{ width: '16px', height: '16px', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
                />
                <span>{t('settings.lifeInsuranceLabel')}</span>
              </label>
              <div style={{ fontSize: '11.5px', color: 'var(--text-muted)', lineHeight: '1.4' }}>
                {t('settings.insuranceNotice')}
              </div>
            </div>
          </div>

          <div className="modal-actions" style={{ marginTop: '20px' }}>
            <button
              type="button"
              onClick={onClose}
              className="btn-secondary"
            >
              {t('common.cancel')}
            </button>
            <button
              id="save-preferences-btn"
              type="submit"
              className="btn-primary"
              disabled={saving}
            >
              {saving ? <span className="spinner" /> : t('common.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default AccountSettingsModal
