export interface Prompt {
  id: number
  memberId: number
  title: string
  content: string
  usageCount: number
  isPublic: boolean
  createdAt: string
  updatedAt: string
}

export interface PromptRequest {
  title: string
  content: string
  isPublic: boolean
}
