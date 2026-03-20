export type PostStatus = 'DRAFT' | 'AI_SUGGESTED' | 'ACCEPTED' | 'PUBLISHED'
export type ContentType = 'ALGORITHM' | 'CODING' | 'CS' | 'TEST' | 'AUTOMATION' | 'DOCUMENT' | 'CODE_REVIEW' | 'ETC'

export const CONTENT_TYPE_LABEL: Record<ContentType, string> = {
  ALGORITHM: '알고리즘',
  CODING: '코딩',
  CS: 'CS',
  TEST: '테스트',
  AUTOMATION: '자동화',
  DOCUMENT: '문서',
  CODE_REVIEW: '코드 리뷰',
  ETC: '기타',
}

export interface Post {
  id: number
  title: string
  content: string
  contentType: ContentType
  status: PostStatus
  hashnodeId?: string
  hashnodeUrl?: string
  tags: string[]
  viewCount: number
  createdAt: string
  updatedAt: string
}

export interface PostListItem {
  id: number
  title: string
  contentType: ContentType
  status: PostStatus
  tags: string[]
  viewCount: number
  createdAt: string
}

export interface PostPage {
  content: PostListItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface AiUsage {
  // 전체 호출 횟수
  used: number
  limit: number
  remaining: number
  // Claude Sonnet 누적 토큰
  sonnetInputTokens: number
  sonnetOutputTokens: number
  // Claude API 실시간 Rate Limit (-1 = 미조회)
  claudeTokenLimit: number
  claudeTokenRemaining: number
  claudeRequestLimit: number
  claudeRequestRemaining: number
  // GPT 이미지 (일별)
  imageDailyUsed: number
  imageDailyLimit: number
  imageDailyRemaining: number
  imagePerPostLimit: number
  // Grok 누적 토큰
  grokInputTokens: number
  grokOutputTokens: number
  // Grok API 실시간 Rate Limit (-1 = 미조회)
  grokTokenLimit: number
  grokTokenRemaining: number
  grokRequestLimit: number
  grokRequestRemaining: number
}
