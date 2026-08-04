import { useEffect } from 'react'
import { ArrowRight, BookOpen, ChevronLeft, ChevronRight, Play } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import WeeklySchedule from '../components/home/WeeklySchedule.jsx'
import MediaCard from '../components/media/MediaCard.jsx'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import SeasonMarquee from '../components/home/SeasonMarquee.jsx'
import PageState from '../components/common/PageState.jsx'
import { fetchAiringSchedule, fetchSeason, setReadingPage, setSeasonOffset, setWatchingPage } from '../features/home/homeSlice.js'
import { fetchLibrary, incrementProgress } from '../features/library/librarySlice.js'

const seasonNames = ['WINTER', 'SPRING', 'SUMMER', 'FALL']
const seasonLabels = { WINTER: 'Invierno', SPRING: 'Primavera', SUMMER: 'Verano', FALL: 'Otoño' }
const pageSize = 5

function seasonAtOffset(offset) {
  const now = new Date()
  const current = now.getFullYear() * 4 + Math.floor(now.getMonth() / 3)
  const target = current + offset
  return { season: seasonNames[target % 4], year: Math.floor(target / 4) }
}

export default function HomeView() {
  const dispatch = useDispatch()
  const library = useSelector((state) => state.library)
  const home = useSelector((state) => state.home)
  const { season: selectedSeasonName, year: selectedSeasonYear } = seasonAtOffset(home.seasonOffset)

  useEffect(() => {
    if (library.status === 'idle') dispatch(fetchLibrary())
    if (home.status === 'idle') dispatch(fetchAiringSchedule())
  }, [dispatch, library.status, home.status])

  useEffect(() => {
    if (home.loadedSeason !== selectedSeasonName || home.loadedYear !== selectedSeasonYear) {
      dispatch(fetchSeason({ season: selectedSeasonName, year: selectedSeasonYear }))
    }
  }, [dispatch, home.loadedSeason, home.loadedYear, selectedSeasonName, selectedSeasonYear])

  const airingWithCurrentProgress = home.airing.map((entry) => ({
    ...entry,
    currentProgress: library.items.find((item) => item.anilistId === entry.anilistId)?.progress ?? entry.currentProgress,
  }))
  const pendingEpisodeIds = new Set(airingWithCurrentProgress
    .filter((entry) => entry.nextAiringEpisode.episode - 1 > entry.currentProgress)
    .map((entry) => entry.anilistId))
  const byRecentProgress = (a, b) => new Date(b.updatedAt) - new Date(a.updatedAt) || b.progress - a.progress
  const byWatchingPriority = (a, b) => {
    const pendingDifference = Number(pendingEpisodeIds.has(b.anilistId)) - Number(pendingEpisodeIds.has(a.anilistId))
    if (pendingDifference) return pendingDifference
    const releasingDifference = Number(b.releaseStatus === 'RELEASING') - Number(a.releaseStatus === 'RELEASING')
    return releasingDifference || byRecentProgress(a, b)
  }
  const allWatching = library.items.filter((item) => item.trackingStatus === 'IN_PROGRESS' && item.mediaType === 'ANIME').sort(byWatchingPriority)
  const allReading = library.items.filter((item) => item.trackingStatus === 'IN_PROGRESS' && item.mediaType === 'MANGA').sort(byRecentProgress)
  const watchingPage = Math.min(home.watchingPage, Math.max(0, Math.ceil(allWatching.length / pageSize) - 1))
  const readingPage = Math.min(home.readingPage, Math.max(0, Math.ceil(allReading.length / pageSize) - 1))
  const watching = allWatching.slice(watchingPage * pageSize, watchingPage * pageSize + pageSize)
  const reading = allReading.slice(readingPage * pageSize, readingPage * pageSize + pageSize)
  const inProgress = library.items.filter((item) => item.trackingStatus === 'IN_PROGRESS').length
  const completed = library.items.filter((item) => item.trackingStatus === 'COMPLETED').length
  const watchingWithPending = watching.map((entry) => ({ ...entry, episodePending: pendingEpisodeIds.has(entry.anilistId) }))
  const scheduleOptions = [
    { value: '0', label: 'Esta semana' },
    ...[1, 2, 3].map((offset) => {
      const item = seasonAtOffset(offset)
      return { value: String(offset), label: `${seasonLabels[item.season]} ${item.year}` }
    }),
  ]

  return (
    <>
      <section className="welcome-row">
        <div><span className="eyebrow">Biblioteca personal</span><h1>¿Qué seguimos hoy?</h1><p>Todo tu anime y manga, ordenado en un solo lugar.</p></div>
        <div className="stats"><div><strong>{inProgress}</strong><span>En curso</span></div><div><strong>{completed}</strong><span>Completados</span></div><div><strong>{library.items.length}</strong><span>Total</span></div></div>
      </section>

      <PageState loading={home.status === 'loading'} error={home.error} />
      {home.status === 'succeeded' && <WeeklySchedule entries={airingWithCurrentProgress} seasonEntries={home.season} seasonOffset={home.seasonOffset} seasonLabel={`${seasonLabels[selectedSeasonName]} ${selectedSeasonYear}`} seasonOptions={scheduleOptions} seasonLoading={home.seasonStatus === 'loading'} onSeasonChange={(offset) => dispatch(setSeasonOffset(Number(offset)))} />}
      <SeasonMarquee
        entries={home.season}
        loading={home.seasonStatus === 'loading'}
        label={`${seasonLabels[selectedSeasonName]} ${selectedSeasonYear}`}
        canGoPrevious={home.seasonOffset > 0}
        canGoNext={home.seasonOffset < 3}
        onPrevious={() => dispatch(setSeasonOffset(home.seasonOffset - 1))}
        onNext={() => dispatch(setSeasonOffset(home.seasonOffset + 1))}
      />

      <LibraryShelf title="Seguir viendo" eyebrow="Tu anime" icon={<Play size={20} fill="currentColor" />} entries={watchingWithPending} total={allWatching.length} page={watchingPage} onPage={(page) => dispatch(setWatchingPage(page))} emptyText="Todavía no tenés anime en curso." catalogPath="/catalogo/anime" onIncrement={(id) => dispatch(incrementProgress(id))} loading={library.status === 'loading'} error={library.error} />
      <LibraryShelf title="Seguir leyendo" eyebrow="Tu manga" icon={<BookOpen size={20} />} entries={reading} total={allReading.length} page={readingPage} onPage={(page) => dispatch(setReadingPage(page))} emptyText="Todavía no tenés manga en curso." catalogPath="/catalogo/manga" onIncrement={(id) => dispatch(incrementProgress(id))} loading={library.status === 'loading'} error={library.error} />
    </>
  )
}

