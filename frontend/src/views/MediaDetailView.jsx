import { useEffect, useState } from 'react'
import { ArrowLeft, CalendarDays, ChevronLeft, ChevronRight, ListPlus, Repeat2, Save, Star } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useParams } from 'react-router-dom'
import PageState from '../components/common/PageState.jsx'
import StyledSelect from '../components/common/StyledSelect.jsx'
import MediaTags from '../components/media/MediaTags.jsx'
import { addToLibrary, fetchLibrary, updateLibraryEntry } from '../features/library/librarySlice.js'
import { cleanSynopsis } from '../lib/media.js'
import { clearDetail, fetchMediaDetail } from '../features/search/searchSlice.js'

const statusLabels = {
  PLANNED: 'Planeado', IN_PROGRESS: 'En curso', PAUSED: 'Pausado',
  COMPLETED: 'Completado', DROPPED: 'Abandonado',
}

export default function MediaDetailView() {
  const { anilistId } = useParams()
  const dispatch = useDispatch()
  const { detail, detailStatus, error } = useSelector((state) => state.search)
  const library = useSelector((state) => state.library)
  const saved = library.items.find((item) => item.anilistId === Number(anilistId))
  const [form, setForm] = useState({ trackingStatus: 'PLANNED', progress: 0, personalScore: '', rewatchCount: 0 })
  const [message, setMessage] = useState('')

  useEffect(() => {
    dispatch(fetchMediaDetail(anilistId))
    return () => dispatch(clearDetail())
  }, [anilistId, dispatch])

  useEffect(() => {
    if (library.status === 'idle') dispatch(fetchLibrary())
  }, [dispatch, library.status])

  useEffect(() => {
    if (saved) setForm({
      trackingStatus: saved.trackingStatus,
      progress: saved.progress,
      personalScore: saved.personalScore ?? '',
      rewatchCount: saved.rewatchCount ?? 0,
    })
  }, [saved])

  if (detailStatus !== 'succeeded') return <PageState loading={detailStatus === 'loading' || detailStatus === 'idle'} error={error} />
  const unit = detail.mediaType === 'ANIME' ? 'episodios' : 'capítulos'
  const statusOptions = Object.entries(statusLabels).map(([value, label]) => ({
    value,
    label: value === 'IN_PROGRESS' ? (detail.mediaType === 'ANIME' ? 'Viendo' : 'Leyendo')
      : value === 'COMPLETED' ? (detail.mediaType === 'ANIME' ? 'Visto' : 'Leído') : label,
  }))

  const saveTracking = async () => {
    setMessage('')
    try {
      await dispatch(updateLibraryEntry({ id: saved.id, changes: {
        trackingStatus: form.trackingStatus,
        progress: Number(form.progress),
        rewatchCount: Number(form.rewatchCount),
        personalScore: form.personalScore === '' ? null : Number(form.personalScore),
        clearScore: form.personalScore === '',
      } })).unwrap()
      setMessage('Cambios guardados')
    } catch (saveError) {
      setMessage(saveError?.message || 'No se pudieron guardar los cambios')
    }
  }

  return (
    <article className="detail-page">
      <div className="detail-banner" style={{ backgroundImage: `linear-gradient(90deg, rgba(10,12,20,.96), rgba(10,12,20,.48)), url(${detail.bannerImage || detail.coverImage})` }}><Link className="back-link" to={-1}><ArrowLeft /> Volver</Link></div>
      <div className="detail-content"><img className="detail-cover" src={detail.coverImage} alt={`Portada de ${detail.title}`} /><div className={`detail-copy ${saved ? 'with-tracking-card' : ''}`}><div className="detail-primary"><span className="eyebrow">{detail.mediaType === 'ANIME' ? 'Anime' : 'Manga'} · {detail.releaseStatus?.replaceAll('_', ' ')}</span><h1>{detail.title}</h1><div className="detail-meta"><span><CalendarDays /> {detail.releaseYear || 'Sin fecha'}</span><span><ListPlus /> {detail.totalUnits ?? '?'} {unit}</span>{saved && <><span className="detail-status-chip">{statusOptions.find((item) => item.value === saved.trackingStatus)?.label}</span><span className="detail-score-chip"><Star /> {saved.personalScore != null ? `${saved.personalScore}/10` : 'Sin puntaje'}</span></>}</div>{!saved && <button className="primary-button" disabled={library.mutationStatus === 'loading'} onClick={() => dispatch(addToLibrary({ anilistId: detail.anilistId }))}><ListPlus /> Agregar a pendientes</button>}</div>{saved && <TrackingEditor form={form} setForm={setForm} mediaType={detail.mediaType} unit={unit} total={detail.totalUnits} statusOptions={statusOptions} loading={library.mutationStatus === 'loading'} message={message} onSave={saveTracking} />}</div>{!!detail.tags?.length && <div className="detail-tags"><span className="eyebrow">Categorías principales</span><MediaTags tags={detail.tags} /></div>}<div className="detail-description"><span className="eyebrow">Sinopsis</span><p>{cleanSynopsis(detail.synopsis) || 'No hay una sinopsis disponible.'}</p></div></div>
      {(!!detail.relations?.length || !!detail.characters?.length) && <div className="detail-extras">
        {!!detail.relations?.length && <section className="relations"><span className="eyebrow">Universo</span><h2>Relaciones</h2><RelationCarousel key={detail.anilistId} relations={detail.relations} /></section>}
        {!!detail.characters?.length && <section className="characters-section"><span className="eyebrow">Reparto principal</span><h2>Personajes</h2><CharacterCarousel key={detail.anilistId} characters={detail.characters} /></section>}
      </div>}
    </article>
  )
}

