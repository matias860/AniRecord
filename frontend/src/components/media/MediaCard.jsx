import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getProgressPercent, STATUS_LABELS } from '../../lib/media.js'

export default function MediaCard({ media, onIncrement }) {
  const progress = media.progress ?? 0
  const totalLabel = media.totalUnits ?? (media.mediaType === 'MANGA' ? (progress > 0 ? `${progress}+` : 'En publicación') : '?')
  return (
    <article className="media-card">
      <Link to={`/media/${media.anilistId}`} className={`cover-wrap ${media.episodePending ? 'episode-pending' : ''}`}>
        <img src={media.coverImage} alt={`Portada de ${media.title}`} loading="lazy" />
        {media.trackingStatus && <span className="status-badge">{STATUS_LABELS[media.trackingStatus]}</span>}
      </Link>
      <div className="media-card-body">
        <Link to={`/media/${media.anilistId}`} className="media-title">{media.title}</Link>
        {media.trackingStatus && (
          <>
            <div className="progress-copy"><span>{media.mediaType === 'MANGA' ? 'Capítulos' : 'Episodios'}</span><b>{progress} / {totalLabel}</b></div>
            <div className="progress-track"><span style={{ width: `${getProgressPercent(progress, media.totalUnits)}%` }} /></div>
            {onIncrement && media.trackingStatus !== 'COMPLETED' && (
              <button className="quick-add" onClick={() => onIncrement(media.id)}><Plus size={15} /> Marcar siguiente</button>
            )}
          </>
        )}
      </div>
    </article>
  )
}
