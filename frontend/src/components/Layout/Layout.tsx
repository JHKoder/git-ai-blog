import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { useTheme } from '../../hooks/useTheme'
import styles from './Layout.module.css'

interface Props {
  children: React.ReactNode
  wide?: boolean
}

export function Layout({ children, wide }: Props) {
  const { logout } = useAuthStore()
  const navigate = useNavigate()
  const { theme, toggle } = useTheme()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.container}>
      <nav className={styles.nav}>
        <div className={styles.navLeft}>
          <Link to="/" className={styles.logo}>AI Blog</Link>
          <Link to="/" className={styles.navLink}>게시글</Link>
          <Link to="/repos" className={styles.navLink}>레포</Link>
          <Link to="/sqlviz" className={styles.navLink}>SQL Viz</Link>
        </div>
        <div className={styles.navRight}>
          <a href="/swagger-ui/index.html" target="_blank" rel="noopener noreferrer" className={styles.navLink}>API 문서</a>
          <button className={styles.themeBtn} onClick={toggle} title={theme === 'light' ? '다크 모드' : '라이트 모드'}>
            {theme === 'light' ? '🌙' : '☀️'}
          </button>
          <Link to="/profile" className={styles.navLink}>프로필</Link>
          <button className={styles.logoutBtn} onClick={handleLogout}>로그아웃</button>
        </div>
      </nav>
      <main className={`${styles.main}${wide ? ` ${styles.mainWide}` : ''}`}>{children}</main>
    </div>
  )
}
