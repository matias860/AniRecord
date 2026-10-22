import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ArrowRight, BookOpen, ChevronLeft, ChevronRight, Grid3X3, Play } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import WeeklySchedule from '../components/home/WeeklySchedule.jsx'
import MediaCard from '../components/media/MediaCard.jsx'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import SeasonMarquee from '../components/home/SeasonMarquee.jsx'
import RecommendationMarquee from '../components/home/RecommendationMarquee.jsx'
import TopNineShowcase from '../components/home/TopNineShowcase.jsx'
import PageState from '../components/common/PageState.jsx'
import { fetchAiringSchedule, fetchSeason, setReadingPage, setSeasonOffset, setWatchingPage } from '../features/home/homeSlice.js'
import { fetchLibrary, incrementProgress, synchronizeAniListLibrary } from '../features/library/librarySlice.js'
import { fetchWeeklyRecommendations } from '../features/recommendations/recommendationsSlice.js'
import { fetchCurrentTopNine } from '../features/topNine/topNineSlice.js'
import { requestLogin } from '../lib/authNavigation.js'
import { acknowledgeSyncPrompt } from '../features/auth/authSlice.js'

const seasonNames = ['WINTER', 'SPRING', 'SUMMER', 'FALL']
const seasonLabels = { WINTER: 'Invierno', SPRING: 'Primavera', SUMMER: 'Verano', FALL: 'Otoño' }
const pageSize = 5
const appCommit = import.meta.env.VITE_APP_COMMIT || 'development'
const synchronizationHistory = import.meta.env.VITE_SYNC_HISTORY || [{ commit: appCommit, recommended: false }]
const WeeklyRecommendationModal = lazy(() => import('../components/home/WeeklyRecommendationModal.jsx'))
const TopNineComposer = lazy(() => import('../components/home/TopNineComposer.jsx'))

function commitsSince(lastSeenCommit) {
  if (!lastSeenCommit) return synchronizationHistory.slice(0, 1)
  const previousIndex = synchronizationHistory.findIndex((entry) => entry.commit === lastSeenCommit)
  if (previousIndex === 0) return []
  return previousIndex > 0
    ? synchronizationHistory.slice(0, previousIndex)
    : synchronizationHistory.slice(0, 1)
}

function seasonAtOffset(offset) {
  const now = new Date()
  const current = now.getFullYear() * 4 + Math.floor(now.getMonth() / 3)
  const target = current + offset
  return { season: seasonNames[target % 4], year: Math.floor(target / 4) }
}

function useIdleReady() {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    const activate = () => setReady(true)
    if ('requestIdleCallback' in window) {
      const handle = window.requestIdleCallback(activate, { timeout: 1800 })
      return () => window.cancelIdleCallback(handle)
    }
    const handle = window.setTimeout(activate, 500)
    return () => window.clearTimeout(handle)
  }, [])
  return ready
}

function useNearViewport(rootMargin = '700px 0px') {
  const ref = useRef(null)
  const [ready, setReady] = useState(false)
  useEffect(() => {
    if (ready) return undefined
    if (!('IntersectionObserver' in window)) {
      const handle = window.setTimeout(() => setReady(true), 0)
      return () => window.clearTimeout(handle)
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setReady(true)
        observer.disconnect()
      }
    }, { rootMargin })
    if (ref.current) observer.observe(ref.current)
    return () => observer.disconnect()
  }, [ready, rootMargin])
  return [ref, ready]
}

