export interface AiSuggestion {
  id: number
  postId: number
  suggestedContent: string
  model: string
  extraPrompt?: string
  createdAt: string
}

export interface AiSuggestionRequest {
  model?: string
  extraPrompt?: string
  tempContent?: string
}
