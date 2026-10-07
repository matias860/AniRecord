import { lazy, Suspense, useEffect, useRef } from 'react'
import { RefreshCw } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
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
import { fetchAuth, sessionExpired, oauthFailed } from './features/auth/authSlice.js'
import apiClient from './api/apiClient.js'
import { clearReturnPath, currentReturnPath, getReturnPath, rememberReturnPath } from './lib/authNavigation.js'

const viewFallback = <div className="page-state full-page route-loading"><ProfileLoader message="Cargando…" /></div>

export default function App() {
  const dispatch = useDispatch()
  const auth = useSelector((state) => state.auth)
  const navigate = useNavigate()
  const initialized = useRef(false)
  useEffect(() => {
    if (initialized.current) return
    initialized.current = true
    const hash = new URLSearchParams(window.location.hash.slice(1))
    const ticket = hash.get('ticket')
    const destination = getReturnPath()
    const fail = (message) => {
      dispatch(oauthFailed(message))
      navigate('/login', { replace: true, state: { authMessage: message, returnTo: destination } })
    }
    if (hash.get('auth') === 'error') {
      fail('No se pudo completar el acceso con AniList. La autorización pudo cancelarse o vencer. Volvé a intentarlo.')
    } else if (ticket) {
      apiClient.post('/auth/claim', { ticket }).then(async () => {
        const status = await dispatch(fetchAuth()).unwrap()
        if (!status.authenticated) throw new Error('La sesión no pudo confirmarse.')
        clearReturnPath()
        navigate(destination, { replace: true })
      }).catch(() => fail('No se pudo confirmar el acceso con AniList. Volvé a iniciar sesión.'))
    } else dispatch(fetchAuth())
  }, [dispatch, navigate])
  useEffect(() => {
    const expire = () => {
      const returnTo = rememberReturnPath(currentReturnPath())
      dispatch(sessionExpired())
      navigate('/login', { replace: true, state: { returnTo, authMessage: 'Tu sesión venció. Iniciá sesión para continuar.' } })
    }
    window.addEventListener('anirecord:session-expired', expire)
    return () => window.removeEventListener('anirecord:session-expired', expire)
  }, [dispatch, navigate])
  const authPending = auth.status === 'idle' || auth.status === 'loading'
  if (auth.authenticated && auth.needsImport) return <Suspense fallback={viewFallback}><ImportView /></Suspense>
  return (
    <Suspense fallback={viewFallback}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<HomeView />} />
          <Route path="biblioteca" element={<RequireAuth auth={auth}><LibraryView /></RequireAuth>} />
          <Route path="catalogo" element={<CatalogView />} />
          <Route path="catalogo/:mediaType" element={<CatalogView />} />
          <Route path="media/:anilistId" element={<MediaDetailView />} />
          <Route path="personajes/:anilistId" element={<CharacterDetailView />} />
          <Route path="seiyuus/:anilistId" element={<StaffDetailView />} />
          <Route path="profesionales/:anilistId" element={<StaffDetailView />} />
          <Route path="perfil" element={<RequireAuth auth={auth}><ProfileView /></RequireAuth>} />
          <Route path="login" element={<LoginRoute authenticated={auth.authenticated} pending={authPending} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </Suspense>
  )
}

function RequireAuth({ auth, children }) {
  const location = useLocation()
  const dispatch = useDispatch()
  if (auth.authenticated) return children
  if (auth.status === 'idle' || auth.status === 'loading') return viewFallback
  if (auth.status === 'failed') return <div className="page-state full-page auth-route-error"><ProfileLoader message="No pudimos conectar con tu cuenta." /><p>{auth.error}</p><button className="primary-button" onClick={() => dispatch(fetchAuth())}><RefreshCw /> Reintentar conexión</button></div>
  return <Navigate to="/login" replace state={{ returnTo: currentReturnPath(location) }} />
}

function LoginRoute({ authenticated, pending }) {
  const destination = getReturnPath()
  useEffect(() => {
    if (authenticated) clearReturnPath()
  }, [authenticated])
  if (authenticated) return <Navigate to={destination} replace />
  return <LoginView checkingSession={pending} />
}
