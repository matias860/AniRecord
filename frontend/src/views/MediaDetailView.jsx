import { highResolutionCover } from '../lib/coverImages.js'
import useModalFocus from '../lib/useModalFocus.js'
import { useEffect, useState } from 'react'
import { ArrowLeft, Building2, CalendarDays, ChevronLeft, ChevronRight, ListPlus, Play, Repeat2, Save, Star, Trash2, UserRound, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import PageState from '../components/common/PageState.jsx'
import StyledSelect from '../components/common/StyledSelect.jsx'
import MediaTags from '../components/media/MediaTags.jsx'
import { addToLibrary, fetchLibrary, removeFromLibrary, updateLibraryEntry } from '../features/library/librarySlice.js'
import { cleanSynopsis, formatReleaseYears, isUnreleasedAnime } from '../lib/media.js'
import { useGetMediaDetailQuery } from '../api/anirecordApi.js'
import { fetchMediaRecommendations } from '../features/recommendations/recommendationsSlice.js'
import { requestLogin } from '../lib/authNavigation.js'

const statusLabels = {
  PLANNED: 'Planeando', IN_PROGRESS: 'En curso', PAUSED: 'En pausa',
  COMPLETED: 'Completado', DROPPED: 'Abandonado',
}
const releaseStatusLabels = {
  RELEASING: 'En emisión', FINISHED: 'Finalizado', NOT_YET_RELEASED: 'Próximamente',
  CANCELLED: 'Cancelado', HIATUS: 'En pausa',
}
const relationLabels = {
  ADAPTATION: 'Adaptación', PREQUEL: 'Precuela', SEQUEL: 'Secuela', PARENT: 'Obra principal',
  SIDE_STORY: 'Historia paralela', CHARACTER: 'Personajes compartidos', SUMMARY: 'Resumen',
  ALTERNATIVE: 'Alternativa', SPIN_OFF: 'Spin-off', OTHER: 'Otra', SOURCE: 'Material original',
  COMPILATION: 'Compilación', CONTAINS: 'Contiene',
}

export default function MediaDetailView() {
  const { anilistId } = useParams()
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const authenticated = useSelector((state) => state.auth.authenticated)
  const { data: detail, error: detailError, isLoading, isFetching, refetch } = useGetMediaDetailQuery(anilistId)
  const library = useSelector((state) => state.library)
  const mediaRecommendations = useSelector((state) => state.recommendations.byMedia[Number(anilistId)]?.entries ?? null)
  const saved = authenticated ? library.items.find((item) => item.anilistId === Number(anilistId)) : null
  const [libraryModal, setLibraryModal] = useState(null)

  useEffect(() => {
    dispatch(fetchMediaRecommendations(anilistId))
  }, [anilistId, dispatch])

  useEffect(() => {
    if (authenticated && library.status === 'idle') dispatch(fetchLibrary())
  }, [authenticated, dispatch, library.status])

  if (!detail) return <PageState fullPage loading={isLoading || isFetching} error={detailError?.data?.message} onRetry={refetch} />
  const unit = detail.mediaType === 'ANIME' ? 'episodios' : detail.mediaFormat === 'NOVEL' ? 'tomos' : 'capítulos'
  const titleLength = [...detail.title].length
  const titleSizeClass = titleLength > 64 ? 'detail-title-extra-long' : titleLength > 38 ? 'detail-title-long' : ''
  const statusOptions = Object.entries(statusLabels).map(([value, label]) => ({
    value,
    label: value === 'IN_PROGRESS' ? (detail.mediaType === 'ANIME' ? 'Viendo' : 'Leyendo')
      : value === 'COMPLETED' ? (detail.mediaType === 'ANIME' ? 'Visto' : 'Leído') : label,
  }))

  return <article className="detail-page">
    <div className="detail-banner" style={{ backgroundImage: `url(${detail.bannerImage || detail.coverImage})` }}><span className="detail-banner-shade" /><Link className="back-link" to={-1}><ArrowLeft /> Volver</Link></div>
    <div className="detail-content">
      <img className="detail-cover" src={highResolutionCover(detail.coverImage)} decoding="async" onError={(event) => {
        const image = event.currentTarget
        if (!image.dataset.originalFallback && image.src !== detail.coverImage) {
          image.dataset.originalFallback = 'true'
          image.src = detail.coverImage
        }
      }} alt={`Portada de ${detail.title}`} />
      <div className={`detail-copy${titleSizeClass ? ' detail-copy-long-title' : ''}`}>
        <div className="detail-primary"><span className="eyebrow">{publicationLabel(detail)} · {releaseStatusLabels[detail.releaseStatus] || detail.releaseStatus?.replaceAll('_', ' ')}</span><h1 className={titleSizeClass}>{detail.title}</h1>{!saved && <button className="primary-button" onClick={() => authenticated ? setLibraryModal('add') : requestLogin(navigate, location)}><ListPlus /> Agregar a la biblioteca</button>}</div>
        <div className="detail-information-strip">
          {!!mediaRecommendations?.length && <div className="detail-recommended-by"><span>Recomendado por:</span>{mediaRecommendations.slice(0, 4).map((recommendation) => <span className="detail-recommender" key={recommendation.id}>{recommendation.recommenderAvatar ? <img src={recommendation.recommenderAvatar} alt="" /> : <i><UserRound /></i>}<b>{recommendation.recommenderName}</b></span>)}</div>}
          {detail.romajiTitle && detail.romajiTitle !== detail.title && <p className="detail-romaji">{detail.romajiTitle}</p>}
          <div className="detail-meta"><span><CalendarDays /> {formatReleaseYears(detail)}</span><span><ListPlus /> {detail.totalUnits ?? '?'} {unit}</span>{saved && <><span className="detail-status-chip">{statusOptions.find((item) => item.value === saved.trackingStatus)?.label}</span><span className="detail-score-chip"><Star /> {saved.personalScore != null ? `${saved.personalScore}/10` : 'Sin puntaje'}</span></>}</div>
        </div>
      </div>
      {saved && <div className="detail-tracking-row"><SavedTrackingEditor unreleased={isUnreleasedAnime(detail)} saved={saved} mediaType={detail.mediaType} unit={unit} total={detail.totalUnits} statusOptions={statusOptions} loading={library.mutationStatus === 'loading'} onRemove={() => setLibraryModal('remove')} /></div>}
      {(!!detail.genres?.length || !!detail.tags?.length || !!detail.streamingLinks?.length) && <div className="detail-discovery-row">
        {(!!detail.genres?.length || !!detail.tags?.length) && <div className="detail-tags"><span className="eyebrow">Géneros y categorías</span><MediaTags genres={detail.genres} tags={detail.tags} mediaType={detail.mediaType} /></div>}
        {!!detail.streamingLinks?.length && <StreamingLinks links={detail.streamingLinks} />}
      </div>}
      <div className="detail-description"><span className="eyebrow">Sinopsis</span><p>{cleanSynopsis(detail.synopsis) || 'Esta serie no tiene descripción.'}</p></div>
      {detail.musicVideoUrl && <MusicVideoPlayer url={detail.musicVideoUrl} title={detail.title} />}
      {!!detail.credits?.length && <MediaCredits credits={detail.credits} mediaType={detail.mediaType} />}
    </div>
    {(!!detail.relations?.length || !!detail.characters?.length) && <div className={`detail-extras${!detail.relations?.length || !detail.characters?.length ? ' single-section' : ''}`}>
      {!!detail.relations?.length && <section className="relations"><span className="eyebrow">Universo</span><h2>Relaciones</h2><RelationCarousel key={detail.anilistId} relations={detail.relations} /></section>}
      {!!detail.characters?.length && <CharacterCarousel key={detail.anilistId} characters={detail.characters} />}
    </div>}
    <AnimatePresence>{libraryModal && <LibraryModal mode={libraryModal} detail={detail} saved={saved} statusOptions={statusOptions} loading={library.mutationStatus === 'loading'} onClose={() => setLibraryModal(null)} />}</AnimatePresence>
  </article>
}

function StreamingLinks({ links }) {
  return <section className="streaming-links">
    <span className="eyebrow">Dónde ver</span>
    <div className="streaming-links-grid">{links.map((link) => {
      const details = [link.site || 'Ver online', link.notes, link.language].filter(Boolean).join(' · ')
      return <a
        className="streaming-link-logo"
        href={link.url}
        key={`${link.site}-${link.url}`}
        target="_blank"
        rel="noopener noreferrer"
        aria-label={`Ver en ${details}`}
        data-label={details}
        style={{ '--streaming-color': link.color || 'var(--accent)' }}
      >
        {link.icon ? <img src={link.icon} alt="" /> : <Play />}
      </a>
    })}</div>
  </section>
}

function MusicVideoPlayer({ url, title }) {
  const embedUrl = youtubeEmbedUrl(url)
  if (!embedUrl) return null
  return <section className="music-video-player"><div><span className="eyebrow">Video oficial</span><h2>Music video</h2></div><div className="music-video-frame"><iframe src={embedUrl} title={`Video musical de ${title}`} loading="lazy" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerPolicy="strict-origin-when-cross-origin" allowFullScreen /></div></section>
}

function youtubeEmbedUrl(value) {
  try {
    const url = new URL(value)
    const host = url.hostname.replace(/^www\./, '')
    const id = host === 'youtu.be' ? url.pathname.split('/').filter(Boolean)[0]
      : ['youtube.com', 'm.youtube.com'].includes(host) ? url.searchParams.get('v') || url.pathname.match(/^\/(?:embed|shorts)\/([^/?]+)/)?.[1]
        : null
    return id && /^[\w-]{6,}$/.test(id) ? `https://www.youtube-nocookie.com/embed/${id}` : null
  } catch { return null }
}

function MediaCredits({ credits, mediaType }) {
  return <section className="media-credits">
    <div className="media-credits-heading"><span className="eyebrow">Créditos</span><h2>{mediaType === 'ANIME' ? 'Equipo creativo' : 'Autoría'}</h2></div>
    <div className="media-credits-grid">
      {credits.map((credit) => {
        const content = <>{credit.creditType === 'STUDIO'
          ? <span className="media-credit-studio"><Building2 /></span>
          : credit.image ? <img src={credit.image} alt={credit.name} loading="lazy" decoding="async" /> : <span className="media-credit-studio"><UserRound /></span>}
          <span><strong>{credit.name}</strong><small>{credit.roles.join(' · ')}</small></span></>
        return credit.creditType === 'PERSON'
          ? <Link className="media-credit-card" key={`${credit.creditType}-${credit.anilistId}`} to={`/profesionales/${credit.anilistId}`}>{content}</Link>
          : <article className="media-credit-card studio" key={`${credit.creditType}-${credit.anilistId}`}>{content}</article>
      })}
    </div>
  </section>
}

function publicationLabel(media) {
  if (media.mediaFormat === 'MUSIC') return 'Music video'
  if (media.mediaType === 'ANIME') return 'Anime'
  if (media.mediaFormat === 'NOVEL') return 'Light novel'
  if (media.mediaFormat === 'ONE_SHOT') return 'One-shot'
  return { KR: 'Manhwa', CN: 'Manhua', JP: 'Manga' }[media.countryOfOrigin] || 'Manga'
}

function RelationCarousel({ relations }) {
  const [page, setPage] = useState(0)
  const pages = Math.ceil(relations.length / 5)
  const visible = relations.slice(page * 5, page * 5 + 5)
  return <div className="relation-carousel">
    <AnimatePresence mode="wait" initial={false}><motion.div className="relation-page" key={page} initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -18 }} transition={{ duration: .22, ease: 'easeOut' }}>{visible.map((relation) => <Link key={`${relation.anilistId}-${relation.relationType}`} to={`/media/${relation.anilistId}`}><img src={relation.coverImage} alt={`Portada de ${relation.title}`} loading="lazy" decoding="async" /><span><small>{relationLabels[relation.relationType] || relation.relationType?.replaceAll('_', ' ')}{relation.format === 'MUSIC' ? ' · Music video' : ''}</small>{relation.title}</span></Link>)}</motion.div></AnimatePresence>
    {pages > 1 && <div className="relation-controls"><button disabled={page === 0} onClick={() => setPage((current) => current - 1)} aria-label="Relaciones anteriores"><ChevronLeft /></button><span>{page + 1} / {pages}</span><button disabled={page === pages - 1} onClick={() => setPage((current) => current + 1)} aria-label="Relaciones siguientes"><ChevronRight /></button></div>}
  </div>
}

