import { create } from 'zustand'

interface AuthState {
  token: string | null
  isAuthenticated: boolean
  setToken: (token: string) => void
  logout: () => void
}

const TOKEN_KEY = 'ai_blog_token'

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem(TOKEN_KEY),
  isAuthenticated: !!localStorage.getItem(TOKEN_KEY),
  setToken: (token) => {
    localStorage.setItem(TOKEN_KEY, token)
    set({ token, isAuthenticated: true })
  },
  logout: () => {
    localStorage.removeItem(TOKEN_KEY)
    set({ token: null, isAuthenticated: false })
  },
}))
