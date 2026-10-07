import useModalFocus from '../../lib/useModalFocus.js'
import { useEffect, useMemo, useState } from 'react'
import { Check, Search, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { createWeeklyRecommendation } from '../../features/recommendations/recommendationsSlice.js'
import MediaTypeSwitch from '../common/MediaTypeSwitch.jsx'

const COVER_FALLBACK = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="240" height="360"%3E%3Crect width="100%25" height="100%25" fill="%2310182b"/%3E%3Cpath d="M60 70h120v190H60zM120 70v190" fill="none" stroke="%23a78bfa" stroke-width="12"/%3E%3C/svg%3E'
const onCoverError = (event) => { if (event.currentTarget.src !== COVER_FALLBACK) event.currentTarget.src = COVER_FALLBACK }

export default function WeeklyRecommendationModal({ onClose }) {
  const dialogRef = useModalFocus(true)
  const dispatch = useDispatch()
  const library = useSelector((state) => state.library.items)
  const loading = useSelector((state) => state.recommendations.createStatus === 'loading')
  const [mediaType, setMediaType] = useState('ANIME')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(null)
  const [success, setSuccess] = useState(false)

  const entries = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('es')
    return library.filter((entry) => entry.mediaType === mediaType
      && ['IN_PROGRESS', 'COMPLETED'].includes(entry.trackingStatus)
      && (!query || entry.title.toLocaleLowerCase('es').includes(query)
        || entry.romajiTitle?.toLocaleLowerCase('es').includes(query)))
  }, [library, mediaType, search])

  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === 'Escape' && !loading) onClose() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [loading, onClose])

  useEffect(() => {
    if (!success) return undefined
    const timer = window.setTimeout(onClose, 1400)
    return () => window.clearTimeout(timer)
  }, [success, onClose])

  const confirm = async () => {
    try {
      await dispatch(createWeeklyRecommendation(selected.id)).unwrap()
      setSuccess(true)
    } catch { /* El middleware global presenta el error. */ }
  }

  return <motion.div className="recommendation-modal-backdrop" role="presentation" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onMouseDown={(event) => { if (event.target === event.currentTarget && !loading) onClose() }}>
    <motion.section ref={dialogRef} className="recommendation-modal" role="dialog" aria-modal="true" aria-labelledby="recommendation-modal-title" initial={{ y: 22, scale: .97 }} animate={{ y: 0, scale: 1 }} exit={{ y: 14, scale: .98 }}>
      <button className="modal-close" type="button" onClick={onClose} disabled={loading} aria-label="Cerrar"><X /></button>
      <AnimatePresence mode="wait" initial={false}>
        {success ? <motion.div className="recommendation-success" key="success" initial={{ opacity: 0, scale: .94 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }}><span><Check /></span><h2>Serie recomendada</h2><p><strong>{selected.title}</strong> ya aparece entre las recomendaciones de esta semana.</p></motion.div>
          : selected ? <motion.div className="recommendation-confirm" key="confirm" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}><img className="recommendation-confirm-cover" src={selected.coverImage || COVER_FALLBACK} onError={onCoverError} alt="" /><div className="recommendation-confirm-content"><span className="eyebrow">Confirmar recomendación</span><small>{selected.mediaType === 'ANIME' ? 'Anime' : 'Manga'}</small><h2 id="recommendation-modal-title">¿Quieres recomendar {selected.title}?</h2><p>Tu perfil aparecerá junto a esta serie hasta el próximo lunes.</p><div className="modal-actions"><button className="secondary-button" type="button" disabled={loading} onClick={() => setSelected(null)}>No, volver</button><button className="primary-button" type="button" disabled={loading} onClick={confirm}>{loading ? 'Recomendando…' : 'Sí, recomendar'}</button></div></div></motion.div>
            : <motion.div key="select" initial={{ opacity: 0, x: -18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 18 }}><span className="eyebrow">Recomendación semanal</span><h2 id="recommendation-modal-title">Selecciona una serie de tu biblioteca</h2><div className="recommendation-picker-tools"><MediaTypeSwitch value={mediaType} onChange={setMediaType} ariaLabel="Cambiar tipo de recomendación" /><label><Search /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar en tu biblioteca…" /></label></div><div className="recommendation-library-grid">{entries.map((entry) => <button type="button" key={entry.id} onClick={() => setSelected(entry)}><img src={entry.coverImage || COVER_FALLBACK} onError={onCoverError} alt="" loading="lazy" decoding="async" /><span><strong>{entry.title}</strong><small>{entry.trackingStatus === 'COMPLETED' ? 'Completado' : entry.trackingStatus === 'IN_PROGRESS' ? 'En curso' : 'En tu biblioteca'}</small></span></button>)}{!entries.length && <p>No hay series que coincidan con esta búsqueda.</p>}</div></motion.div>}
      </AnimatePresence>
    </motion.section>
  </motion.div>
}
