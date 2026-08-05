import { motion } from 'framer-motion'
import { Clock3, History, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'

const STATUS = { PLANNED: 'Planeado', IN_PROGRESS: 'En curso', PAUSED: 'Pausado', COMPLETED: 'Completado', DROPPED: 'Abandonado' }

export default function ActivityTimeline({ entries, status, error }) {
  return <section className="activity-timeline"><div className="timeline-title"><div><span className="eyebrow">Registro personal</span><h2><History /> Historial</h2></div><span>Últimos movimientos</span></div>
    {status === 'loading' && !entries.length ? <p className="timeline-empty">Cargando actividad…</p> : error ? <p className="timeline-empty">{error}</p> : entries.length ? <div className="timeline-list">{entries.slice(0, 20).map((entry, index) => <TimelineEntry entry={entry} index={index} key={entry.id} />)}</div> : <p className="timeline-empty">Tus próximos cambios aparecerán acá.</p>}
  </section>
}

function TimelineEntry({ entry, index }) {
  const Icon = entry.activityType === 'ADDED' ? Plus : entry.activityType === 'REMOVED' ? Trash2 : entry.activityType === 'PROGRESS' ? RefreshCw : Clock3
  return <motion.article className="timeline-entry" initial={{ opacity: 0, x: -12 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} transition={{ delay: Math.min(index, 8) * .04 }}><div className="timeline-node"><Icon /></div><Link to={`/media/${entry.anilistId}`}><img src={entry.coverImage} alt="" /><div><strong>{entry.title}</strong><p>{describe(entry)}</p><time>{formatDate(entry.createdAt)}</time></div></Link></motion.article>
}

function describe(entry) {
  if (entry.activityType === 'ADDED') return 'Agregado a la biblioteca'
  if (entry.activityType === 'REMOVED') return 'Eliminado de la biblioteca'
  if (entry.previousProgress !== entry.newProgress && entry.newProgress != null) return `${entry.mediaType === 'ANIME' ? 'Episodios' : 'Capítulos'}: ${entry.previousProgress ?? 0} → ${entry.newProgress}`
  if (entry.previousStatus !== entry.newStatus && entry.newStatus) return `Estado: ${STATUS[entry.previousStatus] || '—'} → ${STATUS[entry.newStatus]}`
  if (entry.previousScore !== entry.newScore) return `Puntaje: ${entry.previousScore ?? '—'} → ${entry.newScore ?? '—'}`
  if (entry.previousRewatchCount !== entry.newRewatchCount) return `${entry.mediaType === 'ANIME' ? 'Rewatches' : 'Relecturas'}: ${entry.previousRewatchCount ?? 0} → ${entry.newRewatchCount}`
  return 'Información de seguimiento actualizada'
}

function formatDate(value) {
  const date = new Date(value)
  const today = new Date()
  const sameDay = date.toDateString() === today.toDateString()
  return sameDay ? `Hoy, ${date.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })}` : date.toLocaleString('es-AR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}
