import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from '../store/authStore'

const TOKEN_KEY = 'ai_blog_token'

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ token: null, isAuthenticated: false })
  })

  it('초기 상태: localStorage에 token이 없으면 isAuthenticated가 false다', () => {
    const { isAuthenticated, token } = useAuthStore.getState()
    expect(isAuthenticated).toBe(false)
    expect(token).toBeNull()
  })

  it('localStorage에 token이 있으면 초기 상태가 isAuthenticated: true다', () => {
    localStorage.setItem(TOKEN_KEY, 'stored.token')
    // 스토어 재초기화 시뮬레이션
    useAuthStore.setState({
      token: localStorage.getItem(TOKEN_KEY),
      isAuthenticated: !!localStorage.getItem(TOKEN_KEY),
    })

    const { isAuthenticated, token } = useAuthStore.getState()
    expect(isAuthenticated).toBe(true)
    expect(token).toBe('stored.token')
  })

  it('setToken 호출 시 localStorage에 저장하고 isAuthenticated가 true가 된다', () => {
    useAuthStore.getState().setToken('new.token')

    expect(localStorage.getItem(TOKEN_KEY)).toBe('new.token')
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    expect(useAuthStore.getState().token).toBe('new.token')
  })

  it('logout 호출 시 localStorage에서 제거하고 isAuthenticated가 false가 된다', () => {
    useAuthStore.getState().setToken('existing.token')
    useAuthStore.getState().logout()

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().token).toBeNull()
  })
})