function CharacterCarousel({ characters }) {
  const [page, setPage] = useState(0)
  const [role, setRole] = useState(() => characters.some((character) => character.role === 'MAIN') ? 'MAIN' : 'SUPPORTING')
  const roleTotals = {
    MAIN: characters.filter((character) => character.role === 'MAIN').length,
    SUPPORTING: characters.filter((character) => character.role === 'SUPPORTING').length,
  }
  const filtered = characters.filter((character) => character.role === role)
  const pages = Math.max(1, Math.ceil(filtered.length / 5))
  const visible = filtered.slice(page * 5, page * 5 + 5)
  const changeRole = (nextRole) => { setRole(nextRole); setPage(0) }
  return <section className="characters-section">
    <div className="characters-heading"><div><span className="eyebrow">Reparto</span><h2>Personajes</h2></div><div className="character-role-switch" role="group" aria-label="Tipo de personaje"><button type="button" className={role === 'MAIN' ? 'active' : ''} aria-pressed={role === 'MAIN'} onClick={() => changeRole('MAIN')}>Principales <span>{roleTotals.MAIN}</span></button><button type="button" className={role === 'SUPPORTING' ? 'active' : ''} aria-pressed={role === 'SUPPORTING'} onClick={() => changeRole('SUPPORTING')}>Secundarios <span>{roleTotals.SUPPORTING}</span></button></div></div>
    <div className="character-carousel">
    <AnimatePresence mode="wait" initial={false}><motion.div className="character-page" key={`${role}-${page}`} initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -18 }} transition={{ duration: .22, ease: 'easeOut' }}>{visible.length ? visible.map((character) => <article key={character.anilistId}><Link className="character-image-link" to={`/personajes/${character.anilistId}`}><img src={character.image} alt={character.name} loading="lazy" decoding="async" /></Link><span><Link className="character-name-link" to={`/personajes/${character.anilistId}`}><strong>{character.name}</strong></Link><small>{role === 'MAIN' ? 'Principal' : 'Secundario'}</small>{character.voiceActors?.[0] && <Link className="character-voice-link" to={`/seiyuus/${character.voiceActors[0].anilistId}`}>{character.voiceActors[0].name}</Link>}</span></article>) : <p className="character-empty">No hay personajes en esta categoría.</p>}</motion.div></AnimatePresence>
    {pages > 1 && <div className="character-controls"><button disabled={page === 0} onClick={() => setPage((current) => current - 1)} aria-label="Personajes anteriores"><ChevronLeft /></button><span>{page + 1} / {pages}</span><button disabled={page === pages - 1} onClick={() => setPage((current) => current + 1)} aria-label="Personajes siguientes"><ChevronRight /></button></div>}
    </div>
  </section>
}