function RelationCarousel({ relations }) {
  const [page, setPage] = useState(0)
  const pages = Math.ceil(relations.length / 3)
  const visible = relations.slice(page * 3, page * 3 + 3)
  return <div className="relation-carousel">
    <AnimatePresence mode="wait" initial={false}><motion.div className="relation-page" key={page} initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -18 }} transition={{ duration: .22, ease: 'easeOut' }}>{visible.map((relation) => <Link key={`${relation.anilistId}-${relation.relationType}`} to={`/media/${relation.anilistId}`}><img src={relation.coverImage} alt={`Portada de ${relation.title}`} /><span><small>{relation.relationType?.replaceAll('_', ' ')}</small>{relation.title}</span></Link>)}</motion.div></AnimatePresence>
    {pages > 1 && <div className="relation-controls"><button disabled={page === 0} onClick={() => setPage((current) => current - 1)} aria-label="Tres relaciones anteriores"><ChevronLeft /></button><span>{page + 1} / {pages}</span><button disabled={page === pages - 1} onClick={() => setPage((current) => current + 1)} aria-label="Tres relaciones siguientes"><ChevronRight /></button></div>}
  </div>
}

function CharacterCarousel({ characters }) {
  const [page, setPage] = useState(0)
  const pages = Math.ceil(characters.length / 3)
  const visible = characters.slice(page * 3, page * 3 + 3)
  return <div className="character-carousel">
    <AnimatePresence mode="wait" initial={false}><motion.div className="character-page" key={page} initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -18 }} transition={{ duration: .22, ease: 'easeOut' }}>{visible.map((character) => <article key={character.anilistId}><img src={character.image} alt={character.name} /><span><strong>{character.name}</strong><small>Principal</small></span></article>)}</motion.div></AnimatePresence>
    {pages > 1 && <div className="character-controls"><button disabled={page === 0} onClick={() => setPage((current) => current - 1)} aria-label="Tres personajes anteriores"><ChevronLeft /></button><span>{page + 1} / {pages}</span><button disabled={page === pages - 1} onClick={() => setPage((current) => current + 1)} aria-label="Tres personajes siguientes"><ChevronRight /></button></div>}
  </div>
}

function TrackingEditor({ form, setForm, mediaType, unit, total, statusOptions, loading, message, onSave }) {
  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  return <section className="tracking-editor">
    <div className="tracking-editor-heading"><div><span className="eyebrow">Mi biblioteca</span><h2>Seguimiento personal</h2></div><span className="saved-label">Guardado en tu biblioteca</span></div>
    <div className="tracking-fields">
      <label><span>Estado</span><StyledSelect ariaLabel="Estado de seguimiento" value={form.trackingStatus} onChange={(value) => update('trackingStatus', value)} options={statusOptions} /></label>
      <label><span>Progreso ({unit})</span><input type="number" min="0" max={total ?? undefined} value={form.progress} onChange={(event) => update('progress', event.target.value)} /></label>
      <label><span>Puntaje personal</span><input type="number" min="0" max="10" step="0.5" placeholder="Sin puntaje" value={form.personalScore} onChange={(event) => update('personalScore', event.target.value)} /></label>
      <label><span><Repeat2 /> {mediaType === 'ANIME' ? 'Rewatches' : 'Releídas'}</span><input type="number" min="0" value={form.rewatchCount} onChange={(event) => update('rewatchCount', event.target.value)} /></label>
    </div>
    <div className="tracking-actions"><span>{message}</span><button className="primary-button" disabled={loading} onClick={onSave}><Save /> {loading ? 'Guardando…' : 'Guardar cambios'}</button></div>
  </section>
}
