import styles from './LoginPage.module.css'

export function LoginPage() {
  const handleLogin = () => {
    window.location.href = '/oauth2/authorization/github'
  }

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>AI Blog</h1>
        <p className={styles.desc}>GitHub으로 로그인하여 AI가 개선하는 블로그를 시작하세요</p>
        <button className={styles.githubBtn} onClick={handleLogin}>
          GitHub으로 로그인
        </button>
      </div>
    </div>
  )
}
