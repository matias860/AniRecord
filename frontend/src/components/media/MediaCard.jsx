import { Plus, Star } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { getProgressPercent, STATUS_LABELS, isUnreleasedAnime } from '../../lib/media.js'
import MediaTags from './MediaTags.jsx'

export default function MediaCard({ media, onIncrement }) {
  const progress = media.progress ?? 0
  const isLiveAnime = media.mediaType === 'ANIME' && media.releaseStatus === 'RELEASING'
  const progressLabel = media.mediaType === 'ANIME' ? 'Episodios' : media.mediaFormat === 'NOVEL' ? 'Tomos' : 'Capítulos'
  const totalLabel = media.totalUnits ?? (media.mediaType === 'MANGA' ? (progress > 0 ? `${progress}+` : 'En publicación') : '?')
  return (
    <article className="media-card">
      <Link to={`/media/${media.anilistId}`} className={`cover-wrap ${media.episodePending ? 'episode-pending' : ''}`}>
        <img src={media.coverImage} alt={`Portada de ${media.title}`} width="230" height="345" loading="lazy" decoding="async" />
        {media.trackingStatus && <span className="status-badge">{STATUS_LABELS[media.trackingStatus]}</span>}
        {media.personalScore != null && <span className="score-badge"><Star /> {media.personalScore}</span>}
        {isLiveAnime && <span className="airing-badge"><i /> Emisión</span>}
      </Link>
      <div className="media-card-body">
        <Link to={`/media/${media.anilistId}`} className="media-title">{media.title}</Link>
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
