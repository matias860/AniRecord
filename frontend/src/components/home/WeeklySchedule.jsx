import { CalendarDays } from 'lucide-react'
import { Link } from 'react-router-dom'
import StyledSelect from '../common/StyledSelect.jsx'
import { formatCountdown } from '../../lib/media.js'

const days = [
  { day: 1, label: 'L', name: 'Lunes' }, { day: 2, label: 'M', name: 'Martes' },
  { day: 3, label: 'X', name: 'Miércoles' }, { day: 4, label: 'J', name: 'Jueves' },
  { day: 5, label: 'V', name: 'Viernes' }, { day: 6, label: 'S', name: 'Sábado' },
  { day: 0, label: 'D', name: 'Domingo' },
]

export default function WeeklySchedule({ entries, seasonEntries, seasonOffset, seasonLabel, seasonOptions, seasonLoading, onSeasonChange }) {
  const today = new Date().getDay()
  const byDay = days.map((item) => ({
    ...item,
    entries: entries.filter((entry) => new Date(entry.nextAiringEpisode.airingAt * 1000).getDay() === item.day),
  }))

  return (
    <section className="schedule-panel">
      <div className="section-heading"><div><span className="eyebrow">{seasonOffset === 0 ? 'Tu semana' : 'Próximamente'}</span><h2>{seasonOffset === 0 ? 'Próximas emisiones' : `Temporada ${seasonLabel}`}</h2></div><div className="schedule-controls"><StyledSelect className="schedule-select" ariaLabel="Vista del calendario" value={String(seasonOffset)} onChange={onSeasonChange} options={seasonOptions} />{seasonOffset === 0 && <span className="live-pill"><i /> En emisión</span>}</div></div>
      {seasonOffset === 0 ? <div className="schedule-scroll">
        <div className="schedule-grid">
          <div className="schedule-line" />
          {byDay.map((item) => (
            <div className={`schedule-day ${today === item.day ? 'today' : ''}`} key={item.name}>
              <span className="day-dot">{item.label}</span><small>{item.name}</small>
              <div className="day-covers">
                {item.entries.map((entry) => (
                  <Link to={`/media/${entry.anilistId}`} className={`airing-cover ${entry.nextAiringEpisode.episode - 1 > entry.currentProgress ? 'episode-pending' : ''}`} key={entry.anilistId} title={`${entry.title} · Episodio ${entry.nextAiringEpisode.episode}`}>
                    <img src={entry.coverImage} alt={entry.title} />
                    <span>EP {entry.nextAiringEpisode.episode}</span>
                    <small>{formatCountdown(entry.nextAiringEpisode.timeUntilAiring)}</small>
                  </Link>
                ))}
                {!item.entries.length && <span className="no-airing">—</span>}
              </div>
            </div>
          ))}
        </div>
      </div> : <SeasonPreview entries={seasonEntries} loading={seasonLoading} />}
    </section>
  )
}

function SeasonPreview({ entries, loading }) {
  if (loading) return <div className="schedule-preview-empty">Cargando anuncios de AniList…</div>
  if (!entries.length) return <div className="schedule-preview-empty">AniList todavía no anunció títulos para esta temporada.</div>
  return <div className="schedule-season-preview">{entries.slice(0, 10).map((media) => <Link to={`/media/${media.anilistId}`} key={media.anilistId}><img src={media.coverImage} alt={media.title} /><span><strong>{media.title}</strong><small><CalendarDays /> {media.totalUnits != null ? `${media.totalUnits} episodios anunciados` : 'Emisión por confirmar'}</small></span></Link>)}</div>
}
