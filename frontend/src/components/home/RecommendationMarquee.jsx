import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, Sparkles, UserRound } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'

const ROTATION_MS = 10000

export default function RecommendationMarquee({ entries, loading }) {
  const [active, setActive] = useState(0)
  const safeActive = entries.length ? active % entries.length : 0
  const recommendation = entries[safeActive]
  const move = (direction) => setActive((current) => (current + direction + entries.length) % entries.length)

  useEffect(() => {
    if (entries.length < 2) return undefined
    const timer = window.setInterval(() => setActive((current) => (current + 1) % entries.length), ROTATION_MS)
    return () => window.clearInterval(timer)
  }, [entries.length, safeActive])

  return <section className="recommendations-section">
    <div className="section-heading"><div><span className="eyebrow">Elegidos por la comunidad</span><h2><Sparkles /> Recomendados esta semana</h2></div>{entries.length > 0 && <span>{entries.length} {entries.length === 1 ? 'recomendación' : 'recomendaciones'}</span>}</div>
    {loading ? <div className="recommendations-empty">Cargando recomendaciones…</div>
      : !recommendation ? <div className="recommendations-empty"><Sparkles /><strong>Todavía no hay recomendaciones</strong><span>Puedes inaugurar la selección de esta semana.</span></div>
        : <div className="recommendation-carousel">
          <AnimatePresence mode="wait" initial={false}><motion.div key={recommendation.id} initial={{ opacity: 0, x: 40 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -40 }} transition={{ duration: .34, ease: 'easeOut' }}>
            <Link className={`recommendation-slide ${recommendation.bannerImage ? 'has-banner' : 'cover-fallback'}`} to={`/media/${recommendation.anilistId}`}>
              <span className="recommendation-blur" style={{ backgroundImage: `url(${recommendation.bannerImage || recommendation.coverImage})` }} />
              <img src={recommendation.bannerImage || recommendation.coverImage} alt={`Banner de ${recommendation.title}`} />
              <span className="recommendation-shade" />
              <div className="recommendation-copy"><small>{recommendation.mediaType === 'ANIME' ? 'Anime recomendado' : 'Manga recomendado'}</small><strong>{recommendation.title}</strong><span className="recommendation-author">{recommendation.recommenderAvatar ? <img src={recommendation.recommenderAvatar} alt="" /> : <i><UserRound /></i>}<b>{recommendation.recommenderName}</b></span></div>
              {entries.length > 1 && <span className="carousel-progress" key={`progress-${recommendation.id}`} />}
            </Link>
          </motion.div></AnimatePresence>
          {entries.length > 1 && <div className="recommendation-controls"><button type="button" onClick={() => move(-1)} aria-label="Recomendación anterior"><ChevronLeft /></button><span>{safeActive + 1} / {entries.length}</span><button type="button" onClick={() => move(1)} aria-label="Recomendación siguiente"><ChevronRight /></button></div>}
        </div>}
  </section>
}
