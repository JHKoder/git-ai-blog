export interface Member {
  id: number
  username: string
  avatarUrl?: string
  hasHashnodeConnection: boolean
  hasClaudeApiKey: boolean
  hasGrokApiKey: boolean
  hasGptApiKey: boolean
  hasGeminiApiKey: boolean
  hasGithubToken: boolean
  aiDailyLimit: number | null
  claudeDailyLimit: number | null
  grokDailyLimit: number | null
  gptDailyLimit: number | null
  geminiDailyLimit: number | null
}

export interface HashnodeConnectRequest {
  token: string
  publicationId: string
}

export interface ApiKeyUpdateRequest {
  claudeApiKey?: string
  grokApiKey?: string
  gptApiKey?: string
  geminiApiKey?: string
  githubToken?: string
  aiDailyLimit?: number
  claudeDailyLimit?: number
  grokDailyLimit?: number
  gptDailyLimit?: number
  geminiDailyLimit?: number
}
