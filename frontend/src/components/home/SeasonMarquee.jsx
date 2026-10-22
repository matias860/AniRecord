import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, Heart } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import ProfileLoader from '../common/ProfileLoader.jsx'

export default function SeasonMarquee({ entries, loading, error, onRetry, label, canGoPrevious, canGoNext, onPrevious, onNext }) {
  const featured = entries.slice(0, 3)
  const labelParts = label.match(/^(.*)\s(\d{4})$/)
  const seasonName = labelParts?.[1] ?? label
  const seasonYear = labelParts?.[2] ?? ''
  const [selection, setSelection] = useState({ label, index: 0 })
  const active = selection.label === label ? selection.index : 0
  const setActive = (next) => setSelection((current) => {
    const currentIndex = current.label === label ? current.index : 0
    return { label, index: typeof next === 'function' ? next(currentIndex) : next }
  })
  const move = (direction) => setActive((current) => (current + direction + featured.length) % featured.length)
  const safeActive = featured.length ? active % featured.length : 0
  const media = featured[safeActive]

  useEffect(() => {
    if (featured.length < 2) return undefined
    const timer = window.setInterval(() => setSelection((current) => ({
      label,
      index: ((current.label === label ? current.index : 0) + 1) % featured.length,
    })), 10000)
    return () => window.clearInterval(timer)
  }, [active, featured.length, label])

  return <section className="season-section season-featured-section">
    <div className="section-heading"><div><span className="eyebrow">Los más favoritos</span><h2 className="animated-season-title"><span>Destacados de</span><span className="animated-season-value"><AnimatePresence initial={false} mode="popLayout"><motion.span key={seasonName} initial={{ y: -12, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 12, opacity: 0 }} transition={{ duration: .22, ease: 'easeOut' }}>{seasonName}</motion.span></AnimatePresence></span>{seasonYear && <span className="animated-season-value"><AnimatePresence initial={false} mode="popLayout"><motion.span key={seasonYear} initial={{ y: -12, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 12, opacity: 0 }} transition={{ duration: .22, ease: 'easeOut' }}>{seasonYear}</motion.span></AnimatePresence></span>}</h2></div><div className="season-actions"><button disabled={!canGoPrevious || loading} onClick={onPrevious} title="Temporada anterior"><ChevronLeft /></button><button disabled={!canGoNext || loading} onClick={onNext} title="Temporada siguiente"><ChevronRight /></button><Link to="/catalogo/anime">Ver catálogo</Link></div></div>
    <AnimatePresence mode="wait" initial={false}>{loading ? <motion.div key={`loading-${label}`} className="season-loading" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}><ProfileLoader compact message="Cargando temporada…" /></motion.div>
      : error ? <motion.div key={`error-${label}`} className="season-loading" role="status" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}><p>{error}</p><button type="button" className="primary-button" onClick={onRetry}>Reintentar temporada</button></motion.div>
      : !media ? <motion.div key={`empty-${label}`} className="season-loading" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>AniList todavía no publicó títulos para esta temporada.</motion.div>
      : <motion.div key={`season-${label}`} className="panorama-carousel" initial={{ opacity: 0, y: 18, filter: 'blur(5px)' }} animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }} exit={{ opacity: 0, y: -12, filter: 'blur(4px)' }} transition={{ duration: .34, ease: 'easeOut' }}>
      <AnimatePresence mode="wait" initial={false}><motion.div key={`${label}-${media.anilistId}`} initial={{ opacity: 0, x: 35 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -35 }} transition={{ duration: .32, ease: 'easeOut' }}>
        <Link className={`panorama-slide ${media.bannerImage ? 'has-banner' : 'cover-fallback'}`} to={`/media/${media.anilistId}`}>
          <span className="panorama-blur" style={{ backgroundImage: `url(${media.bannerImage || media.coverImage})` }} />
          <img src={media.bannerImage || media.coverImage} alt={`Banner de ${media.title}`} loading="lazy" decoding="async" />
          <span className="panorama-shade" />
          <div className="panorama-copy"><small>#{safeActive + 1} · Anime de temporada</small><strong>{media.title}</strong><span><Heart /> {media.favourites?.toLocaleString('es-AR') ?? '—'} favoritos</span></div>
          {featured.length > 1 && <span className="carousel-progress" key={`progress-${media.anilistId}`} />}
        </Link>
      </motion.div></AnimatePresence>
      {featured.length > 1 && <div className="featured-controls"><button onClick={() => move(-1)} aria-label="Destacado anterior"><ChevronLeft /></button>{featured.map((item, index) => <button className={index === safeActive ? 'active' : ''} onClick={() => setActive(index)} aria-label={`Ver ${item.title}`} key={item.anilistId} />)}<button onClick={() => move(1)} aria-label="Destacado siguiente"><ChevronRight /></button></div>}
    </motion.div>}</AnimatePresence>
  </section>
}
