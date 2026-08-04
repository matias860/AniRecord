import { useEffect, useMemo, useState } from 'react'
import { Library } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import MediaCard from '../components/media/MediaCard.jsx'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import PageState from '../components/common/PageState.jsx'
import { fetchLibrary, incrementProgress } from '../features/library/librarySlice.js'

const statuses = [['ALL', 'Todos'], ['IN_PROGRESS', 'En curso'], ['PLANNED', 'Pendientes'], ['COMPLETED', 'Completados'], ['PAUSED', 'Pausados'], ['DROPPED', 'Abandonados']]

export default function LibraryView() {
  const dispatch = useDispatch()
  const { items, status, error } = useSelector((state) => state.library)
  const [mediaType, setMediaType] = useState('ALL')
  const [trackingStatus, setTrackingStatus] = useState('ALL')
  useEffect(() => { if (status === 'idle') dispatch(fetchLibrary()) }, [dispatch, status])
  const filtered = useMemo(() => items.filter((item) => (mediaType === 'ALL' || item.mediaType === mediaType) && (trackingStatus === 'ALL' || item.trackingStatus === trackingStatus)), [items, mediaType, trackingStatus])

  return (
    <>
      <div className="page-title"><div><span className="eyebrow">Colección personal</span><h1><Library /> Mi biblioteca</h1><p>Seguí tu progreso sin perder el hilo.</p></div><div className="type-toggle"><button className={mediaType === 'ALL' ? 'active' : ''} onClick={() => setMediaType('ALL')}>Todo</button><button className={mediaType === 'ANIME' ? 'active' : ''} onClick={() => setMediaType('ANIME')}>Anime</button><button className={mediaType === 'MANGA' ? 'active' : ''} onClick={() => setMediaType('MANGA')}>Manga</button></div></div>
      <div className="filter-row">{statuses.map(([value, label]) => <button key={value} className={trackingStatus === value ? 'active' : ''} onClick={() => setTrackingStatus(value)}>{label}</button>)}</div>
      <PageState loading={status === 'loading'} error={error} empty={status === 'succeeded' && !filtered.length}>No hay títulos para mostrar con estos filtros.</PageState>
      {!!filtered.length && <HoverMediaGrid className="library-grid">{filtered.map((media) => <MediaCard key={media.id} media={media} onIncrement={(id) => dispatch(incrementProgress(id))} />)}</HoverMediaGrid>}
    </>
  )
}
