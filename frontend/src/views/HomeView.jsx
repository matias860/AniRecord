import { useEffect } from 'react'
import { ArrowRight, Play } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import WeeklySchedule from '../components/home/WeeklySchedule.jsx'
import MediaCard from '../components/media/MediaCard.jsx'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import SeasonMarquee from '../components/home/SeasonMarquee.jsx'
import PageState from '../components/common/PageState.jsx'
import { fetchAiringSchedule, fetchCurrentSeason } from '../features/home/homeSlice.js'
import { fetchLibrary, incrementProgress } from '../features/library/librarySlice.js'

export default function HomeView() {
  const dispatch = useDispatch()
  const library = useSelector((state) => state.library)
  const home = useSelector((state) => state.home)

  useEffect(() => {
    if (library.status === 'idle') dispatch(fetchLibrary())
    if (home.status === 'idle') dispatch(fetchAiringSchedule())
    if (home.seasonStatus === 'idle') dispatch(fetchCurrentSeason())
  }, [dispatch, library.status, home.status, home.seasonStatus])

  const watching = library.items.filter((item) => item.trackingStatus === 'IN_PROGRESS').slice(0, 5)
  const completed = library.items.filter((item) => item.trackingStatus === 'COMPLETED').length

  return (
    <>
      <section className="welcome-row">
        <div><span className="eyebrow">Biblioteca personal</span><h1>¿Qué seguimos hoy?</h1><p>Todo tu anime y manga, ordenado en un solo lugar.</p></div>
        <div className="stats"><div><strong>{watching.length}</strong><span>En curso</span></div><div><strong>{completed}</strong><span>Completados</span></div><div><strong>{library.items.length}</strong><span>Total</span></div></div>
      </section>

      <PageState loading={home.status === 'loading'} error={home.error} />
      {home.status === 'succeeded' && <WeeklySchedule entries={home.airing} />}
      <SeasonMarquee entries={home.season} />

      <section className="content-section">
        <div className="section-heading"><div><span className="eyebrow">Tu biblioteca</span><h2><Play size={20} fill="currentColor" /> Continuar</h2></div><Link to="/biblioteca">Ver todo <ArrowRight size={16} /></Link></div>
        <PageState loading={library.status === 'loading'} error={library.error} empty={library.status === 'succeeded' && !watching.length}>
          <p>Todavía no tenés títulos en curso.</p><Link className="primary-button" to="/buscar">Buscar anime</Link>
        </PageState>
        {!!watching.length && <HoverMediaGrid>{watching.map((media) => <MediaCard key={media.id} media={media} onIncrement={(id) => dispatch(incrementProgress(id))} />)}</HoverMediaGrid>}
      </section>
    </>
  )
}
