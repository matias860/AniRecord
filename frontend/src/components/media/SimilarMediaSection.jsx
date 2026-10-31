import { useEffect, useMemo, useRef, useState } from 'react'
import { Sparkles } from 'lucide-react'
import { useSelector } from 'react-redux'
import { useGetSimilarMediaQuery } from '../../api/anirecordApi.js'
import HoverMediaGrid from './HoverMediaGrid.jsx'
import MediaCard from './MediaCard.jsx'

export default function SimilarMediaSection({ anilistId }) {
  const sectionRef = useRef(null)
  const [ready, setReady] = useState(false)
  const adultContentMode = useSelector((state) => state.profile.adultContentMode)
  const libraryItems = useSelector((state) => state.library.items)
  const completedIds = useMemo(() => new Set(libraryItems
    .filter((item) => item.trackingStatus === 'COMPLETED')
    .map((item) => item.anilistId)), [libraryItems])
  const { data = [], isFetching } = useGetSimilarMediaQuery(
    { anilistId, includeAdult: adultContentMode !== 'HIDE' },
    { skip: !ready || !anilistId },
  )
  const suggestions = useMemo(() => data.filter((item) => !completedIds.has(item.media.anilistId)).slice(0, 8), [completedIds, data])

  useEffect(() => {
    if (ready) return undefined
    if (!('IntersectionObserver' in window)) {
      const timer = window.setTimeout(() => setReady(true), 0)
      return () => window.clearTimeout(timer)
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) { setReady(true); observer.disconnect() }
    }, { rootMargin: '500px 0px' })
    if (sectionRef.current) observer.observe(sectionRef.current)
    return () => observer.disconnect()
  }, [ready])

  return <section ref={sectionRef} className="similar-media-section" aria-labelledby="similar-media-title">
    <div className="detail-section-heading"><span><span className="eyebrow">Para seguir explorando</span><h2 id="similar-media-title"><Sparkles /> Obras similares</h2></span></div>
    {!ready || isFetching ? <div className="similar-media-loading" role="status">Buscando recomendaciones relacionadas…</div>
      : suggestions.length > 0 ? <HoverMediaGrid>{suggestions.map((item) => <div className="similar-media-item" key={item.media.anilistId}><MediaCard media={item.media} /><p>{item.reason}</p></div>)}</HoverMediaGrid>
        : <p className="similar-media-empty">No encontramos alternativas suficientemente distintas para recomendar acá.</p>}
  </section>
}
