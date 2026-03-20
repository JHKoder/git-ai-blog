import { useState, useEffect } from 'react'
import toast from 'react-hot-toast'
import { repoApi } from '../../api/repoApi'
import { memberApi } from '../../api/memberApi'
import { Repo, RepoAddRequest, CollectType, PrSummary } from '../../types/repo'
import styles from './RepoListPage.module.css'

const COLLECT_TYPES: CollectType[] = ['COMMIT', 'PR', 'WIKI', 'README']

const COLLECT_TYPE_LABEL: Record<CollectType, string> = {
  COMMIT: '커밋',
  PR: 'PR',
  WIKI: 'Wiki',
  README: 'README',
}

const COLLECT_TYPE_DESC: Record<CollectType, string> = {
  COMMIT: '[blog] 태그 포함된 커밋만 수집',
  PR: 'blog 라벨이 달린 PR만 수집',
  WIKI: 'Wiki 페이지를 선택해서 수집',
  README: '클릭할 때마다 README 수집',
}

const PR_PAGE_SIZE = 8

interface PrSelectModalProps {
  repo: Repo
  onClose: () => void
  onCollect: (id: number, prNumbers: number[]) => void
  collecting: boolean
}

function PrSelectModal({ repo, onClose, onCollect, collecting }: PrSelectModalProps) {
  const [prs, setPrs] = useState<PrSummary[]>([])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)

  useEffect(() => {
    repoApi.getPrList(repo.id)
      .then(res => setPrs(res.data.data))
      .catch(() => toast.error('PR 목록 조회 실패'))
      .finally(() => setLoading(false))
  }, [repo.id])

  const totalPages = Math.ceil(prs.length / PR_PAGE_SIZE)
  const pagePrs = prs.slice(page * PR_PAGE_SIZE, (page + 1) * PR_PAGE_SIZE)

  const toggle = (num: number) => {
    setSelected(prev => {
      const next = new Set(prev)
      next.has(num) ? next.delete(num) : next.add(num)
      return next
    })
  }

  const toggleAll = () => {
    const available = prs.filter(p => !p.alreadyCollected).map(p => p.number)
    if (available.every(n => selected.has(n))) {
      setSelected(new Set())
    } else {
      setSelected(new Set(available))
    }
  }

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modal} onClick={e => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>PR 선택</h3>
        <p className={styles.modalDesc}>{repo.owner}/{repo.repoName}</p>

        {loading ? (
          <p className={styles.modalLoading}>PR 목록 불러오는 중...</p>
        ) : prs.length === 0 ? (
          <p className={styles.modalLoading}>closed PR이 없습니다.</p>
        ) : (
          <>
            <div className={styles.prListHeader}>
              <label className={styles.prCheckRow}>
                <input
                  type="checkbox"
                  checked={prs.filter(p => !p.alreadyCollected).every(p => selected.has(p.number))}
                  onChange={toggleAll}
                />
                <span className={styles.prSelectAll}>전체 선택</span>
              </label>
              <span className={styles.prCount}>{selected.size}개 선택</span>
            </div>
            <div className={styles.prList}>
              {pagePrs.map(pr => (
                <label
                  key={pr.number}
                  className={`${styles.prCheckRow} ${pr.alreadyCollected ? styles.prCollected : ''}`}
                >
                  <input
                    type="checkbox"
                    checked={selected.has(pr.number)}
                    disabled={pr.alreadyCollected}
                    onChange={() => toggle(pr.number)}
                  />
                  <span className={styles.prNumber}>#{pr.number}</span>
                  <span className={styles.prTitle}>{pr.title}</span>
                  {pr.hasBlogLabel && <span className={styles.blogBadge}>blog</span>}
                  {pr.alreadyCollected && <span className={styles.collectedBadge}>수집됨</span>}
                </label>
              ))}
            </div>
            {totalPages > 1 && (
              <div className={styles.prPagination}>
                <button
                  className={styles.pageBtn}
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                >‹</button>
                <span className={styles.pageInfo}>{page + 1} / {totalPages}</span>
                <button
                  className={styles.pageBtn}
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page === totalPages - 1}
                >›</button>
              </div>
            )}
          </>
        )}

        <div className={styles.modalActions}>
          <button
            className={styles.collectBtn}
            onClick={() => onCollect(repo.id, Array.from(selected))}
            disabled={collecting || selected.size === 0}
          >
            {collecting ? '수집 중...' : `${selected.size}개 게시글 작성`}
          </button>
          <button className={styles.deleteBtn} onClick={onClose}>취소</button>
        </div>
      </div>
    </div>
  )
}

interface WikiModalProps {
  repo: Repo
  onClose: () => void
  onCollect: (id: number, wikiPage: string) => void
  collecting: boolean
}

function WikiPageModal({ repo, onClose, onCollect, collecting }: WikiModalProps) {
  const [pages, setPages] = useState<string[]>([])
  const [selected, setSelected] = useState('Home')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    repoApi.getWikiPages(repo.id)
      .then(res => {
        setPages(res.data.data)
        if (res.data.data.length > 0) setSelected(res.data.data[0])
      })
      .catch(() => setPages(['Home']))
      .finally(() => setLoading(false))
  }, [repo.id])

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modal} onClick={e => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>Wiki 페이지 선택</h3>
        <p className={styles.modalDesc}>{repo.owner}/{repo.repoName}</p>
        {loading ? (
          <p className={styles.modalLoading}>페이지 목록 불러오는 중...</p>
        ) : (
          <>
            <select
              className={styles.modalSelect}
              value={selected}
              onChange={e => setSelected(e.target.value)}
            >
              {pages.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
            <div className={styles.modalInput}>
              <label className={styles.modalLabel}>직접 입력 (페이지명)</label>
              <input
                className={styles.modalTextInput}
                value={selected}
                onChange={e => setSelected(e.target.value)}
                placeholder="Home"
              />
            </div>
          </>
        )}
        <div className={styles.modalActions}>
          <button
            className={styles.collectBtn}
            onClick={() => onCollect(repo.id, selected)}
            disabled={collecting || loading}
          >
            {collecting ? '수집 중...' : '수집'}
          </button>
          <button className={styles.deleteBtn} onClick={onClose}>취소</button>
        </div>
      </div>
    </div>
  )
}

