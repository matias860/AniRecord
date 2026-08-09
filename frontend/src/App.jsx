import { lazy, Suspense, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import HomeView from './views/HomeView.jsx'
import LibraryView from './views/LibraryView.jsx'
import MediaDetailView from './views/MediaDetailView.jsx'
import CatalogView from './views/CatalogView.jsx'
import { BookOpen } from 'lucide-react'
const ProfileView = lazy(() => import('./views/ProfileView.jsx'))
import LoginView from './views/LoginView.jsx'
import { fetchAuth } from './features/auth/authSlice.js'
import apiClient from './api/apiClient.js'
import ImportView from './views/ImportView.jsx'
import CharacterDetailView from './views/CharacterDetailView.jsx'
import StaffDetailView from './views/StaffDetailView.jsx'

export default function App() {
  const dispatch = useDispatch()
  const auth = useSelector((state) => state.auth)
  useEffect(() => {
    if (auth.status !== 'idle') return
    const hash = new URLSearchParams(window.location.hash.slice(1))
    const ticket = hash.get('ticket')
    if (ticket) {
      apiClient.post('/auth/claim', { ticket }).finally(() => { window.history.replaceState({}, '', '/'); dispatch(fetchAuth()) })
    } else dispatch(fetchAuth())
  }, [auth.status, dispatch])
  if (auth.status === 'idle' || auth.status === 'loading') return <div className="auth-loading"><BookOpen /> Despertando tu biblioteca…</div>
  if (!auth.authenticated) return <LoginView />
  if (auth.needsImport) return <ImportView />
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomeView />} />
        <Route path="biblioteca" element={<LibraryView />} />
        <Route path="catalogo" element={<CatalogView />} />
        <Route path="catalogo/:mediaType" element={<CatalogView />} />
        <Route path="media/:anilistId" element={<MediaDetailView />} />
        <Route path="personajes/:anilistId" element={<CharacterDetailView />} />
        <Route path="seiyuus/:anilistId" element={<StaffDetailView />} />
        <Route path="perfil" element={<Suspense fallback={<div className="page-state">Cargando perfil...</div>}><ProfileView /></Suspense>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
