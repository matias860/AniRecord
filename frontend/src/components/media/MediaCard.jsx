import { coverSources } from '../../lib/coverImages.js'
import { Eye, Plus, Star } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { memo, useState } from 'react'
import { useSelector } from 'react-redux'
import { getProgressPercent, STATUS_LABELS, isUnreleasedAnime, normalizeMediaId } from '../../lib/media.js'
import MediaTags from './MediaTags.jsx'
import { usePrefetch } from '../../api/anirecordApi.js'
import { preloadRoute } from '../../lib/routeModules.js'
import { adultPresentation, preferredTitle } from '../../lib/contentPreferences.js'

function MediaCard({ media, onIncrement }) {
  const { adultContentMode, titleLanguage } = useSelector((state) => state.profile)
  const [adultRevealed, setAdultRevealed] = useState(false)
  const presentation = adultRevealed ? 'show' : adultPresentation(media, adultContentMode)
  const displayTitle = preferredTitle(media, titleLanguage)
  const progress = media.progress ?? 0
  const mediaId = normalizeMediaId(media.anilistId)
  const prefetchDetail = usePrefetch('getMediaDetail', { ifOlderThan: 300 })
  const preloadDetailRoute = () => {
    preloadRoute(`/media/${media.anilistId}`)
  }
  const prepareDetailNow = () => {
    preloadDetailRoute()
    if (mediaId != null && !navigator.connection?.saveData) prefetchDetail(mediaId)
  }
  const isLiveAnime = media.mediaType === 'ANIME' && media.releaseStatus === 'RELEASING'
  const progressLabel = media.mediaType === 'ANIME' ? 'Episodios' : media.mediaFormat === 'NOVEL' ? 'Tomos' : 'Capítulos'
  const totalLabel = media.totalUnits ?? (media.mediaType === 'MANGA' ? (progress > 0 ? `${progress}+` : 'En publicación') : '?')
  if (presentation === 'hide') return null
  return (
    <article className={`media-card ${presentation === 'blur' ? 'adult-media-blurred' : ''}`}>
      <div className="cover-shell"><Link to={`/media/${media.anilistId}`} onPointerEnter={preloadDetailRoute} onFocus={preloadDetailRoute} onClick={prepareDetailNow} className={`cover-wrap ${media.episodePending ? 'episode-pending' : ''}`}>
        <img src={media.coverImage} srcSet={coverSources(media.coverImage)} sizes="auto, (max-width: 620px) 45vw, 300px" onError={(event) => {
          const image = event.currentTarget
          if (!image.dataset.originalFallback && image.srcset) {
            image.dataset.originalFallback = 'true'
            image.removeAttribute('srcset')
            image.src = media.coverImage
          }
        }} alt={`Portada de ${displayTitle}`} width="230" height="345" loading="lazy" decoding="async" />
        {media.trackingStatus && <span className="status-badge">{STATUS_LABELS[media.trackingStatus]}</span>}
        {media.personalScore != null && <span className="score-badge"><Star /> {media.personalScore}</span>}
        {isLiveAnime && <span className="airing-badge"><i /> Emisión</span>}
      </Link>{presentation === 'blur' && <button type="button" className="adult-reveal" onClick={() => setAdultRevealed(true)}><Eye /> Mostrar</button>}</div>
      <div className="media-card-body">
        <Link to={`/media/${media.anilistId}`} onPointerEnter={preloadDetailRoute} onFocus={preloadDetailRoute} onClick={prepareDetailNow} className="media-title">{presentation === 'blur' ? 'Contenido adulto' : displayTitle}</Link>
        {!media.trackingStatus && <MediaTags tags={media.tags} compact />}
        {media.trackingStatus && (
          <>
            <div className="progress-copy"><span>{progressLabel}</span><b><span className="progress-number"><AnimatePresence initial={false} mode="popLayout"><motion.span key={progress} initial={{ y: -9, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 9, opacity: 0 }} transition={{ duration: .2, ease: 'easeOut' }}>{progress}</motion.span></AnimatePresence></span> / {totalLabel}</b></div>
            <div className="progress-track"><span style={{ width: `${getProgressPercent(progress, media.totalUnits)}%` }} /></div>
            {onIncrement && media.trackingStatus !== 'COMPLETED' && !isUnreleasedAnime(media) && (
              <button className="quick-add" onClick={() => onIncrement(media.id)}><Plus size={15} /> Marcar siguiente</button>
            )}
          </>
        )}
      </div>
    </article>
  )
}

export default memo(MediaCard)
