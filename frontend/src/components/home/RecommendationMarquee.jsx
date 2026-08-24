import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, CircleAlert, UserRound } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'

const ROTATION_MS = 10000
const FALLBACK_ARTWORK = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="1200" height="675" viewBox="0 0 1200 675"%3E%3Cdefs%3E%3ClinearGradient id="g" x1="0" y1="0" x2="1" y2="1"%3E%3Cstop stop-color="%23241d42"/%3E%3Cstop offset="1" stop-color="%230b101f"/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width="1200" height="675" fill="url(%23g)"/%3E%3Cpath d="M520 205h160c33 0 60 27 60 60v175H460V265c0-33 27-60 60-60Z" fill="none" stroke="%23a78bfa" stroke-width="20"/%3E%3Cpath d="M600 220v200M480 270c38 0 81 12 120 41 39-29 82-41 120-41" fill="none" stroke="%23a78bfa" stroke-width="16"/%3E%3C/svg%3E'

function RecommendationSlide({ recommendation, entriesCount }) {
  const preferredArtwork = recommendation.bannerImage || recommendation.coverImage || FALLBACK_ARTWORK
  const [artwork, setArtwork] = useState(preferredArtwork)

  const useFallback = () => setArtwork((current) => {
    if (current !== recommendation.coverImage && recommendation.coverImage) return recommendation.coverImage
    return FALLBACK_ARTWORK
  })

  return <Link className={`recommendation-slide ${recommendation.bannerImage && artwork === recommendation.bannerImage ? 'has-banner' : 'cover-fallback'}`} to={`/media/${recommendation.anilistId}`}>
    <span className="recommendation-blur" style={{ backgroundImage: `url(${artwork})` }} />
    <img src={artwork} onError={useFallback} alt={`Banner de ${recommendation.title}`} />
    <span className="recommendation-shade" />
    <div className="recommendation-copy"><small>{recommendation.mediaType === 'ANIME' ? 'Anime recomendado' : 'Manga recomendado'}</small><strong>{recommendation.title}</strong><span className="recommendation-author">{recommendation.recommenderAvatar ? <img src={recommendation.recommenderAvatar} onError={(event) => { event.currentTarget.remove() }} alt="" /> : <i><UserRound /></i>}<b>{recommendation.recommenderName}</b></span></div>
    {entriesCount > 1 && <span className="carousel-progress" key={`progress-${recommendation.id}`} />}
  </Link>
}

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
    <div className="section-heading"><div><span className="eyebrow">Elegidos por la comunidad</span><h2><CircleAlert /> Recomendados esta semana</h2></div>{entries.length > 0 && <span>{entries.length} {entries.length === 1 ? 'recomendación' : 'recomendaciones'}</span>}</div>
    {loading ? <div className="recommendations-empty">Cargando recomendaciones…</div>
      : !recommendation ? <div className="recommendations-empty"><CircleAlert /><strong>Todavía no hay recomendaciones</strong><span>Puedes inaugurar la selección de esta semana.</span></div>
        : <div className="recommendation-carousel">
          <AnimatePresence mode="wait" initial={false}><motion.div key={recommendation.id} initial={{ opacity: 0, x: 40 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -40 }} transition={{ duration: .34, ease: 'easeOut' }}>
            <RecommendationSlide recommendation={recommendation} entriesCount={entries.length} />
          </motion.div></AnimatePresence>
          {entries.length > 1 && <div className="featured-controls"><button type="button" onClick={() => move(-1)} aria-label="Recomendación anterior"><ChevronLeft /></button>{entries.map((item, index) => <button type="button" className={index === safeActive ? 'active' : ''} onClick={() => setActive(index)} aria-label={`Ver recomendación de ${item.title}`} key={item.id} />)}<button type="button" onClick={() => move(1)} aria-label="Recomendación siguiente"><ChevronRight /></button></div>}
        </div>}
  </section>
}
