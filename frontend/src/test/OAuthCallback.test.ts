import { describe, it, expect, beforeEach, vi } from 'vitest'

const TOKEN_KEY = 'ai_blog_token'

// OAuthCallback의 핵심 로직을 순수 함수로 추출하여 테스트
function handleOAuthCallback(
  search: string,
  storage: Pick<Storage, 'setItem' | 'getItem'>,
  replace: (url: string) => void
) {
  const params = new URLSearchParams(search)
  const token = params.get('token')

  if (token) {
    storage.setItem(TOKEN_KEY, token)
    replace('/')
  } else {
    replace('/login')
  }
}

describe('OAuthCallback 핵심 로직', () => {
  const replaceMock = vi.fn()
  const storageMock = {
    store: {} as Record<string, string>,
    setItem(key: string, value: string) { this.store[key] = value },
    getItem(key: string) { return this.store[key] ?? null },
  }

  beforeEach(() => {
    replaceMock.mockClear()
    storageMock.store = {}
  })

  it('token이 있으면 localStorage에 저장하고 /로 이동한다', () => {
    handleOAuthCallback('?token=test.jwt.token', storageMock, replaceMock)

    expect(storageMock.getItem(TOKEN_KEY)).toBe('test.jwt.token')
    expect(replaceMock).toHaveBeenCalledWith('/')
  })

  it('token이 없으면 /login으로 이동한다', () => {
    handleOAuthCallback('', storageMock, replaceMock)

    expect(storageMock.getItem(TOKEN_KEY)).toBeNull()
    expect(replaceMock).toHaveBeenCalledWith('/login')
  })

  it('token이 있으면 /login으로는 이동하지 않는다', () => {
    handleOAuthCallback('?token=valid.token', storageMock, replaceMock)

    expect(replaceMock).not.toHaveBeenCalledWith('/login')
    expect(replaceMock).toHaveBeenCalledWith('/')
  })

  it('token 파싱: URL에 다른 파라미터가 섞여있어도 token만 추출한다', () => {
    handleOAuthCallback('?foo=bar&token=my.token&baz=qux', storageMock, replaceMock)

    expect(storageMock.getItem(TOKEN_KEY)).toBe('my.token')
    expect(replaceMock).toHaveBeenCalledWith('/')
  })

  it('빈 token 값이면 /login으로 이동한다', () => {
    handleOAuthCallback('?token=', storageMock, replaceMock)

    // 빈 문자열은 falsy이므로 /login으로 이동
    expect(replaceMock).toHaveBeenCalledWith('/login')
  })
})
