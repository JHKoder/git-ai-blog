import { useState, useEffect } from 'react'
import toast from 'react-hot-toast'
import { memberApi } from '../../api/memberApi'
import { postApi } from '../../api/postApi'
import { Member, ApiKeyUpdateRequest } from '../../types/member'
import { AiUsage } from '../../types/post'
import styles from './ProfilePage.module.css'

function RateLimitBar({ used, limit, color }: { used: number; limit: number; color: string }) {
  if (limit <= 0) return null
  return (
    <div className={styles.progressBar}>
      <div className={styles.progressFill} style={{ width: `${Math.min(100, (used / limit) * 100)}%`, background: color }} />
    </div>
  )
}

export function ProfilePage() {
  const [member, setMember] = useState<Member | null>(null)
  const [aiUsage, setAiUsage] = useState<AiUsage | null>(null)
  const [hashnodeToken, setHashnodeToken] = useState('')
  const [publicationId, setPublicationId] = useState('')
  const [claudeKey, setClaudeKey] = useState('')
  const [grokKey, setGrokKey] = useState('')
  const [gptKey, setGptKey] = useState('')
  const [geminiKey, setGeminiKey] = useState('')
  const [githubToken, setGithubToken] = useState('')
  const [aiDailyLimit, setAiDailyLimit] = useState('')
  const [claudeDailyLimit, setClaudeDailyLimit] = useState('')
  const [grokDailyLimit, setGrokDailyLimit] = useState('')
  const [gptDailyLimit, setGptDailyLimit] = useState('')
  const [geminiDailyLimit, setGeminiDailyLimit] = useState('')
  const [connectingHashnode, setConnectingHashnode] = useState(false)
  const [syncing, setSyncing] = useState(false)

  useEffect(() => {
    memberApi.getMe().then(res => setMember(res.data.data))
    memberApi.getAiUsage().then(res => setAiUsage(res.data.data)).catch(() => {})
  }, [])

  const handleHashnodeConnect = async (e: React.FormEvent) => {
    e.preventDefault()
    setConnectingHashnode(true)
    try {
      const res = await memberApi.connectHashnode({ token: hashnodeToken, publicationId })
      setMember(res.data.data)
      setHashnodeToken(''); setPublicationId('')
      toast.success('Hashnode 연동 완료.')
    } catch {
      toast.error('연동 실패')
    } finally {
      setConnectingHashnode(false)
    }
  }

  const handleHashnodeSync = async () => {
    setSyncing(true)
    try {
      const res = await postApi.syncHashnode()
      const { added, updated, deleted } = res.data.data
      toast.success(`동기화 완료 — 추가 ${added}, 수정 ${updated}, 삭제 ${deleted}`)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toast.error(err.response?.data?.message || '동기화 실패')
    } finally {
      setSyncing(false)
    }
  }

  const handleHashnodeDisconnect = async () => {
    try {
      await memberApi.disconnectHashnode()
      setMember(prev => prev ? { ...prev, hasHashnodeConnection: false } : prev)
      toast.success('연동이 해제됐습니다.')
    } catch {
      toast.error('해제 실패')
    }
  }

  const handleApiKeys = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const data: ApiKeyUpdateRequest = {}
      if (claudeKey) data.claudeApiKey = claudeKey
      if (grokKey) data.grokApiKey = grokKey
      if (gptKey) data.gptApiKey = gptKey
      if (geminiKey) data.geminiApiKey = geminiKey
      if (githubToken) data.githubToken = githubToken
      if (aiDailyLimit) data.aiDailyLimit = parseInt(aiDailyLimit, 10)
      if (claudeDailyLimit) data.claudeDailyLimit = parseInt(claudeDailyLimit, 10)
      if (grokDailyLimit) data.grokDailyLimit = parseInt(grokDailyLimit, 10)
      if (gptDailyLimit) data.gptDailyLimit = parseInt(gptDailyLimit, 10)
      if (geminiDailyLimit) data.geminiDailyLimit = parseInt(geminiDailyLimit, 10)
      const res = await memberApi.updateApiKeys(data)
      setMember(res.data.data)
      setClaudeKey(''); setGrokKey(''); setGptKey(''); setGeminiKey('')
      setGithubToken(''); setAiDailyLimit('')
      setClaudeDailyLimit(''); setGrokDailyLimit(''); setGptDailyLimit(''); setGeminiDailyLimit('')
      toast.success('설정이 저장됐습니다.')
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toast.error(err.response?.data?.message || '저장 실패')
    }
  }

  if (!member) return <p className={styles.loading}>로딩 중...</p>

  const claudeKnown = aiUsage && aiUsage.claudeTokenLimit >= 0
  const grokKnown   = aiUsage && aiUsage.grokTokenLimit >= 0

  return (
    <div className={styles.container}>
      <h2 className={styles.pageTitle}>마이페이지</h2>

      {/* 기본 정보 */}
      <div className={styles.section}>
        <h3>기본 정보</h3>
        <div className={styles.profile}>
          {member.avatarUrl && <img src={member.avatarUrl} alt="avatar" className={styles.avatar} />}
          <span className={styles.username}>{member.username}</span>
        </div>
      </div>

      {/* Hashnode 연동 */}
      <div className={styles.section}>
        <h3>Hashnode 연동</h3>
        {member.hasHashnodeConnection ? (
          <div className={styles.connectedRow}>
            <span className={styles.connected}>✓ 연동됨</span>
            <p className={styles.hint}>Hashnode 블로그와 게시글을 동기화합니다.</p>
            <div className={styles.connectedActions}>
              <button className={styles.syncBtn} onClick={handleHashnodeSync} disabled={syncing}>
                {syncing ? '동기화 중...' : '게시글 동기화'}
              </button>
              <button className={styles.disconnectBtn} onClick={handleHashnodeDisconnect}>연동 해제</button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleHashnodeConnect} className={styles.form}>
            <p className={styles.hint}>연동 시 Hashnode 블로그 글을 자동으로 가져옵니다.</p>
            <div className={styles.field}>
              <label>Hashnode API Token</label>
              <input type="password" value={hashnodeToken} onChange={e => setHashnodeToken(e.target.value)}
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" required className={styles.input} />
            </div>
            <div className={styles.field}>
              <label>Publication ID</label>
              <input value={publicationId} onChange={e => setPublicationId(e.target.value)}
                placeholder="64a1b2c3d4e5f6..." required className={styles.input} />
            </div>
            <button type="submit" className={styles.saveBtn} disabled={connectingHashnode}>
              {connectingHashnode ? '연동 중...' : '연동하기'}
            </button>
          </form>
        )}
      </div>

      {/* AI 사용량 대시보드 */}
      {aiUsage && (
        <div className={styles.section}>
          <h3>AI 사용량 대시보드</h3>
          <p className={styles.dashNote}>
            잔여 토큰은 AI 호출 응답 헤더에서 실시간으로 가져옵니다. AI를 한 번 호출하면 값이 채워집니다.
          </p>
          <div className={styles.dashboardGrid}>

            {/* Claude Sonnet */}
            <div className={styles.dashCard}>
              <div className={styles.dashCardHeader}>
                <span className={styles.modelBadgeSonnet}>Claude Sonnet 4.6</span>
                <span className={styles.modelRole}>텍스트</span>
              </div>
              <div className={styles.tokenRow}>
                <div className={styles.tokenBlock}>
                  <span className={styles.tokenNum}>{aiUsage.sonnetInputTokens.toLocaleString()}</span>
                  <span className={styles.tokenLabel}>입력</span>
                </div>
                <div className={styles.tokenDivider} />
                <div className={styles.tokenBlock}>
                  <span className={styles.tokenNum}>{aiUsage.sonnetOutputTokens.toLocaleString()}</span>
                  <span className={styles.tokenLabel}>출력</span>
                </div>
                <div className={styles.tokenDivider} />
                <div className={styles.tokenBlock}>
                  <span className={styles.tokenNumTotal}>{(aiUsage.sonnetInputTokens + aiUsage.sonnetOutputTokens).toLocaleString()}</span>
                  <span className={styles.tokenLabel}>이번 세션 합계</span>
                </div>
              </div>
              <div className={styles.dashStat}>
                <span className={styles.dashLabel}>오늘 호출</span>
                <span className={styles.dashValue}>{aiUsage.used} / {aiUsage.limit}회</span>
              </div>
              <RateLimitBar used={aiUsage.used} limit={aiUsage.limit} color="#3b82f6" />
              {claudeKnown ? (
                <>
                  <div className={styles.dashStat} style={{ marginTop: 8 }}>
                    <span className={styles.dashLabel}>분당 토큰 잔여</span>
                    <span className={styles.dashValue}>
                      {aiUsage.claudeTokenRemaining.toLocaleString()} / {aiUsage.claudeTokenLimit.toLocaleString()}
                    </span>
                  </div>
                  <RateLimitBar
                    used={aiUsage.claudeTokenLimit - aiUsage.claudeTokenRemaining}
                    limit={aiUsage.claudeTokenLimit}
                    color="#3b82f6"
                  />
                  <div className={styles.dashStat} style={{ marginTop: 4 }}>
                    <span className={styles.dashLabel}>분당 요청 잔여</span>
                    <span className={styles.dashValue}>
                      {aiUsage.claudeRequestRemaining} / {aiUsage.claudeRequestLimit}회
                    </span>
                  </div>
                </>
              ) : (
                <span className={styles.limitHint}>AI 개선 요청을 1회 하면 잔여 토큰이 표시됩니다</span>
              )}
            </div>

            {/* Claude Opus - 이미지 */}
            <div className={styles.dashCard}>
              <div className={styles.dashCardHeader}>
                <span className={styles.modelBadgeOpus}>Claude Opus 4.5</span>
                <span className={styles.modelRole}>이미지</span>
              </div>
              <div className={styles.dashStat}>
                <span className={styles.dashLabel}>오늘 생성</span>
                <span className={styles.dashValue}>{aiUsage.imageDailyUsed} / {aiUsage.imageDailyLimit}장</span>
              </div>
              <RateLimitBar used={aiUsage.imageDailyUsed} limit={aiUsage.imageDailyLimit} color="#7c3aed" />
              <span className={styles.dashRemain}>게시글당 최대 {aiUsage.imagePerPostLimit}장 · 오늘 {aiUsage.imageDailyRemaining}장 남음</span>
            </div>

            {/* Grok */}
            <div className={styles.dashCard}>
              <div className={styles.dashCardHeader}>
                <span className={styles.modelBadgeGrok}>Grok 3</span>
                <span className={styles.modelRole}>텍스트</span>
              </div>
              <div className={styles.tokenRow}>
                <div className={styles.tokenBlock}>
                  <span className={styles.tokenNum}>{aiUsage.grokInputTokens.toLocaleString()}</span>
                  <span className={styles.tokenLabel}>입력</span>
                </div>
                <div className={styles.tokenDivider} />
                <div className={styles.tokenBlock}>
                  <span className={styles.tokenNum}>{aiUsage.grokOutputTokens.toLocaleString()}</span>
                  <span className={styles.tokenLabel}>출력</span>
                </div>
                <div className={styles.tokenDivider} />
                <div className={styles.tokenBlock}>
                  <span className={styles.tokenNumTotal}>{(aiUsage.grokInputTokens + aiUsage.grokOutputTokens).toLocaleString()}</span>
                  <span className={styles.tokenLabel}>이번 세션 합계</span>
                </div>
              </div>
              {grokKnown ? (
                <>
                  <div className={styles.dashStat} style={{ marginTop: 8 }}>
                    <span className={styles.dashLabel}>분당 토큰 잔여</span>
                    <span className={styles.dashValue}>
                      {aiUsage.grokTokenRemaining.toLocaleString()} / {aiUsage.grokTokenLimit.toLocaleString()}
                    </span>
                  </div>
                  <RateLimitBar
                    used={aiUsage.grokTokenLimit - aiUsage.grokTokenRemaining}
                    limit={aiUsage.grokTokenLimit}
                    color="#d97706"
                  />
                  <div className={styles.dashStat} style={{ marginTop: 4 }}>
                    <span className={styles.dashLabel}>분당 요청 잔여</span>
                    <span className={styles.dashValue}>
                      {aiUsage.grokRequestRemaining} / {aiUsage.grokRequestLimit}회
                    </span>
                  </div>
                </>
              ) : (
                <span className={styles.limitHint}>알고리즘 게시글로 AI 개선 시 잔여 토큰이 표시됩니다</span>
              )}
            </div>

          </div>
        </div>
      )}

      {/* 연동 설정 */}
      <div className={styles.section}>
        <h3>연동 설정</h3>
        <form onSubmit={handleApiKeys} className={styles.form}>
          <div className={styles.keyGroup}>
            <p className={styles.groupLabel}>GitHub</p>
            <div className={styles.field}>
              <label>Personal Access Token (데이터 수집용) {member.hasGithubToken && <span className={styles.set}>✓ 설정됨</span>}</label>
              <input type="password" value={githubToken} onChange={e => setGithubToken(e.target.value)}
                placeholder="ghp_xxxxxxxxxxxx" className={styles.input} />
            </div>
          </div>

          <div className={styles.keyGroup}>
            <p className={styles.groupLabel}>AI API 키 <span className={styles.optional}>(선택 — 미설정 시 서버 공통 키 사용)</span></p>
            <div className={styles.field}>
              <label>Claude API 키 {member.hasClaudeApiKey && <span className={styles.set}>✓ 설정됨</span>}</label>
              <input type="password" value={claudeKey} onChange={e => setClaudeKey(e.target.value)}
                placeholder="sk-ant-..." className={styles.input} />
            </div>
            <div className={styles.field}>
              <label>Grok API 키 {member.hasGrokApiKey && <span className={styles.set}>✓ 설정됨</span>}</label>
              <input type="password" value={grokKey} onChange={e => setGrokKey(e.target.value)}
                placeholder="xai-..." className={styles.input} />
            </div>
            <div className={styles.field}>
              <label>GPT API 키 (OpenAI) {member.hasGptApiKey && <span className={styles.set}>✓ 설정됨</span>}</label>
              <input type="password" value={gptKey} onChange={e => setGptKey(e.target.value)}
                placeholder="sk-..." className={styles.input} />
            </div>
            <div className={styles.field}>
              <label>Gemini API 키 (Google) {member.hasGeminiApiKey && <span className={styles.set}>✓ 설정됨</span>}</label>
              <input type="password" value={geminiKey} onChange={e => setGeminiKey(e.target.value)}
                placeholder="AIza..." className={styles.input} />
            </div>
          </div>

          <div className={styles.keyGroup}>
            <p className={styles.groupLabel}>AI 사용량 설정</p>
            <div className={styles.field}>
              <label>
                전체 일일 AI 호출 한도 <span className={styles.optional}>(현재: {member.aiDailyLimit != null ? `${member.aiDailyLimit}회` : '서버 기본값'})</span>
              </label>
              <input
                type="number"
                min={1}
                max={1000}
                value={aiDailyLimit}
                onChange={e => setAiDailyLimit(e.target.value)}
                placeholder="예: 20 (1~1000)"
                className={styles.input}
              />
              <span className={styles.hint}>모델 무관 전체 호출 한도. 한도 초과 시 AI 기능이 비활성화됩니다.</span>
            </div>
            <div className={styles.field}>
              <label>
                Claude 일일 한도 <span className={styles.optional}>(현재: {member.claudeDailyLimit != null ? `${member.claudeDailyLimit}회` : '전체 한도 따름'})</span>
              </label>
              <input
                type="number"
                min={1}
                max={1000}
                value={claudeDailyLimit}
                onChange={e => setClaudeDailyLimit(e.target.value)}
                placeholder="예: 10 (1~1000)"
                className={styles.input}
              />
            </div>
            <div className={styles.field}>
              <label>
                Grok 일일 한도 <span className={styles.optional}>(현재: {member.grokDailyLimit != null ? `${member.grokDailyLimit}회` : '전체 한도 따름'})</span>
              </label>
              <input
                type="number"
                min={1}
                max={1000}
                value={grokDailyLimit}
                onChange={e => setGrokDailyLimit(e.target.value)}
                placeholder="예: 10 (1~1000)"
                className={styles.input}
              />
            </div>
            <div className={styles.field}>
              <label>
                GPT 일일 한도 <span className={styles.optional}>(현재: {member.gptDailyLimit != null ? `${member.gptDailyLimit}회` : '전체 한도 따름'})</span>
              </label>
              <input
                type="number"
                min={1}
                max={1000}
                value={gptDailyLimit}
                onChange={e => setGptDailyLimit(e.target.value)}
                placeholder="예: 10 (1~1000)"
                className={styles.input}
              />
            </div>
            <div className={styles.field}>
              <label>
                Gemini 일일 한도 <span className={styles.optional}>(현재: {member.geminiDailyLimit != null ? `${member.geminiDailyLimit}회` : '전체 한도 따름'})</span>
              </label>
              <input
                type="number"
                min={1}
                max={1000}
                value={geminiDailyLimit}
                onChange={e => setGeminiDailyLimit(e.target.value)}
                placeholder="예: 10 (1~1000)"
                className={styles.input}
              />
            </div>
          </div>

          <button type="submit" className={styles.saveBtn}>저장</button>
        </form>
      </div>
    </div>
  )
}
