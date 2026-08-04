import { Link } from 'react-router-dom'

export default function SeasonMarquee({ entries }) {
  if (!entries.length) return null
  const loop = [...entries, ...entries]
  return (
    <section className="season-section">
      <div className="section-heading"><div><span className="eyebrow">Descubrí</span><h2>Animes de la temporada</h2></div><Link to="/buscar">Explorar catálogo</Link></div>
      <div className="marquee-mask"><div className="season-marquee">
        {loop.map((media, index) => <Link className="season-card" to={`/media/${media.anilistId}`} key={`${media.anilistId}-${index}`} aria-hidden={index >= entries.length}>
          <img src={media.coverImage} alt={index < entries.length ? media.title : ''} />
          <span><strong>{media.title}</strong><small>{media.totalUnits ?? '?'} episodios</small></span>
        </Link>)}
      </div></div>
    </section>
  )
}
