import { Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'

function ProtectedRoute({ children }) {
  const { t } = useTranslation()
  const { isAuthenticated, loading } = useAuth()
  const hasStoredToken = !!localStorage.getItem('finance_token')

  if (loading) {
    return (
      <div className="loading-screen">
        <span className="spinner" />
        <span>{t('common.loading')}</span>
      </div>
    )
  }

  return (isAuthenticated || hasStoredToken) ? children : <Navigate to="/login" replace />
}

export default ProtectedRoute
