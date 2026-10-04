import { createContext, useContext, useState, useEffect } from 'react'
import api from '../api/axios'
import { loadLanguage } from '../i18n'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user,    setUser]    = useState(null)
  const [token,   setToken]   = useState(null)
  const [loading, setLoading] = useState(true)

  // Rehydrate from localStorage and sync with backend on first render
  useEffect(() => {
    const rehydrate = async () => {
      const storedToken = localStorage.getItem('finance_token')
      const storedUser  = localStorage.getItem('finance_user')
      if (storedToken) {
        setToken(storedToken)
        let parsed = null
        if (storedUser) {
          try {
            parsed = JSON.parse(storedUser)
            setUser({
              ...parsed,
              country: parsed.country || 'US',
              preferredCurrency: parsed.preferredCurrency || 'USD',
              preferredLanguage: parsed.preferredLanguage || localStorage.getItem('finance_language') || 'en'
            })
          } catch (e) {
            console.error('Failed to parse cached user', e)
          }
        }
        try {
          // Read preferredCurrency and preferredLanguage directly from server
          const res = await api.get('/user/profile', {
            headers: { Authorization: `Bearer ${storedToken}` }
          })
          if (res.data) {
            const serverUser = {
              ...(parsed || {}),
              username: res.data.username,
              email: res.data.email,
              country: res.data.country || 'US',
              preferredCurrency: res.data.preferredCurrency || 'USD',
              preferredLanguage: res.data.preferredLanguage || 'en'
            }
            setUser(serverUser)
            localStorage.setItem('finance_user', JSON.stringify(serverUser))
            if (res.data.preferredLanguage) {
              loadLanguage(res.data.preferredLanguage)
            }
          }
        } catch (err) {
          console.error('Failed to fetch user profile on app load', err)
          if (err.response?.status === 401 || err.response?.status === 403) {
            localStorage.removeItem('finance_token')
            localStorage.removeItem('finance_user')
            setToken(null)
            setUser(null)
          }
        }
      }
      setLoading(false)
    }

    rehydrate()
  }, [])

  const login = (newToken, userData) => {
    const lang = userData.preferredLanguage || localStorage.getItem('finance_language') || 'en'
    const userWithDefaults = {
      ...userData,
      country: userData.country || 'US',
      preferredCurrency: userData.preferredCurrency || 'USD',
      preferredLanguage: lang
    }
    localStorage.setItem('finance_token', newToken)
    localStorage.setItem('finance_user',  JSON.stringify(userWithDefaults))
    setToken(newToken)
    setUser(userWithDefaults)
    if (userWithDefaults.preferredLanguage) {
      loadLanguage(userWithDefaults.preferredLanguage)
    }
  }

  const updateUser = (updatedData) => {
    setUser(prev => {
      const merged = { ...prev, ...updatedData }
      localStorage.setItem('finance_user', JSON.stringify(merged))
      return merged
    })
  }

  const logout = () => {
    localStorage.removeItem('finance_token')
    localStorage.removeItem('finance_user')
    setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, token, login, updateUser, logout, loading, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
