import { Link } from 'react-router-dom'

export default function SeasonMarquee({ entries }) {
  if (!entries.length) return null
  const loop = [...entries, ...entries]
  return (
    <section className="season-section">
      <div className="section-heading"><div><span className="eyebrow">Descubrí</span><h2>Animes de la temporada</h2></div><Link to="/catalogo/anime">Ver catálogo</Link></div>
      <div className="marquee-mask"><div className="season-marquee">
        {loop.map((media, index) => <Link className="season-card" to={`/media/${media.anilistId}`} key={`${media.anilistId}-${index}`} aria-hidden={index >= entries.length}>
          <img src={media.coverImage} alt={index < entries.length ? media.title : ''} />
          <span><strong>{media.title}</strong><small>{episodeLabel(media)}</small></span>
        </Link>)}
      </div></div>
    </section>
  )
}

function episodeLabel(media) {
  const current = media.nextAiringEpisode?.episode ? Math.max(0, media.nextAiringEpisode.episode - 1) : null
  if (current != null && media.totalUnits != null) return `${current} episodios (${media.totalUnits} totales)`
  if (current != null) return `${current} episodios emitidos`
  return media.totalUnits != null ? `${media.totalUnits} episodios totales` : 'Cantidad por confirmar'
}
