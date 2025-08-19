import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import axios from 'axios'

interface AuthState {
  isAuthenticated: boolean
  isLoading: boolean
  token: string | null
  username: string | null
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  checkAuth: () => Promise<void>
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      isAuthenticated: false,
      isLoading: false,
      token: null,
      username: null,
      
      login: async (username: string, password: string) => {
        try {
          const response = await axios.post('/auth/login', { username, password })
          const { success, token, message } = response.data
          
          // 检查登录是否成功
          if (!success || !token) {
            throw new Error(message || '登录失败')
          }
          
          // 设置 axios 默认 header
          axios.defaults.headers.common['Authorization'] = `Bearer ${token}`
          
          set({
            isAuthenticated: true,
            token,
            username,
          })
        } catch (error) {
          throw error
        }
      },
      
      logout: async () => {
        try {
          await axios.post('/auth/logout')
        } catch (error) {
          console.error('Logout error:', error)
        } finally {
          delete axios.defaults.headers.common['Authorization']
          set({
            isAuthenticated: false,
            token: null,
            username: null,
          })
        }
      },
      
      checkAuth: async () => {
        set({ isLoading: true })
        
        // 从localStorage获取持久化的token
        const storedData = localStorage.getItem('auth-storage')
        let token = null
        if (storedData) {
          try {
            const parsed = JSON.parse(storedData)
            token = parsed.state?.token
          } catch (e) {
            console.error('Failed to parse auth storage:', e)
          }
        }
        
        if (!token) {
          set({ isAuthenticated: false, isLoading: false })
          return
        }
        
        try {
          axios.defaults.headers.common['Authorization'] = `Bearer ${token}`
          const response = await axios.get('/auth/check')
          const { authenticated } = response.data
          
          if (authenticated) {
            set({ isAuthenticated: true, isLoading: false, token })
          } else {
            // Token无效，清除认证状态
            delete axios.defaults.headers.common['Authorization']
            localStorage.removeItem('auth-storage')
            set({
              isAuthenticated: false,
              isLoading: false,
              token: null,
              username: null,
            })
          }
        } catch (error) {
          delete axios.defaults.headers.common['Authorization']
          localStorage.removeItem('auth-storage')
          set({
            isAuthenticated: false,
            isLoading: false,
            token: null,
            username: null,
          })
        }
      },
    }),
    {
      name: 'auth-storage',
    }
  )
)