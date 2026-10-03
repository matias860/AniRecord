import { CalendarDays } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import StyledSelect from '../common/StyledSelect.jsx'
import { hasPendingEpisode, isUpcomingPremiere } from '../../lib/schedule.js'

const days = [
  { day: 1, label: 'L', name: 'Lunes' }, { day: 2, label: 'M', name: 'Martes' },
  { day: 3, label: 'X', name: 'Miércoles' }, { day: 4, label: 'J', name: 'Jueves' },
  { day: 5, label: 'V', name: 'Viernes' }, { day: 6, label: 'S', name: 'Sábado' },
  { day: 0, label: 'D', name: 'Domingo' },
]

export default function WeeklySchedule({ entries, airingEntries, liveDataAvailable = true, seasonLabel, seasonOptions, seasonOffset, onSeasonChange }) {
  const airingById = new Map(airingEntries.map((entry) => [entry.anilistId, entry]))
  const seasonalIds = new Set(entries.map((entry) => entry.anilistId))
  const scheduleSource = seasonOffset === 0
    ? [...entries, ...airingEntries.filter((entry) => !seasonalIds.has(entry.anilistId))]
    : entries
  const scheduled = scheduleSource.map((entry) => {
    const airing = airingById.get(entry.anilistId)
    const timestamp = airing?.nextAiringEpisode?.airingAt
    const knownDate = timestamp ? new Date(timestamp * 1000) : entry.releaseStartDate ? new Date(`${entry.releaseStartDate}T12:00:00`) : null
    return { ...entry, airing, day: knownDate ? knownDate.getDay() : null }
  })

  return <section className="schedule-panel">
    <div className="section-heading"><div><span className="eyebrow">{seasonOffset === 0 ? 'Tu semana' : 'Tu lista planeada'}</span><h2><CalendarDays /> {seasonOffset === 0 ? 'Próximas emisiones' : `Temporada ${seasonLabel}`}</h2></div><div className="schedule-controls"><StyledSelect className="schedule-select" ariaLabel="Temporada del calendario" value={String(seasonOffset)} onChange={onSeasonChange} options={seasonOptions} />{seasonOffset === 0 && <span className={`live-pill ${liveDataAvailable ? '' : 'unavailable'}`}><i /> {liveDataAvailable ? 'En emisión' : 'Datos guardados'}</span>}</div></div>
    <AnimatePresence mode="wait" initial={false}><motion.div key={seasonOffset} initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: .25, ease: 'easeOut' }}>
      {seasonOffset === 0
        ? <CurrentWeek entries={scheduled} liveDataAvailable={liveDataAvailable} />
        : <FutureSeason entries={scheduled} />}
    </motion.div></AnimatePresence>
  </section>
}

function CurrentWeek({ entries, liveDataAvailable }) {
  if (!entries.length) return <div className="schedule-preview-empty">No tienes anime en esta temporada dentro de Viendo o Pendientes.</div>
  const today = new Date().getDay()
  const byDay = days.map((item) => ({ ...item, entries: entries.filter((entry) => entry.day === item.day) }))
  const unknownEntries = entries.filter((entry) => entry.day == null)
  return <>
    <div className="schedule-scroll"><div className="schedule-grid"><div className="schedule-line" />{byDay.map((item) => <div className={`schedule-day ${today === item.day ? 'today' : ''}`} key={item.name}>
      <span className="day-dot">{item.label}</span><small>{item.name}</small><div className="day-covers">{item.entries.map((entry) => <CurrentScheduleCover entry={entry} liveDataAvailable={liveDataAvailable} key={entry.anilistId} />)}{!item.entries.length && <span className="no-airing">—</span>}</div>
    </div>)}</div></div>
    {!!unknownEntries.length && <div className="schedule-undated"><strong>Fecha por confirmar</strong><div>{unknownEntries.map((entry) => <CurrentScheduleCover entry={entry} liveDataAvailable={liveDataAvailable} key={entry.anilistId} />)}</div></div>}
  </>
}

function CurrentScheduleCover({ entry, liveDataAvailable }) {
  const nextEpisode = entry.airing?.nextAiringEpisode
  const upcoming = isUpcomingPremiere(entry)
  const pending = hasPendingEpisode(entry)
  const label = upcoming ? 'Estreno' : pending ? `EP ${nextEpisode.episode - 1}` : nextEpisode ? `EP ${nextEpisode.episode}` : 'Al día'
  const dateLabel = upcoming
    ? entry.releaseStartDate
      ? formatShortDate(entry.releaseStartDate)
      : nextEpisode ? formatTimestamp(nextEpisode.airingAt) : null
    : null
  const titleDetail = upcoming
    ? `Estreno ${dateLabel}`
    : nextEpisode ? `Próximo episodio ${nextEpisode.episode}` : liveDataAvailable ? 'Sin próximo episodio anunciado' : 'Horario no disponible'

  return <div className={`airing-item ${upcoming ? 'upcoming' : ''}`}>
    <Link to={`/media/${entry.anilistId}`} className={`airing-cover ${pending ? 'episode-pending' : ''}`} title={`${entry.title} · ${titleDetail}`}>
      <img src={entry.coverImage} alt={entry.title} /><span>{label}</span>
    </Link>
    {upcoming && <small>{dateLabel ? `Estrena ${dateLabel}` : 'Estreno sin fecha'}</small>}
  </div>
}

function FutureSeason({ entries }) {
  const knownGroups = days.map((item) => ({ ...item, entries: entries.filter((entry) => entry.day === item.day) })).filter((item) => item.entries.length)
  const unknownEntries = entries.filter((entry) => entry.day == null)
  if (!entries.length) return <div className="schedule-preview-empty">No tienes anime planeado para esta temporada.</div>
  const layout = knownGroups.length && unknownEntries.length ? 'mixed' : knownGroups.length ? 'only-known' : 'only-unknown'
  return <div className="future-schedule-scroll"><div className={`future-schedule ${layout}`}>
    {!!knownGroups.length && <div className="known-days-track" style={{ '--known-days': knownGroups.length }}><div className="compact-schedule-line" />{knownGroups.map((group) => <ScheduleDay key={group.name} group={group} />)}</div>}
    {!!unknownEntries.length && <ScheduleDay unknown group={{ label: '?', name: 'Día por confirmar', entries: unknownEntries }} />}
  </div></div>
}

function ScheduleDay({ group, unknown = false }) {
  const singleUnknown = unknown && group.entries.length === 1
  return <section className={`compact-schedule-day ${unknown ? 'unknown' : ''} ${singleUnknown ? 'single-entry' : ''}`}><span className="day-dot">{group.label}</span><strong>{group.name}</strong><small>{group.entries.length} {group.entries.length === 1 ? 'anime' : 'animes'}</small><div className="compact-day-media future-covers">{group.entries.map((entry) => <Link className="future-cover" to={`/media/${entry.anilistId}`} key={entry.id} title={entry.title}><img src={entry.coverImage} alt={entry.title} /><small>{formatShortDate(entry.releaseStartDate)}</small></Link>)}</div></section>
}

function formatShortDate(date) {
  if (!date) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-AR', { day: '2-digit', month: '2-digit', year: '2-digit' }).format(new Date(`${date}T12:00:00`))
}

function formatTimestamp(timestamp) {
  if (!timestamp) return null
  return new Intl.DateTimeFormat('es-AR', { day: '2-digit', month: '2-digit', year: '2-digit' }).format(new Date(timestamp * 1000))
}