function SavedTrackingEditor({ unreleased, saved, mediaType, unit, total, statusOptions, loading, onRemove }) {
  const dispatch = useDispatch()
  const [form, setForm] = useState(() => ({
    trackingStatus: saved.trackingStatus,
    progress: saved.progress,
    personalScore: saved.personalScore ?? '',
    rewatchCount: saved.rewatchCount ?? 0,
  }))
  const [message, setMessage] = useState('')

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
    } catch { /* El middleware global muestra el error en un toast. */ }
  }

  return <TrackingEditor unreleased={unreleased} form={form} setForm={setForm} mediaType={mediaType} unit={unit} total={total} statusOptions={statusOptions} loading={loading} message={message} onSave={saveTracking} onRemove={onRemove} />
}

function TrackingEditor({ unreleased, form, setForm, mediaType, unit, total, statusOptions, loading, message, onSave, onRemove }) {
  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  return <section className="tracking-editor">
    <div className="tracking-editor-heading"><div><span className="eyebrow">Mi biblioteca</span><h2>Seguimiento</h2></div><span className="saved-label">Guardado en tu biblioteca</span></div>
    <div className="tracking-fields">
      <label><span>Estado</span><StyledSelect ariaLabel="Estado de seguimiento" value={form.trackingStatus} onChange={(value) => update('trackingStatus', value)} options={statusOptions} /></label>
      <label><span>{unreleased ? "Próximamente · sin episodios disponibles" : `Progreso (${unit})`}</span><input disabled={unreleased} type="number" min="0" max={total ?? undefined} value={form.progress} onChange={(event) => update('progress', event.target.value)} /></label>
      <label className="detail-score-range"><span>Puntaje personal <b>{form.personalScore === '' ? 'Sin puntaje' : `${form.personalScore}/10`}</b></span><div><input aria-label="Puntaje personal" type="range" min="0" max="10" step="0.5" value={form.personalScore === '' ? 0 : form.personalScore} onChange={(event) => update('personalScore', event.target.value)} />{form.personalScore !== '' && <button type="button" onClick={() => update('personalScore', '')}>Quitar</button>}</div></label>
      <label><span><Repeat2 /> {mediaType === 'ANIME' ? 'Rewatches' : 'Releídas'}</span><input type="number" min="0" value={form.rewatchCount} onChange={(event) => update('rewatchCount', event.target.value)} /></label>
    </div>
    <div className="tracking-actions"><span>{message}</span><button className="tracking-remove-button" type="button" disabled={loading} onClick={onRemove} aria-label="Quitar de la biblioteca" title="Quitar de la biblioteca"><Trash2 /></button><button className="tracking-save-button" type="button" disabled={loading} onClick={onSave} aria-label={loading ? 'Guardando cambios' : 'Guardar cambios'} title="Guardar cambios"><Save /></button></div>
  </section>
}

