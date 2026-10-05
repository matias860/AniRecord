import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight, Grid3X3, UserRound } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import './TopNineShowcase.css'

const ROTATION_MS = 9000
const COVER_FALLBACK = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="240" height="360"%3E%3Crect width="100%25" height="100%25" fill="%2310182b"/%3E%3Cpath d="M60 70h120v190H60zM120 70v190" fill="none" stroke="%2334d058" stroke-width="12"/%3E%3C/svg%3E'
const coverError = (event) => { if (event.currentTarget.src !== COVER_FALLBACK) event.currentTarget.src = COVER_FALLBACK }

function SubmissionCard({ submission }) {
  return <article className="top-nine-community-card">
    <header>{submission.userAvatar ? <img src={submission.userAvatar} onError={(event) => { event.currentTarget.style.display = 'none' }} alt="" loading="lazy" decoding="async" /> : <span><UserRound /></span>}<div><strong>{submission.userName}</strong><small>Su selección</small></div></header>
    <div className="top-nine-mini-grid">{submission.selections.map((media) => <Link to={`/media/${media.anilistId}`} key={media.position} title={media.title} aria-label={media.title}><img src={media.coverImage || COVER_FALLBACK} onError={coverError} alt="" loading="lazy" decoding="async" /></Link>)}</div>
  </article>
}

export default function TopNineShowcase({ prompt, submissions }) {
  const pages = useMemo(() => {
    const result = []
    for (let index = 0; index < submissions.length; index += 2) result.push(submissions.slice(index, index + 2))
    return result
  }, [submissions])
  const [active, setActive] = useState(0)
  const [paused, setPaused] = useState(false)
  const [remaining] = useState(() => prompt ? Math.max(0, Math.ceil((new Date(`${prompt.changesAt}T00:00:00-03:00`) - Date.now()) / 86400000)) : 0)
  const safeActive = pages.length ? active % pages.length : 0
  const change = (direction) => setActive((current) => (current + direction + pages.length) % pages.length)

  useEffect(() => {
    if (pages.length < 2 || paused) return undefined
    const timer = window.setInterval(() => setActive((current) => (current + 1) % pages.length), ROTATION_MS)
    return () => window.clearInterval(timer)
  }, [pages.length, paused, safeActive])

  if (!prompt || !submissions.length) return null

  return <section className="content-section top-nine-showcase" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)} onFocusCapture={() => setPaused(true)} onBlurCapture={() => setPaused(false)}>
    <div className="section-heading"><div><span className="eyebrow">El 3×3 de la comunidad</span><h2><Grid3X3 /> {prompt.title}</h2></div><span>{remaining === 1 ? 'Cambia mañana' : `Cambia en ${remaining} días`}</span></div>
    <AnimatePresence mode="wait" initial={false}><motion.div className={`top-nine-community-page${pages[safeActive].length === 1 ? ' single' : ''}`} key={safeActive} initial={{ opacity: 0, x: 34 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -34 }} transition={{ duration: .32, ease: 'easeOut' }}>
      {pages[safeActive].map((submission) => <SubmissionCard submission={submission} key={submission.id} />)}
    </motion.div></AnimatePresence>
    {pages.length > 1 && <div className="top-nine-carousel-controls"><button type="button" onClick={() => change(-1)} aria-label="Selecciones anteriores"><ChevronLeft /></button><div>{pages.map((_, index) => <button type="button" className={index === safeActive ? 'active' : ''} onClick={() => setActive(index)} aria-label={`Ver grupo ${index + 1}`} key={index} />)}</div><button type="button" onClick={() => change(1)} aria-label="Selecciones siguientes"><ChevronRight /></button></div>}
  </section>
}