export function RepoListPage() {
  const [repos, setRepos] = useState<Repo[]>([])
  const [owner, setOwner] = useState('')
  const [repoName, setRepoName] = useState('')
  const [collectType, setCollectType] = useState<CollectType>('COMMIT')
  const [collecting, setCollecting] = useState<number | null>(null)
  const [wikiModal, setWikiModal] = useState<Repo | null>(null)
  const [prModal, setPrModal] = useState<Repo | null>(null)

  useEffect(() => {
    repoApi.getList().then(res => setRepos(res.data.data))
    memberApi.getMe().then(res => setOwner(res.data.data.username))
  }, [])

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const req: RepoAddRequest = { owner, repoName, collectType }
      const res = await repoApi.add(req)
      setRepos(prev => [...prev, res.data.data])
      setRepoName('')
      toast.success('레포가 추가됐습니다.')
    } catch {
      toast.error('추가 실패')
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await repoApi.delete(id)
      setRepos(prev => prev.filter(r => r.id !== id))
      toast.success('삭제됐습니다.')
    } catch {
      toast.error('삭제 실패')
    }
  }

  const handleCollectClick = (repo: Repo) => {
    if (repo.collectType === 'WIKI') {
      setWikiModal(repo)
    } else if (repo.collectType === 'PR') {
      setPrModal(repo)
    } else {
      doCollect(repo.id)
    }
  }

  const doCollect = async (id: number, wikiPage?: string) => {
    setCollecting(id)
    try {
      await repoApi.collect(id, wikiPage)
      toast.success('수집이 완료됐습니다. 게시글 목록을 확인하세요.')
      setWikiModal(null)
    } catch (e: any) {
      toast.error(e.response?.data?.message || '수집 실패')
    } finally {
      setCollecting(null)
    }
  }

  const doCollectPrs = async (id: number, prNumbers: number[]) => {
    setCollecting(id)
    try {
      await repoApi.collectPrs(id, prNumbers)
      toast.success('게시글이 작성됐습니다. 게시글 목록을 확인하세요.')
      setPrModal(null)
    } catch (e: any) {
      toast.error(e.response?.data?.message || '수집 실패')
    } finally {
      setCollecting(null)
    }
  }

  return (
    <div>
      <h2 className={styles.pageTitle}>GitHub 레포 연동</h2>
      <p className={styles.desc}>레포를 등록하고 데이터를 수집하면 블로그 초안이 자동 생성됩니다.</p>

      <div className={styles.addCard}>
        <h3>레포 추가</h3>
        <form onSubmit={handleAdd} className={styles.addForm}>
          <input value={owner} onChange={e => setOwner(e.target.value)} placeholder="사용자명 또는 조직명" required className={styles.input} />
          <span className={styles.slash}>/</span>
          <input value={repoName} onChange={e => setRepoName(e.target.value)} placeholder="레포지토리명" required className={styles.input} />
          <select value={collectType} onChange={e => setCollectType(e.target.value as CollectType)} className={styles.select}>
            {COLLECT_TYPES.map(t => (
              <option key={t} value={t}>{COLLECT_TYPE_LABEL[t]}</option>
            ))}
          </select>
          <button type="submit" className={styles.addBtn}>추가</button>
        </form>
        {collectType && (
          <p className={styles.collectDesc}>{COLLECT_TYPE_DESC[collectType]}</p>
        )}
      </div>

      <div className={styles.list}>
        {repos.length === 0 && (
          <p className={styles.empty}>등록된 레포가 없습니다.</p>
        )}
        {repos.map(repo => (
          <div key={repo.id} className={styles.item}>
            <div className={styles.itemInfo}>
              <span className={styles.repoName}>{repo.owner}/{repo.repoName}</span>
              <span className={styles.badge}>{COLLECT_TYPE_LABEL[repo.collectType]}</span>
            </div>
            <div className={styles.actions}>
              <button
                className={styles.collectBtn}
                onClick={() => handleCollectClick(repo)}
                disabled={collecting === repo.id}
              >
                {collecting === repo.id ? '수집 중...' : '데이터 수집'}
              </button>
              <button className={styles.deleteBtn} onClick={() => handleDelete(repo.id)}>삭제</button>
            </div>
          </div>
        ))}
      </div>

      {wikiModal && (
        <WikiPageModal
          repo={wikiModal}
          onClose={() => setWikiModal(null)}
          onCollect={(id, page) => doCollect(id, page)}
          collecting={collecting === wikiModal.id}
        />
      )}
      {prModal && (
        <PrSelectModal
          repo={prModal}
          onClose={() => setPrModal(null)}
          onCollect={(id, prNumbers) => doCollectPrs(id, prNumbers)}
          collecting={collecting === prModal.id}
        />
      )}
    </div>
  )
}