export default function HomeView() {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const auth = useSelector((state) => state.auth)
  const library = useSelector((state) => state.library)
  const home = useSelector((state) => state.home)
  const recommendations = useSelector((state) => state.recommendations)
  const topNine = useSelector((state) => state.topNine)
  const [recommendationModal, setRecommendationModal] = useState(false)
  const [topNineOpen, setTopNineOpen] = useState(false)
  const handleIncrement = useCallback((id) => dispatch(incrementProgress(id)), [dispatch])
  const communityReady = useIdleReady()
  const [seasonSectionRef, seasonSectionReady] = useNearViewport()
  const syncPromptKey = `anime-list:last-sync-prompt:${auth.userId ?? 'anonymous'}`
  const localDismissedCommit = window.localStorage.getItem(syncPromptKey)
  const dismissedCommit = auth.lastSyncPromptCommit || localDismissedCommit
  const pendingCommits = commitsSince(dismissedCommit)
  const synchronizationRecommended = pendingCommits.some((entry) => entry.recommended)
  const automaticSyncKey = `${syncPromptKey}:automatic:${appCommit}`
  const { season: selectedSeasonName, year: selectedSeasonYear } = seasonAtOffset(home.seasonOffset)
  const selectedSeasonKey = `${selectedSeasonName}-${selectedSeasonYear}`
  const selectedSeasonEntries = useMemo(
    () => home.seasonCache[selectedSeasonKey] ?? [],
    [home.seasonCache, selectedSeasonKey],
  )

  useEffect(() => {
    if (auth.authenticated && library.status === 'idle') dispatch(fetchLibrary())
  }, [auth.authenticated, dispatch, library.status])

  useEffect(() => {
    if (!auth.authenticated || !auth.userId) return
    const localCommit = window.localStorage.getItem(syncPromptKey)
    if (auth.lastSyncPromptCommit) {
      window.localStorage.removeItem(syncPromptKey)
      return
    }
    if (auth.accountType === 'ANILIST' && localCommit) {
      dispatch(acknowledgeSyncPrompt(localCommit)).unwrap()
        .then(() => window.localStorage.removeItem(syncPromptKey))
        .catch(() => {})
    }
  }, [auth.accountType, auth.authenticated, auth.lastSyncPromptCommit, auth.userId, dispatch, syncPromptKey])

  useEffect(() => {
    if (!auth.authenticated || !auth.userId || auth.accountType !== 'ANILIST'
        || dismissedCommit === appCommit || library.status !== 'succeeded') return

    const acknowledgeCurrentVersion = () => {
      window.localStorage.setItem(syncPromptKey, appCommit)
      dispatch(acknowledgeSyncPrompt(appCommit)).unwrap()
        .then(() => window.localStorage.removeItem(syncPromptKey))
        .catch(() => {})
    }

    const synchronize = () => {
      if (window.sessionStorage.getItem(automaticSyncKey)) return
      window.sessionStorage.setItem(automaticSyncKey, 'started')
      if (!synchronizationRecommended) {
        acknowledgeCurrentVersion()
        return
      }
      dispatch(synchronizeAniListLibrary()).unwrap()
        .then(acknowledgeCurrentVersion)
        .catch(() => {
          // The global toast reports the failure. Keeping the stored commit unchanged
          // allows a new attempt in the next browser session or a manual retry in Perfil.
        })
    }
    if ('requestIdleCallback' in window) {
      const handle = window.requestIdleCallback(synchronize, { timeout: 2500 })
      return () => window.cancelIdleCallback(handle)
    }
    const handle = window.setTimeout(synchronize, 900)
    return () => window.clearTimeout(handle)
  }, [auth.accountType, auth.authenticated, auth.userId, automaticSyncKey, dismissedCommit,
    dispatch, library.status, synchronizationRecommended, syncPromptKey])

  useEffect(() => {
    if (auth.authenticated && library.status === 'succeeded' && home.status === 'idle') dispatch(fetchAiringSchedule())
  }, [auth.authenticated, dispatch, home.status, library.status])

  useEffect(() => {
    // Weekly recommendations live in our own database. They must not wait for
    // AniList's airing endpoint, otherwise an external outage hides a local feature.
    if (communityReady && recommendations.status === 'idle') dispatch(fetchWeeklyRecommendations())
  }, [communityReady, dispatch, recommendations.status])

  useEffect(() => {
    if (communityReady && topNine.status === 'idle') dispatch(fetchCurrentTopNine())
  }, [communityReady, dispatch, topNine.status])

  useEffect(() => {
    if (seasonSectionReady && !home.seasonCache[selectedSeasonKey] && !home.seasonErrors[selectedSeasonKey] && home.seasonStatus !== 'loading') {
      dispatch(fetchSeason({ season: selectedSeasonName, year: selectedSeasonYear }))
    }
  }, [dispatch, home.seasonCache, home.seasonErrors, home.seasonStatus, seasonSectionReady, selectedSeasonKey, selectedSeasonName, selectedSeasonYear])

  const { airingWithCurrentProgress, allReading, allWatching, completed, inProgress, pendingEpisodeIds } = useMemo(() => {
    const libraryByAniListId = new Map(library.items.map((item) => [item.anilistId, item]))
    const airing = home.airing.map((entry) => ({
      ...entry,
      currentProgress: libraryByAniListId.get(entry.anilistId)?.progress ?? entry.currentProgress,
    }))
    const pending = new Set(airing
      .filter((entry) => entry.nextAiringEpisode.episode - 1 > entry.currentProgress)
      .map((entry) => entry.anilistId))
    const byRecentProgress = (a, b) => new Date(b.updatedAt) - new Date(a.updatedAt) || b.progress - a.progress
    const byWatchingPriority = (a, b) => {
      const pendingDifference = Number(pending.has(b.anilistId)) - Number(pending.has(a.anilistId))
      if (pendingDifference) return pendingDifference
      const releasingDifference = Number(b.releaseStatus === 'RELEASING') - Number(a.releaseStatus === 'RELEASING')
      return releasingDifference || byRecentProgress(a, b)
    }
    const watchingEntries = []
    const readingEntries = []
    let currentCount = 0
    let completedCount = 0
    for (const item of library.items) {
      if (item.trackingStatus === 'IN_PROGRESS') {
        currentCount += 1
        if (item.mediaType === 'ANIME') watchingEntries.push(item)
        else if (item.mediaType === 'MANGA') readingEntries.push(item)
      } else if (item.trackingStatus === 'COMPLETED') completedCount += 1
    }
    watchingEntries.sort(byWatchingPriority)
    readingEntries.sort(byRecentProgress)
    return {
      airingWithCurrentProgress: airing,
      allReading: readingEntries,
      allWatching: watchingEntries,
      completed: completedCount,
      inProgress: currentCount,
      pendingEpisodeIds: pending,
    }
  }, [home.airing, library.items])
  const watchingPage = Math.min(home.watchingPage, Math.max(0, Math.ceil(allWatching.length / pageSize) - 1))
  const readingPage = Math.min(home.readingPage, Math.max(0, Math.ceil(allReading.length / pageSize) - 1))
  const watching = allWatching.slice(watchingPage * pageSize, watchingPage * pageSize + pageSize)
  const reading = allReading.slice(readingPage * pageSize, readingPage * pageSize + pageSize)
  const libraryReady = library.status === 'succeeded'
  const ownRecommendation = recommendations.items.find((item) => item.recommenderId === auth.userId)
  const showRecommendationCta = recommendations.status === 'succeeded' && !ownRecommendation
  const showNewTopNine = auth.authenticated && topNine.status === 'succeeded' && topNine.prompt && !topNine.mine
  const watchingWithPending = watching.map((entry) => ({ ...entry, episodePending: pendingEpisodeIds.has(entry.anilistId) }))
  const scheduleOptions = [0, 1, 2, 3].map((offset) => {
      const item = seasonAtOffset(offset)
      return { value: String(offset), label: `${seasonLabels[item.season]} ${item.year}` }
    })
  const calendarEntries = useMemo(() => {
    const seasonMediaById = new Map(selectedSeasonEntries.map((item) => [item.anilistId, item]))
    return library.items.filter((item) => item.mediaType === 'ANIME'
      && ['PLANNED', 'IN_PROGRESS'].includes(item.trackingStatus)
      && item.releaseSeason === selectedSeasonName && item.releaseYear === selectedSeasonYear)
      .map((item) => {
        const currentMedia = seasonMediaById.get(item.anilistId)
        return currentMedia ? {
          ...item,
          releaseStartDate: currentMedia.releaseStartDate ?? item.releaseStartDate,
          releaseStatus: currentMedia.releaseStatus ?? item.releaseStatus,
        } : item
      })
  }, [library.items, selectedSeasonEntries, selectedSeasonName, selectedSeasonYear])

  return (
    <>
      <AnimatePresence>
        {recommendationModal && <Suspense fallback={null}><WeeklyRecommendationModal key="recommendation-modal" onClose={() => setRecommendationModal(false)} /></Suspense>}
      </AnimatePresence>
      <section className="welcome-row">
        <div><span className="eyebrow">Biblioteca personal</span><h1>¿Qué seguimos hoy?</h1><p>Todo tu anime y manga, ordenado en un solo lugar.</p></div>
        {auth.authenticated ? <div className={`stats ${libraryReady ? '' : 'loading'}`} aria-busy={!libraryReady}><div><strong>{libraryReady ? inProgress : '—'}</strong><span>En curso</span></div><div><strong>{libraryReady ? completed : '—'}</strong><span>Completados</span></div><div><strong>{libraryReady ? library.items.length : '—'}</strong><span>Total</span></div></div> : <Link className="primary-button welcome-login-button" to="/login">Iniciar sesión</Link>}
      </section>

      <div className={`home-community-actions${showRecommendationCta ? ' has-weekly' : ''}`}>
        <AnimatePresence initial={false}>{showRecommendationCta && <motion.button key="recommendation-cta" className="recommendation-cta" type="button" disabled={auth.authenticated && library.status !== 'succeeded'} onClick={() => auth.authenticated ? setRecommendationModal(true) : requestLogin(navigate, location)} initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 52 }} exit={{ opacity: 0, height: 0, scale: .98 }} transition={{ duration: .32, ease: 'easeOut' }}>
          <span>Recomendar una serie esta semana</span>
        </motion.button>}</AnimatePresence>
        {topNine.prompt && <div className="home-top-nine-action"><button className="home-top-nine-launch" type="button" disabled={auth.authenticated && library.status !== 'succeeded'} onClick={() => auth.authenticated ? setTopNineOpen((open) => !open) : requestLogin(navigate, location)}><Grid3X3 /> {topNine.mine ? 'Editar mi 3×3' : 'Armar mi 3×3'}</button>{showNewTopNine && <motion.span className="home-top-nine-available" initial={{ opacity: 0, x: -5 }} animate={{ opacity: 1, x: 0 }}><i aria-hidden="true" /> Nuevo 3×3 disponible</motion.span>}</div>}
      </div>
      <AnimatePresence initial={false}>{topNineOpen && auth.authenticated && topNine.prompt && <Suspense fallback={null}><TopNineComposer key={topNine.prompt.key} prompt={topNine.prompt} mine={topNine.mine} onClose={() => setTopNineOpen(false)} /></Suspense>}</AnimatePresence>

      {home.unavailable && <aside className="external-service-notice" role="status">
        <strong>Información en tiempo real temporalmente no disponible</strong>
        <span>AniList deshabilitó su API pública. Conservamos tu biblioteca y mostramos las fechas guardadas localmente.</span>
      </aside>}
      {home.status === 'failed' && !home.unavailable && <PageState error={home.error} />}
      {auth.authenticated && libraryReady && <WeeklySchedule entries={calendarEntries} airingEntries={airingWithCurrentProgress} liveDataAvailable={home.status === 'succeeded'} seasonOffset={home.seasonOffset} seasonLabel={`${seasonLabels[selectedSeasonName]} ${selectedSeasonYear}`} seasonOptions={scheduleOptions} onSeasonChange={(offset) => dispatch(setSeasonOffset(Number(offset)))} />}
      <RecommendationMarquee entries={recommendations.items} loading={recommendations.status === 'loading'} />
      <div ref={seasonSectionRef} className="home-deferred-section"><SeasonMarquee
        entries={selectedSeasonEntries}
        error={home.seasonErrors[selectedSeasonKey]}
        onRetry={() => dispatch(fetchSeason({ season: selectedSeasonName, year: selectedSeasonYear, retry: true }))}
        loading={!selectedSeasonEntries.length && home.seasonStatus === 'loading'}
        label={`${seasonLabels[selectedSeasonName]} ${selectedSeasonYear}`}
        canGoPrevious={home.seasonOffset > 0}
        canGoNext={home.seasonOffset < 3}
        onPrevious={() => dispatch(setSeasonOffset(home.seasonOffset - 1))}
        onNext={() => dispatch(setSeasonOffset(home.seasonOffset + 1))}
      /></div>

      <TopNineShowcase key={topNine.prompt?.key ?? 'top-nine-loading'} prompt={topNine.prompt} submissions={topNine.submissions} />

      {auth.authenticated && <><LibraryShelf title="Seguir viendo" eyebrow="Tu anime" icon={<Play size={20} fill="currentColor" />} entries={watchingWithPending} total={allWatching.length} page={watchingPage} onPage={(page) => dispatch(setWatchingPage(page))} emptyText="Todavía no tienes anime en curso." mediaType="ANIME" catalogPath="/catalogo/anime" onIncrement={handleIncrement} loading={library.status === 'loading'} error={library.error} />
      <LibraryShelf title="Seguir leyendo" eyebrow="Tu manga" icon={<BookOpen size={20} />} entries={reading} total={allReading.length} page={readingPage} onPage={(page) => dispatch(setReadingPage(page))} emptyText="Todavía no tienes manga en curso." mediaType="MANGA" catalogPath="/catalogo/manga" onIncrement={handleIncrement} loading={library.status === 'loading'} error={library.error} /></>}
    </>
  )
}

