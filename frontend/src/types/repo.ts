export type CollectType = 'COMMIT' | 'PR' | 'WIKI' | 'README'

export interface Repo {
  id: number
  owner: string
  repoName: string
  collectType: CollectType
  createdAt: string
}

export interface RepoAddRequest {
  owner: string
  repoName: string
  collectType: CollectType
}

export interface PrSummary {
  number: number
  title: string
  hasBlogLabel: boolean
  alreadyCollected: boolean
}
