import { useEffect } from 'react'
import { ArrowLeft, CalendarDays, ListPlus, Star } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useParams } from 'react-router-dom'
import PageState from '../components/common/PageState.jsx'
import { addToLibrary } from '../features/library/librarySlice.js'
import { cleanSynopsis } from '../lib/media.js'
import { clearDetail, fetchMediaDetail } from '../features/search/searchSlice.js'

export default function MediaDetailView() {
  const { anilistId } = useParams()
  const dispatch = useDispatch()
  const { detail, detailStatus, error } = useSelector((state) => state.search)
  const library = useSelector((state) => state.library)
  const saved = library.items.find((item) => item.anilistId === Number(anilistId))
  useEffect(() => { dispatch(fetchMediaDetail(anilistId)); return () => dispatch(clearDetail()) }, [anilistId, dispatch])
  if (detailStatus !== 'succeeded') return <PageState loading={detailStatus === 'loading' || detailStatus === 'idle'} error={error} />
  return (
    <article className="detail-page">
      <div className="detail-banner" style={{ backgroundImage: `linear-gradient(90deg, rgba(10,12,20,.96), rgba(10,12,20,.48)), url(${detail.bannerImage || detail.coverImage})` }}><Link className="back-link" to={-1}><ArrowLeft /> Volver</Link></div>
      <div className="detail-content"><img className="detail-cover" src={detail.coverImage} alt={`Portada de ${detail.title}`} /><div className="detail-copy"><span className="eyebrow">{detail.mediaType === 'ANIME' ? 'Anime' : 'Manga'} · {detail.releaseStatus?.replaceAll('_', ' ')}</span><h1>{detail.title}</h1><div className="detail-meta"><span><CalendarDays /> {detail.releaseYear || 'Sin fecha'}</span><span><ListPlus /> {detail.totalUnits ?? '?'} {detail.mediaType === 'ANIME' ? 'episodios' : 'capítulos'}</span>{saved?.personalScore != null && <span><Star /> {saved.personalScore}/10</span>}</div><p>{cleanSynopsis(detail.synopsis) || 'No hay una sinopsis disponible.'}</p>{saved ? <span className="saved-label">Ya está en tu biblioteca</span> : <button className="primary-button" disabled={library.mutationStatus === 'loading'} onClick={() => dispatch(addToLibrary({ anilistId: detail.anilistId }))}><ListPlus /> Agregar a pendientes</button>}</div></div>
      {!!detail.relations?.length && <section className="relations"><span className="eyebrow">Universo</span><h2>Relaciones</h2><div>{detail.relations.map((relation) => <Link key={`${relation.anilistId}-${relation.relationType}`} to={`/media/${relation.anilistId}`}><img src={relation.coverImage} alt="" /><span><small>{relation.relationType?.replaceAll('_', ' ')}</small>{relation.title}</span></Link>)}</div></section>}
    </article>
  )
}
