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
  hasGithubClientId: boolean
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
  githubClientId?: string
  githubClientSecret?: string
}
