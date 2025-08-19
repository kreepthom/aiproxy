import React, { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import { useAuthStore } from './store/authStore'
import axios from 'axios'
import { API_BASE_URL } from './config/api-simple'

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuthStore((state) => ({ 
    isAuthenticated: state.isAuthenticated, 
    isLoading: state.isLoading 
  }))
  
  // 正在检查认证状态时显示加载
  if (isLoading) {
    return <div style={{ padding: '20px', textAlign: 'center' }}>检查登录状态...</div>
  }
  
  if (!isAuthenticated) {
    return <Navigate to="/login" />
  }
  
  return <>{children}</>
}

const App: React.FC = () => {
  const { token, checkAuth } = useAuthStore((state) => ({ 
    token: state.token, 
    checkAuth: state.checkAuth 
  }))
  
  useEffect(() => {
    // 设置 axios 默认配置
    axios.defaults.baseURL = API_BASE_URL
    
    // 从localStorage恢复token
    const storedData = localStorage.getItem('auth-storage')
    if (storedData) {
      try {
        const parsed = JSON.parse(storedData)
        const storedToken = parsed.state?.token
        if (storedToken) {
          axios.defaults.headers.common['Authorization'] = `Bearer ${storedToken}`
        }
      } catch (e) {
        console.error('Failed to parse auth storage:', e)
      }
    }
    
    // 应用启动时检查认证状态
    checkAuth()
  }, [checkAuth])
  
  useEffect(() => {
    // token变化时更新 axios 默认 header
    if (token) {
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`
    } else {
      delete axios.defaults.headers.common['Authorization']
    }
  }, [token])
  
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/dashboard/*"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route path="/" element={<Navigate to="/dashboard" />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App