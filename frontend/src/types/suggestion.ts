export interface AiSuggestion {
  id: number
  postId: number
  suggestedContent: string
  suggestedTitle?: string
  suggestedTags?: string
  model: string
  extraPrompt?: string
  createdAt: string
}

export interface AiSuggestionRequest {
  model?: string
  extraPrompt?: string
  tempContent?: string
  promptId?: number
}
