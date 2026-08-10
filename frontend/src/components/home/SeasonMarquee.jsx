import { useState } from 'react'
import { ChevronLeft, ChevronRight, Heart } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import ProfileLoader from '../common/ProfileLoader.jsx'

export default function SeasonMarquee({ entries, loading, label, canGoPrevious, canGoNext, onPrevious, onNext }) {
  const featured = entries.slice(0, 3)
  const [active, setActive] = useState(0)
  const move = (direction) => setActive((current) => (current + direction + featured.length) % featured.length)
  const media = featured[active]

  return <section className="season-section season-featured-section">
    <div className="section-heading"><div><span className="eyebrow">Los más favoritos</span><h2>Destacados de {label}</h2></div><div className="season-actions"><button disabled={!canGoPrevious || loading} onClick={onPrevious} title="Temporada anterior"><ChevronLeft /></button><button disabled={!canGoNext || loading} onClick={onNext} title="Temporada siguiente"><ChevronRight /></button><Link to="/catalogo/anime">Ver catálogo</Link></div></div>
    <AnimatePresence mode="wait" initial={false}>{loading ? <motion.div key={`loading-${label}`} className="season-loading" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}><ProfileLoader compact message="Cargando temporada…" /></motion.div>
      : !media ? <motion.div key={`empty-${label}`} className="season-loading" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>AniList todavía no publicó títulos para esta temporada.</motion.div>
      : <motion.div key={`season-${label}`} className="panorama-carousel" initial={{ opacity: 0, y: 18, filter: 'blur(5px)' }} animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }} exit={{ opacity: 0, y: -12, filter: 'blur(4px)' }} transition={{ duration: .34, ease: 'easeOut' }}>
      <AnimatePresence mode="wait" initial={false}><motion.div key={`${label}-${media.anilistId}`} initial={{ opacity: 0, x: 35 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -35 }} transition={{ duration: .32, ease: 'easeOut' }}>
        <Link className={`panorama-slide ${media.bannerImage ? 'has-banner' : 'cover-fallback'}`} to={`/media/${media.anilistId}`}>
          <span className="panorama-blur" style={{ backgroundImage: `url(${media.bannerImage || media.coverImage})` }} />
          <img src={media.bannerImage || media.coverImage} alt={`Banner de ${media.title}`} />
          <span className="panorama-shade" />
          <div className="panorama-copy"><small>#{active + 1} · Anime de temporada</small><strong>{media.title}</strong><span><Heart /> {media.favourites?.toLocaleString('es-AR') ?? '—'} favoritos</span></div>
        </Link>
      </motion.div></AnimatePresence>
      {featured.length > 1 && <div className="featured-controls"><button onClick={() => move(-1)} aria-label="Destacado anterior"><ChevronLeft /></button>{featured.map((item, index) => <button className={index === active ? 'active' : ''} onClick={() => setActive(index)} aria-label={`Ver ${item.title}`} key={item.anilistId} />)}<button onClick={() => move(1)} aria-label="Destacado siguiente"><ChevronRight /></button></div>}
    </motion.div>}</AnimatePresence>
  </section>
}
