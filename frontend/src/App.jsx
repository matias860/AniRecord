import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import HomeView from './views/HomeView.jsx'
import LibraryView from './views/LibraryView.jsx'
import MediaDetailView from './views/MediaDetailView.jsx'
import CatalogView from './views/CatalogView.jsx'
const ProfileView = lazy(() => import('./views/ProfileView.jsx'))

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomeView />} />
        <Route path="biblioteca" element={<LibraryView />} />
        <Route path="catalogo" element={<CatalogView />} />
        <Route path="catalogo/:mediaType" element={<CatalogView />} />
        <Route path="media/:anilistId" element={<MediaDetailView />} />
        <Route path="perfil" element={<Suspense fallback={<div className="page-state">Cargando perfil...</div>}><ProfileView /></Suspense>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
