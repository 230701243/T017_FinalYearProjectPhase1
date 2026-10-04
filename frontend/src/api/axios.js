import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT token on every request if present
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('finance_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Intercept 401/403 ONLY for protected endpoints (NOT for /auth/* login/register)
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status
    const isAuthEndpoint = err.config?.url?.includes('/auth/')

    // If a protected request fails with 401/403, redirect to login
    if ((status === 401 || status === 403) && !isAuthEndpoint) {
      localStorage.removeItem('finance_token')
      localStorage.removeItem('finance_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  }
)

export default api
