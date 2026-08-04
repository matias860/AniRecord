import { Link } from 'react-router-dom'
import { formatCountdown } from '../../lib/media.js'

const days = [
  { day: 1, label: 'L', name: 'Lunes' }, { day: 2, label: 'M', name: 'Martes' },
  { day: 3, label: 'X', name: 'Miércoles' }, { day: 4, label: 'J', name: 'Jueves' },
  { day: 5, label: 'V', name: 'Viernes' }, { day: 6, label: 'S', name: 'Sábado' },
  { day: 0, label: 'D', name: 'Domingo' },
]

export default function WeeklySchedule({ entries }) {
  const today = new Date().getDay()
  const byDay = days.map((item) => ({
    ...item,
    entries: entries.filter((entry) => new Date(entry.nextAiringEpisode.airingAt * 1000).getDay() === item.day),
  }))

  return (
    <section className="schedule-panel">
      <div className="section-heading"><div><span className="eyebrow">Tu semana</span><h2>Próximas emisiones</h2></div><span className="live-pill"><i /> En emisión</span></div>
      <div className="schedule-scroll">
        <div className="schedule-grid">
          <div className="schedule-line" />
          {byDay.map((item) => (
            <div className={`schedule-day ${today === item.day ? 'today' : ''}`} key={item.name}>
              <span className="day-dot">{item.label}</span><small>{item.name}</small>
              <div className="day-covers">
                {item.entries.map((entry) => (
                  <Link to={`/media/${entry.anilistId}`} className="airing-cover" key={entry.anilistId} title={`${entry.title} · Episodio ${entry.nextAiringEpisode.episode}`}>
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
      </div>
    </section>
  )
}
