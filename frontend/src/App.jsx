import { lazy, Suspense, useEffect } from 'react'
import { RefreshCw } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import HomeView from './views/HomeView.jsx'
import LibraryView from './views/LibraryView.jsx'
import MediaDetailView from './views/MediaDetailView.jsx'
import CatalogView from './views/CatalogView.jsx'
import ProfileLoader from './components/common/ProfileLoader.jsx'
const ProfileView = lazy(() => import('./views/ProfileView.jsx'))
import LoginView from './views/LoginView.jsx'
import { fetchAuth } from './features/auth/authSlice.js'
import apiClient from './api/apiClient.js'
import ImportView from './views/ImportView.jsx'
import CharacterDetailView from './views/CharacterDetailView.jsx'
import StaffDetailView from './views/StaffDetailView.jsx'
import { clearReturnPath, consumeReturnPath, currentReturnPath, getReturnPath } from './lib/authNavigation.js'

export default function App() {
  return <MaintenanceView />
}

function MaintenanceView() {
  return (
    <main className="maintenance-page dot-background">
      <section className="maintenance-card" aria-labelledby="maintenance-title">
        <div className="maintenance-logo" aria-hidden="true">
          <span className="maintenance-book">▯</span>
          <span className="maintenance-orbit" />
        </div>
        <p className="maintenance-eyebrow">ANIME LIST</p>
        <h1 id="maintenance-title">Estamos migrando</h1>
        <p className="maintenance-copy">
          Estamos preparando un servidor más estable para que tu biblioteca cargue mejor.
          Volveremos dentro de poco.
        </p>
        <div className="maintenance-status"><span /> Migración en curso</div>
      </section>
    </main>
  )
}

function Application() {
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
  if (auth.authenticated && auth.needsImport) return <ImportView />
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomeView />} />
        <Route path="biblioteca" element={<RequireAuth authenticated={auth.authenticated}><LibraryView /></RequireAuth>} />
        <Route path="catalogo" element={<CatalogView />} />
        <Route path="catalogo/:mediaType" element={<CatalogView />} />
        <Route path="media/:anilistId" element={<MediaDetailView />} />
        <Route path="personajes/:anilistId" element={<CharacterDetailView />} />
        <Route path="seiyuus/:anilistId" element={<StaffDetailView />} />
        <Route path="perfil" element={<RequireAuth authenticated={auth.authenticated}><Suspense fallback={<div className="page-state"><ProfileLoader message="Cargando perfil..." /></div>}><ProfileView /></Suspense></RequireAuth>} />
        <Route path="login" element={<LoginRoute authenticated={auth.authenticated} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
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
