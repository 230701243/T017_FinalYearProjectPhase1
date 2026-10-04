import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/"          element={<Navigate to="/dashboard" replace />} />
          <Route path="/login"     element={<Login />} />
          <Route path="/register"  element={<Register />} />
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />
          <Route path="/ai-assistant" element={
            <ProtectedRoute>
              <Dashboard initialNav="assistant" />
            </ProtectedRoute>
          } />
          <Route path="/assistant" element={<Navigate to="/ai-assistant" replace />} />
          {/* Catch-all → login */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
