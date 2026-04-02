import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import styles from './LoginPage.module.css'

export function LoginPage() {
  const navigate = useNavigate()
  const { setToken } = useAuthStore()

  const handleLogin = () => {
    window.location.href = '/oauth2/authorization/github'
  }

  const handleMockLogin = async () => {
    const res = await fetch('/api/auth/mock-login')
    const body = await res.json()
    const token: string | undefined = body?.data?.token
    if (token) {
      setToken(token)
      navigate('/', { replace: true })
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>AI Blog</h1>
        <p className={styles.desc}>GitHub으로 로그인하여 AI가 개선하는 블로그를 시작하세요</p>
        <button className={styles.githubBtn} onClick={handleLogin}>
          GitHub으로 로그인
        </button>
        {import.meta.env.DEV && (
          <button className={styles.mockBtn} onClick={handleMockLogin}>
            [개발] Mock 로그인
          </button>
        )}
      </div>
    </div>
  )
}
