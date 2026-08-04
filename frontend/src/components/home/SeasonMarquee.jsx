import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function SeasonMarquee({ entries, loading, label, canGoPrevious, canGoNext, onPrevious, onNext }) {
  const loop = [...entries, ...entries]
  return (
    <section className="season-section">
      <div className="section-heading"><div><span className="eyebrow">Descubrí</span><h2>Animes de {label}</h2></div><div className="season-actions"><button disabled={!canGoPrevious || loading} onClick={onPrevious}><ChevronLeft /></button><button disabled={!canGoNext || loading} onClick={onNext}><ChevronRight /></button><Link to="/catalogo/anime">Ver catálogo</Link></div></div>
      {loading && <div className="season-loading">Cargando temporada…</div>}
      {!loading && !entries.length && <div className="season-loading">AniList todavía no publicó títulos para esta temporada.</div>}
      {!loading && !!entries.length && <div className="marquee-mask"><div className="season-marquee">
        {loop.map((media, index) => <Link className="season-card" to={`/media/${media.anilistId}`} key={`${media.anilistId}-${index}`} aria-hidden={index >= entries.length}>
          <img src={media.coverImage} alt={index < entries.length ? media.title : ''} />
          <span><strong>{media.title}</strong><small>{episodeLabel(media)}</small></span>
        </Link>)}
      </div></div>}
    </section>
  )
}

function episodeLabel(media) {
  const current = media.nextAiringEpisode?.episode ? Math.max(0, media.nextAiringEpisode.episode - 1) : null
  if (current != null && media.totalUnits != null) return `${current} episodios (${media.totalUnits} totales)`
  if (current != null) return `${current} episodios emitidos`
  return media.totalUnits != null ? `${media.totalUnits} episodios totales` : 'Cantidad por confirmar'
}
