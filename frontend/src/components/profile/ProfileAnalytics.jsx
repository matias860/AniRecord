import { useEffect, useState } from 'react'
import { motion, useMotionValue, useMotionValueEvent, useSpring } from 'framer-motion'
import { BookOpen, CheckCircle2, Clapperboard, RotateCcw, Star } from 'lucide-react'

const STATUS_COLORS = { IN_PROGRESS: '#a88bff', COMPLETED: '#62d9a2', PLANNED: '#6aa9ff', PAUSED: '#f5bd62', DROPPED: '#ef6b73' }
const STATUS_LABELS = { IN_PROGRESS: 'En curso', COMPLETED: 'Completados', PLANNED: 'Planeados', PAUSED: 'Pausados', DROPPED: 'Abandonados' }

export default function ProfileAnalytics({ library, history }) {
  const anime = library.filter((item) => item.mediaType === 'ANIME')
  const manga = library.filter((item) => item.mediaType === 'MANGA')
  const scored = library.filter((item) => item.personalScore != null)
  const average = scored.length ? scored.reduce((sum, item) => sum + Number(item.personalScore), 0) / scored.length : 0
  const metrics = [
    { label: 'Anime', value: anime.length, icon: Clapperboard },
    { label: 'Manga', value: manga.length, icon: BookOpen },
    { label: 'Completados', value: library.filter((item) => item.trackingStatus === 'COMPLETED').length, icon: CheckCircle2 },
    { label: 'Rewatches', value: library.reduce((sum, item) => sum + Number(item.rewatchCount || 0), 0), icon: RotateCcw },
  ]
  const statuses = Object.keys(STATUS_LABELS).map((status) => ({ status, count: library.filter((item) => item.trackingStatus === status).length }))
  const scores = Array.from({ length: 10 }, (_, index) => ({ score: index + 1, count: scored.filter((item) => Math.round(Number(item.personalScore)) === index + 1).length }))
  const maxScore = Math.max(1, ...scores.map((item) => item.count))
  const activity = lastDays(history, 14)
  const maxActivity = Math.max(1, ...activity.map((item) => item.count))

  return <section className="profile-analytics">
    <div className="analytics-heading"><div><span className="eyebrow">Tus números</span><h2>Estadísticas personales</h2></div><div className="average-score"><Star /><b>{average ? <AnimatedNumber value={average} decimals={1} /> : '—'}</b><span>Puntaje medio</span></div></div>
    <div className="analytics-metrics">{metrics.map(({ label, value, icon: Icon }, index) => <motion.article className="analytics-metric" key={label} initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: index * .06 }}><div className="metric-glow" /><Icon /><strong><AnimatedNumber value={value} /></strong><span>{label}</span></motion.article>)}</div>
    <div className="analytics-charts">
      <article className="chart-panel activity-chart"><div><span className="eyebrow">Actividad</span><h3>Últimos 14 días</h3></div><div className="activity-bars">{activity.map((day) => <div className="bar-column" key={day.key} title={`${day.label}: ${day.count} movimientos`}><motion.div initial={{ height: 0 }} whileInView={{ height: `${Math.max(5, day.count / maxActivity * 100)}%` }} viewport={{ once: true }} transition={{ duration: .55 }} className={day.count ? 'has-value' : ''}><span>{day.count || ''}</span></motion.div><small>{day.short}</small></div>)}</div></article>
      <article className="chart-panel score-chart"><div><span className="eyebrow">Valoraciones</span><h3>Distribución de puntajes</h3></div><div className="score-bars">{scores.map((item) => <div key={item.score}><span>{item.score}</span><div><motion.i initial={{ width: 0 }} whileInView={{ width: `${item.count / maxScore * 100}%` }} viewport={{ once: true }} transition={{ duration: .55 }} /></div><b>{item.count}</b></div>)}</div></article>
      <article className="chart-panel status-chart"><div><span className="eyebrow">Biblioteca</span><h3>Estados</h3></div><div className="status-donut" style={{ background: donutGradient(statuses, library.length) }}><div><b>{library.length}</b><span>Títulos</span></div></div><div className="status-legend">{statuses.map((item) => <div key={item.status}><i style={{ background: STATUS_COLORS[item.status] }} /><span>{STATUS_LABELS[item.status]}</span><b>{item.count}</b></div>)}</div></article>
    </div>
  </section>
}

function AnimatedNumber({ value, decimals = 0 }) {
  const raw = useMotionValue(0)
  const spring = useSpring(raw, { stiffness: 75, damping: 18 })
  const [display, setDisplay] = useState('0')
  useEffect(() => { raw.set(Number(value) || 0) }, [raw, value])
  useMotionValueEvent(spring, 'change', (latest) => setDisplay(latest.toLocaleString('es-AR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })))
  return display
}

function lastDays(history, amount) {
  return Array.from({ length: amount }, (_, offset) => {
    const date = new Date(); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - (amount - 1 - offset))
    const next = new Date(date); next.setDate(next.getDate() + 1)
    return { key: date.toISOString(), label: date.toLocaleDateString('es-AR'), short: date.toLocaleDateString('es-AR', { weekday: 'narrow' }), count: history.filter((item) => { const created = new Date(item.createdAt); return created >= date && created < next }).length }
  })
}

function donutGradient(statuses, total) {
  if (!total) return '#252a3a'
  let position = 0
  return `conic-gradient(${statuses.map((item) => { const start = position; position += item.count / total * 100; return `${STATUS_COLORS[item.status]} ${start}% ${position}%` }).join(',')})`
}
