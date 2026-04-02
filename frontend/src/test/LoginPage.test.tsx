import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage/LoginPage'
import { useAuthStore } from '../store/authStore'

const TOKEN_KEY = 'ai_blog_token'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ token: null, isAuthenticated: false })
    mockNavigate.mockClear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('GitHub 로그인 버튼이 렌더링된다', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    expect(screen.getByText('GitHub으로 로그인')).toBeInTheDocument()
  })

  it('DEV 환경에서 Mock 로그인 버튼이 렌더링된다', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    expect(screen.getByText('[개발] Mock 로그인')).toBeInTheDocument()
  })

  it('Mock 로그인 성공 시 token을 localStorage에 저장하고 /로 이동한다', async () => {
    const fakeToken = 'mock.jwt.token'
    global.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve({ data: { token: fakeToken }, success: true }),
    }) as ReturnType<typeof vi.fn>

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    fireEvent.click(screen.getByText('[개발] Mock 로그인'))

    await waitFor(() => {
      expect(localStorage.getItem(TOKEN_KEY)).toBe(fakeToken)
      expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
    })
  })

  it('Mock 로그인 응답에 token이 없으면 navigate를 호출하지 않는다', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve({ data: {}, success: false }),
    }) as ReturnType<typeof vi.fn>

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    fireEvent.click(screen.getByText('[개발] Mock 로그인'))

    await waitFor(() => {
      expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })
})
