import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useSearchParams, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { Layout } from '../components/Layout/Layout'
import { LoginPage } from '../pages/LoginPage/LoginPage'
import { PostListPage } from '../pages/PostListPage/PostListPage'
import { PostDetailPage } from '../pages/PostDetailPage/PostDetailPage'
import { PostCreatePage } from '../pages/PostCreatePage/PostCreatePage'
import { PostEditPage } from '../pages/PostEditPage/PostEditPage'
import { RepoListPage } from '../pages/RepoListPage/RepoListPage'
import { ProfilePage } from '../pages/ProfilePage/ProfilePage'
import { SqlVizPage } from '../pages/SqlVizPage/SqlVizPage'
import { SqlVizEmbedPage } from '../pages/SqlVizEmbedPage/SqlVizEmbedPage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function OAuthCallback() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { setToken } = useAuthStore()

  useEffect(() => {
    const token = params.get('token')
    if (token) {
      setToken(token)
      navigate('/', { replace: true })
    } else {
      navigate('/login', { replace: true })
    }
  }, [])

  return <p>로그인 처리 중...</p>
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/oauth/callback" element={<OAuthCallback />} />
        <Route path="/" element={<PrivateRoute><Layout><PostListPage /></Layout></PrivateRoute>} />
        <Route path="/posts/new" element={<PrivateRoute><Layout><PostCreatePage /></Layout></PrivateRoute>} />
        <Route path="/posts/:id" element={<PrivateRoute><Layout wide><PostDetailPage /></Layout></PrivateRoute>} />
        <Route path="/posts/:id/edit" element={<PrivateRoute><Layout><PostEditPage /></Layout></PrivateRoute>} />
        <Route path="/repos" element={<PrivateRoute><Layout><RepoListPage /></Layout></PrivateRoute>} />
        <Route path="/profile" element={<PrivateRoute><Layout><ProfilePage /></Layout></PrivateRoute>} />
        <Route path="/sqlviz" element={<PrivateRoute><Layout><SqlVizPage /></Layout></PrivateRoute>} />
        <Route path="/embed/sqlviz/:id" element={<SqlVizEmbedPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