function LibraryModal({ mode, detail, saved, statusOptions, loading, onClose }) {
  const dialogRef = useModalFocus(true)
  const dispatch = useDispatch()
  const [status, setStatus] = useState('PLANNED')
  useEffect(() => {
    const closeOnEscape = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose])
  const confirm = async () => {
    try {
      if (mode === 'add') await dispatch(addToLibrary({ anilistId: detail.anilistId, trackingStatus: status })).unwrap()
      else await dispatch(removeFromLibrary(saved.id)).unwrap()
      onClose()
    } catch { /* El toast global presenta el error y permite volver a intentar. */ }
  }
  return <motion.div className="modal-backdrop" role="presentation" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <motion.section ref={dialogRef} className="library-action-modal" role="dialog" aria-modal="true" aria-labelledby="library-modal-title" initial={{ y: 18, scale: .97 }} animate={{ y: 0, scale: 1 }} exit={{ y: 12, scale: .98 }}>
      <button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar"><X /></button>
      <span className="eyebrow">Mi biblioteca</span><h2 id="library-modal-title">{mode === 'add' ? 'Agregar a la biblioteca' : 'Quitar de la biblioteca'}</h2>
      {mode === 'add' ? <><p>Elige cómo deseas comenzar a seguir <strong>{detail.title}</strong>.</p><label><span>Estado inicial</span><StyledSelect ariaLabel="Estado inicial" value={status} onChange={setStatus} options={statusOptions} /></label></> : <p><strong>{detail.title}</strong> y todo su progreso personal se quitarán de tu biblioteca.</p>}
      <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button type="button" className={mode === 'remove' ? 'danger-button' : 'primary-button'} disabled={loading} onClick={confirm}>{mode === 'remove' ? <Trash2 /> : <ListPlus />}{loading ? 'Procesando…' : mode === 'remove' ? 'Quitar' : 'Agregar'}</button></div>
    </motion.section>
  </motion.div>
}