function LibraryShelf({ title, eyebrow, icon, entries, total, page, onPage, emptyText, catalogPath, onIncrement, loading, error }) {
  const pages = Math.ceil(total / pageSize)
  return <section className="content-section">
    <div className="section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{icon} {title}</h2></div><div className="shelf-actions">{pages > 1 && <div className="shelf-pagination"><button disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Ver cinco anteriores"><ChevronLeft /></button><span>{page + 1} / {pages}</span><button disabled={page >= pages - 1} onClick={() => onPage(page + 1)} aria-label="Ver cinco siguientes"><ChevronRight /></button></div>}<Link to="/biblioteca">Ver todo <ArrowRight size={16} /></Link></div></div>
    <PageState loading={loading} error={error} empty={!loading && !entries.length}>
      <p>{emptyText}</p><Link className="primary-button" to={catalogPath}>Explorar</Link>
    </PageState>
    <AnimatePresence mode="wait" initial={false}>{!!entries.length && <motion.div key={page} initial={{ opacity: 0, x: 22 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -22 }} transition={{ duration: 0.22, ease: 'easeOut' }}><HoverMediaGrid>{entries.map((media) => <MediaCard key={media.id} media={media} onIncrement={onIncrement} />)}</HoverMediaGrid></motion.div>}</AnimatePresence>
  </section>
}
