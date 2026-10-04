import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Wallet, AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'
import LanguagePicker from '../components/LanguagePicker'

function Login() {
  const { t } = useTranslation()
  const [form,    setForm]    = useState({ username: '', password: '' })
  const [error,   setError]   = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate  = useNavigate()

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value })
    setError('')
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await api.post('/auth/login', form)
      login(res.data.token, {
        username: res.data.username,
        email: res.data.email,
        country: res.data.country,
        preferredCurrency: res.data.preferredCurrency,
        preferredLanguage: res.data.preferredLanguage
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      const msg = err.response?.data?.message || t('auth.loginFailed')
      setError(msg)
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
          <LanguagePicker variant="auth" id="login-language-picker" />
        </div>

        <h1 className="auth-title">{t('auth.welcomeBack')}</h1>
        <p className="auth-subtitle">{t('auth.signInSubtitle')}</p>

        {error && (
          <div className="alert alert-error" role="alert" id="login-error">
            <AlertTriangle size={15} />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form" noValidate>
          <div className="form-group">
            <label htmlFor="login-username">{t('auth.username')}</label>
            <input
              id="login-username"
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
            <label htmlFor="login-password">{t('auth.password')}</label>
            <input
              id="login-password"
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder={t('auth.passwordPlaceholder')}
              autoComplete="current-password"
              required
            />
          </div>

          <button
            id="login-submit-btn"
            type="submit"
            className="btn-primary"
            disabled={loading || !form.username || !form.password}
            style={{ marginTop: '8px' }}
          >
            {loading ? <span className="spinner" /> : t('auth.signIn')}
          </button>
        </form>

        <p className="auth-footer">
          {t('auth.dontHaveAccount')}{' '}
          <Link to="/register" id="go-to-register">{t('auth.createAccount')}</Link>
        </p>
      </div>
    </div>
  )
}

export default Login

