import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Wallet, AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'
import LanguagePicker from '../components/LanguagePicker'

const DEFAULT_CURRENCIES = [
  { countryCode: 'US', currencyCode: 'USD', currencySymbol: '$', currencyName: 'US Dollar' },
  { countryCode: 'IN', currencyCode: 'INR', currencySymbol: '₹', currencyName: 'Indian Rupee' },
  { countryCode: 'EU', currencyCode: 'EUR', currencySymbol: '€', currencyName: 'Euro' },
  { countryCode: 'GB', currencyCode: 'GBP', currencySymbol: '£', currencyName: 'British Pound' },
  { countryCode: 'JP', currencyCode: 'JPY', currencySymbol: '¥', currencyName: 'Japanese Yen' },
  { countryCode: 'CA', currencyCode: 'CAD', currencySymbol: 'CA$', currencyName: 'Canadian Dollar' },
  { countryCode: 'AU', currencyCode: 'AUD', currencySymbol: 'A$', currencyName: 'Australian Dollar' },
  { countryCode: 'SG', currencyCode: 'SGD', currencySymbol: 'S$', currencyName: 'Singapore Dollar' },
  { countryCode: 'AE', currencyCode: 'AED', currencySymbol: 'د.إ', currencyName: 'UAE Dirham' },
  { countryCode: 'CH', currencyCode: 'CHF', currencySymbol: 'CHF', currencyName: 'Swiss Franc' },
]

function Register() {
  const { t, i18n } = useTranslation()
  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    country: 'US',
    preferredCurrency: 'USD'
  })
  const [availableCurrencies, setAvailableCurrencies] = useState(DEFAULT_CURRENCIES)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    api.get('/currencies')
      .then(res => {
        if (Array.isArray(res.data) && res.data.length > 0) {
          setAvailableCurrencies(res.data)
        }
      })
      .catch(() => {
        // Fallback to static defaults
      })
  }, [])

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value })
    setError('')
  }

  const handleCurrencyChange = (e) => {
    const selectedCurr = availableCurrencies.find(c => c.currencyCode === e.target.value)
    setForm({
      ...form,
      preferredCurrency: e.target.value,
      country: selectedCurr ? selectedCurr.countryCode : form.country
    })
    setError('')
  }

  const validate = () => {
    if (!form.username.trim()) return 'Username is required.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return 'Please enter a valid email address (e.g. name@example.com).'
    if (form.password.length < 6) return 'Password must be at least 6 characters long.'
    return null
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const validationError = validate()
    if (validationError) { 
      setError(validationError)
      return 
    }

    setLoading(true)
    setError('')
    try {
      const payload = {
        ...form,
        preferredLanguage: i18n.language || 'en'
      }
      const res = await api.post('/auth/register', payload)
      login(res.data.token, {
        username: res.data.username,
        email: res.data.email,
        country: res.data.country || form.country || 'US',
        preferredCurrency: res.data.preferredCurrency || form.preferredCurrency || 'USD',
        preferredLanguage: res.data.preferredLanguage || i18n.language || 'en'
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      const serverError = err.response?.data?.message
      const errorMsg = serverError || (err.message === 'Network Error' ? 'Network Error: Backend unreachable on port 8080.' : t('auth.registrationFailed'))
      setError(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card" role="main">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div className="auth-logo" style={{ marginBottom: 0 }}>
            <div className="logo-badge">
              <Wallet size={20} strokeWidth={2} />
            </div>
            <span className="logo-title">{t('common.brand')}</span>
          </div>
          <LanguagePicker variant="auth" id="register-language-picker" />
        </div>

        <h1 className="auth-title">{t('auth.registerTitle')}</h1>
        <p className="auth-subtitle">{t('auth.registerSubtitle')}</p>

        {error && (
          <div className="alert alert-error" role="alert" id="register-error">
            <AlertTriangle size={15} />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form" noValidate>
          <div className="form-group">
            <label htmlFor="reg-username">{t('auth.username')}</label>
            <input
              id="reg-username"
              name="username"
              type="text"
              value={form.username}
              onChange={handleChange}
              placeholder={t('auth.usernamePlaceholder')}
              autoComplete="username"
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label htmlFor="reg-email">{t('auth.email')}</label>
            <input
              id="reg-email"
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              placeholder={t('auth.emailPlaceholder')}
              autoComplete="email"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="reg-password">{t('auth.password')}</label>
            <input
              id="reg-password"
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder={t('auth.passwordPlaceholder')}
              autoComplete="new-password"
              required
              minLength={6}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label htmlFor="reg-currency">{t('auth.preferredCurrency')}</label>
              <select
                id="reg-currency"
                name="preferredCurrency"
                value={form.preferredCurrency}
                onChange={handleCurrencyChange}
                className="form-select"
              >
                {availableCurrencies.map(c => (
                  <option key={c.currencyCode} value={c.currencyCode}>
                    {c.currencyCode} ({c.currencySymbol}) - {c.currencyName || c.countryCode}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="reg-country">{t('auth.country')}</label>
              <input
                id="reg-country"
                name="country"
                type="text"
                maxLength={4}
                value={form.country}
                onChange={handleChange}
                placeholder={t('auth.countryPlaceholder')}
                className="input-field"
                style={{ textTransform: 'uppercase' }}
              />
            </div>
          </div>

          <button
            id="register-submit-btn"
            type="submit"
            className="btn-primary"
            disabled={loading}
            style={{ marginTop: '8px' }}
          >
            {loading ? <span className="spinner" /> : t('auth.registerButton')}
          </button>
        </form>

        <p className="auth-footer">
          {t('auth.alreadyHaveAccount')}{' '}
          <Link to="/login" id="go-to-login">{t('auth.signIn')}</Link>
        </p>
      </div>
    </div>
  )
}

export default Register

