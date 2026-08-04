import { useEffect, useMemo } from 'react'
import { Library } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import MediaCard from '../components/media/MediaCard.jsx'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import PageState from '../components/common/PageState.jsx'
import {
  fetchLibrary,
  incrementProgress,
  setLibraryMediaType,
  setLibraryTrackingStatus,
} from '../features/library/librarySlice.js'

const statuses = [['ALL', 'Todos'], ['IN_PROGRESS', 'En curso'], ['PLANNED', 'Pendientes'], ['COMPLETED', 'Completados'], ['PAUSED', 'Pausados'], ['DROPPED', 'Abandonados']]
const sectionOrder = ['IN_PROGRESS', 'COMPLETED', 'PLANNED', 'PAUSED', 'DROPPED']
const sectionLabels = {
  IN_PROGRESS: { ANIME: 'Viendo', MANGA: 'Leyendo' },
  COMPLETED: { ANIME: 'Anime completado', MANGA: 'Manga completado' },
  PLANNED: { ANIME: 'Anime pendiente', MANGA: 'Manga pendiente' },
  PAUSED: { ANIME: 'Anime pausado', MANGA: 'Manga pausado' },
  DROPPED: { ANIME: 'Anime abandonado', MANGA: 'Manga abandonado' },
}

export default function LibraryView() {
  const dispatch = useDispatch()
  const { items, status, error, filters = {} } = useSelector((state) => state.library)
  const mediaType = filters.mediaType === 'MANGA' ? 'MANGA' : 'ANIME'
  const trackingStatus = filters.trackingStatus ?? 'ALL'

  useEffect(() => { if (status === 'idle') dispatch(fetchLibrary()) }, [dispatch, status])

  const filtered = useMemo(() => items.filter((item) =>
    item.mediaType === mediaType
    && (trackingStatus === 'ALL' || item.trackingStatus === trackingStatus)),
  [items, mediaType, trackingStatus])

  const sections = useMemo(() => sectionOrder
    .filter((sectionStatus) => trackingStatus === 'ALL' || trackingStatus === sectionStatus)
    .map((sectionStatus) => ({
      status: sectionStatus,
      title: sectionLabels[sectionStatus][mediaType],
      items: filtered.filter((item) => item.trackingStatus === sectionStatus),
    }))
    .filter((section) => section.items.length), [filtered, mediaType, trackingStatus])

  return (
    <>
      <div className="page-title">
        <div><span className="eyebrow">Colección personal</span><h1><Library /> Mi biblioteca</h1><p>Seguí tu progreso sin perder el hilo.</p></div>
        <div className="type-toggle">
          <button className={mediaType === 'ANIME' ? 'active' : ''} onClick={() => dispatch(setLibraryMediaType('ANIME'))}>Anime</button>
          <button className={mediaType === 'MANGA' ? 'active' : ''} onClick={() => dispatch(setLibraryMediaType('MANGA'))}>Manga</button>
        </div>
      </div>
      <div className="filter-row">{statuses.map(([value, label]) => <button key={value} className={trackingStatus === value ? 'active' : ''} onClick={() => dispatch(setLibraryTrackingStatus(value))}>{label}</button>)}</div>
      <PageState loading={status === 'loading'} error={error} empty={status === 'succeeded' && !filtered.length}>No hay títulos para mostrar con estos filtros.</PageState>
      <div className="library-sections">
        {sections.map((section) => <section className="library-status-section" key={section.status}>
          <div className="library-section-heading"><h2>{section.title}</h2><span>{section.items.length}</span></div>
          <HoverMediaGrid className="library-grid">{section.items.map((media) => <MediaCard key={media.id} media={media} onIncrement={(id) => dispatch(incrementProgress(id))} />)}</HoverMediaGrid>
        </section>)}
      </div>
    </>
  )
}