function LibraryShelf({ title, eyebrow, icon, entries, total, page, onPage, emptyText, mediaType, catalogPath, onIncrement, loading, error }) {
  const pages = Math.ceil(total / pageSize)
  return <section className="content-section">
    <div className="section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{icon} {title}</h2></div><div className="shelf-actions">{pages > 1 && <div className="shelf-pagination"><button disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Ver cinco anteriores"><ChevronLeft /></button><span>{page + 1} / {pages}</span><button disabled={page >= pages - 1} onClick={() => onPage(page + 1)} aria-label="Ver cinco siguientes"><ChevronRight /></button></div>}<Link to={`/biblioteca?tipo=${mediaType.toLowerCase()}`}>Ver todo <ArrowRight size={16} /></Link></div></div>
    <PageState loading={loading} error={error} empty={!loading && !entries.length}>
      <p>{emptyText}</p><Link className="primary-button" to={catalogPath}>Explorar</Link>
    </PageState>
    <AnimatePresence mode="wait" initial={false}>{!!entries.length && <motion.div key={page} initial={{ opacity: 0, x: 22 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -22 }} transition={{ duration: 0.22, ease: 'easeOut' }}><HoverMediaGrid>{entries.map((media) => <MediaCard key={media.id} media={media} onIncrement={onIncrement} />)}</HoverMediaGrid></motion.div>}</AnimatePresence>
  </section>
}
