import { lazy, Suspense, useEffect } from 'react'
import { RefreshCw } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import ProfileLoader from './components/common/ProfileLoader.jsx'
const HomeView = lazy(() => import('./views/HomeView.jsx'))
const LibraryView = lazy(() => import('./views/LibraryView.jsx'))
const MediaDetailView = lazy(() => import('./views/MediaDetailView.jsx'))
const CatalogView = lazy(() => import('./views/CatalogView.jsx'))
const ProfileView = lazy(() => import('./views/ProfileView.jsx'))
const LoginView = lazy(() => import('./views/LoginView.jsx'))
const ImportView = lazy(() => import('./views/ImportView.jsx'))
const CharacterDetailView = lazy(() => import('./views/CharacterDetailView.jsx'))
const StaffDetailView = lazy(() => import('./views/StaffDetailView.jsx'))
import { fetchAuth } from './features/auth/authSlice.js'
import apiClient from './api/apiClient.js'
import { clearReturnPath, consumeReturnPath, currentReturnPath, getReturnPath } from './lib/authNavigation.js'

const viewFallback = <div className="page-state full-page route-loading"><ProfileLoader message="Cargando…" /></div>

export default function App() {
  const dispatch = useDispatch()
  const auth = useSelector((state) => state.auth)
  useEffect(() => {
    if (auth.status !== 'idle') return
    const hash = new URLSearchParams(window.location.hash.slice(1))
    const ticket = hash.get('ticket')
    if (ticket) {
      apiClient.post('/auth/claim', { ticket }).finally(() => { window.history.replaceState({}, '', consumeReturnPath()); dispatch(fetchAuth()) })
    } else dispatch(fetchAuth())
  }, [auth.status, dispatch])
  if (auth.status === 'idle' || auth.status === 'loading') return <div className="auth-loading dot-background"><ProfileLoader message="Despertando tu biblioteca… Esto puede tardar hasta dos minutos." /></div>
  if (auth.status === 'failed') return <div className="auth-loading auth-wake-error dot-background"><ProfileLoader message="El servidor no pudo despertarse." /><p>{auth.error}</p><button className="primary-button" onClick={() => dispatch(fetchAuth())}><RefreshCw /> Reintentar conexión</button></div>
  if (auth.authenticated && auth.needsImport) return <Suspense fallback={viewFallback}><ImportView /></Suspense>
  return (
    <Suspense fallback={viewFallback}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<HomeView />} />
          <Route path="biblioteca" element={<RequireAuth authenticated={auth.authenticated}><LibraryView /></RequireAuth>} />
          <Route path="catalogo" element={<CatalogView />} />
          <Route path="catalogo/:mediaType" element={<CatalogView />} />
          <Route path="media/:anilistId" element={<MediaDetailView />} />
          <Route path="personajes/:anilistId" element={<CharacterDetailView />} />
          <Route path="seiyuus/:anilistId" element={<StaffDetailView />} />
          <Route path="profesionales/:anilistId" element={<StaffDetailView />} />
          <Route path="perfil" element={<RequireAuth authenticated={auth.authenticated}><ProfileView /></RequireAuth>} />
          <Route path="login" element={<LoginRoute authenticated={auth.authenticated} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </Suspense>
  )
}

function RequireAuth({ authenticated, children }) {
  const location = useLocation()
  if (authenticated) return children
  return <Navigate to="/login" replace state={{ returnTo: currentReturnPath(location) }} />
}

function LoginRoute({ authenticated }) {
  const destination = getReturnPath()
  useEffect(() => {
    if (authenticated) clearReturnPath()
  }, [authenticated])
  return authenticated ? <Navigate to={destination} replace /> : <LoginView />
}
