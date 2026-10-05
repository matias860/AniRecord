import { memo, useEffect, useMemo, useState } from 'react'
import { motion, useMotionValue, useMotionValueEvent, useSpring } from 'framer-motion'
import { BookOpen, CheckCircle2, Clapperboard, RotateCcw, Star } from 'lucide-react'

const STATUS_COLORS = { IN_PROGRESS: '#a88bff', COMPLETED: '#62d9a2', PLANNED: '#6aa9ff', PAUSED: '#f5bd62', DROPPED: '#ef6b73' }
const STATUS_LABELS = { IN_PROGRESS: 'En curso', COMPLETED: 'Completados', PLANNED: 'Planeados', PAUSED: 'Pausados', DROPPED: 'Abandonados' }

function ProfileAnalytics({ library, history }) {
  const { average, metrics, statuses, scores, maxScore, activity, maxActivity } = useMemo(() => {
    const totals = { anime: 0, manga: 0, completed: 0, rewatches: 0, scoreSum: 0, scored: 0 }
    const statusCounts = Object.fromEntries(Object.keys(STATUS_LABELS).map((status) => [status, 0]))
    const scoreCounts = Array(10).fill(0)
    library.forEach((item) => {
      if (item.mediaType === 'ANIME') totals.anime += 1
      if (item.mediaType === 'MANGA') totals.manga += 1
      if (item.trackingStatus === 'COMPLETED') totals.completed += 1
      if (statusCounts[item.trackingStatus] != null) statusCounts[item.trackingStatus] += 1
      totals.rewatches += Number(item.rewatchCount || 0)
      if (item.personalScore != null) {
        const score = Number(item.personalScore)
        totals.scoreSum += score
        totals.scored += 1
        const rounded = Math.round(score)
        if (rounded >= 1 && rounded <= 10) scoreCounts[rounded - 1] += 1
      }
    })
    const computedScores = scoreCounts.map((count, index) => ({ score: index + 1, count }))
    const computedActivity = lastDays(history, 14)
    return {
      average: totals.scored ? totals.scoreSum / totals.scored : 0,
      metrics: [
        { label: 'Anime', value: totals.anime, icon: Clapperboard },
        { label: 'Manga', value: totals.manga, icon: BookOpen },
        { label: 'Completados', value: totals.completed, icon: CheckCircle2 },
        { label: 'Rewatches', value: totals.rewatches, icon: RotateCcw },
      ],
      statuses: Object.keys(STATUS_LABELS).map((status) => ({ status, count: statusCounts[status] })),
      scores: computedScores,
      maxScore: Math.max(1, ...computedScores.map((item) => item.count)),
      activity: computedActivity,
      maxActivity: Math.max(1, ...computedActivity.map((item) => item.count)),
    }
  }, [history, library])

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

export default memo(ProfileAnalytics)

function AnimatedNumber({ value, decimals = 0 }) {
  const raw = useMotionValue(0)
  const spring = useSpring(raw, { stiffness: 75, damping: 18 })
  const [display, setDisplay] = useState('0')
  useEffect(() => { raw.set(Number(value) || 0) }, [raw, value])
  useMotionValueEvent(spring, 'change', (latest) => setDisplay(latest.toLocaleString('es-AR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })))
  return display
}

function lastDays(history, amount) {
  const counts = new Map()
  history.forEach((item) => {
    const date = new Date(item.createdAt)
    if (!Number.isNaN(date.getTime())) {
      date.setHours(0, 0, 0, 0)
      const key = date.toISOString()
      counts.set(key, (counts.get(key) || 0) + 1)
    }
  })
  return Array.from({ length: amount }, (_, offset) => {
    const date = new Date(); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - (amount - 1 - offset))
    const key = date.toISOString()
    return { key, label: date.toLocaleDateString('es-AR'), short: date.toLocaleDateString('es-AR', { weekday: 'narrow' }), count: counts.get(key) || 0 }
  })
}

function donutGradient(statuses, total) {
  if (!total) return '#252a3a'
  let position = 0
  return `conic-gradient(${statuses.map((item) => { const start = position; position += item.count / total * 100; return `${STATUS_COLORS[item.status]} ${start}% ${position}%` }).join(',')})`
}
