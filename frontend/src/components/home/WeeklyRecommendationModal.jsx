import { useEffect, useMemo, useState } from 'react'
import { BookOpen, Check, Clapperboard, Search, Sparkles, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { createWeeklyRecommendation } from '../../features/recommendations/recommendationsSlice.js'

export default function WeeklyRecommendationModal({ onClose }) {
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
    <motion.section className="recommendation-modal" role="dialog" aria-modal="true" aria-labelledby="recommendation-modal-title" initial={{ y: 22, scale: .97 }} animate={{ y: 0, scale: 1 }} exit={{ y: 14, scale: .98 }}>
      <button className="modal-close" type="button" onClick={onClose} disabled={loading} aria-label="Cerrar"><X /></button>
      <AnimatePresence mode="wait" initial={false}>
        {success ? <motion.div className="recommendation-success" key="success" initial={{ opacity: 0, scale: .94 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }}><span><Check /></span><h2>Serie recomendada</h2><p><strong>{selected.title}</strong> ya aparece entre las recomendaciones de esta semana.</p></motion.div>
          : selected ? <motion.div className="recommendation-confirm" key="confirm" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}><span className="eyebrow">Confirmar recomendación</span><div className="recommendation-confirm-media"><img src={selected.coverImage} alt="" /><div><small>{selected.mediaType === 'ANIME' ? 'Anime' : 'Manga'}</small><h2 id="recommendation-modal-title">¿Quieres recomendar {selected.title}?</h2><p>Tu perfil aparecerá junto a esta serie hasta el próximo lunes.</p></div></div><div className="modal-actions"><button className="secondary-button" type="button" disabled={loading} onClick={() => setSelected(null)}>No, volver</button><button className="primary-button" type="button" disabled={loading} onClick={confirm}><Sparkles /> {loading ? 'Recomendando…' : 'Sí, recomendar'}</button></div></motion.div>
            : <motion.div key="select" initial={{ opacity: 0, x: -18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 18 }}><span className="eyebrow">Recomendación semanal</span><h2 id="recommendation-modal-title">Selecciona una serie de tu biblioteca</h2><div className="recommendation-picker-tools"><div className="type-toggle" role="group" aria-label="Tipo de serie"><button type="button" className={mediaType === 'ANIME' ? 'active' : ''} onClick={() => setMediaType('ANIME')}><Clapperboard /> Anime</button><button type="button" className={mediaType === 'MANGA' ? 'active' : ''} onClick={() => setMediaType('MANGA')}><BookOpen /> Manga</button></div><label><Search /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar en tu biblioteca…" /></label></div><div className="recommendation-library-grid">{entries.map((entry) => <button type="button" key={entry.id} onClick={() => setSelected(entry)}><img src={entry.coverImage} alt="" /><span><strong>{entry.title}</strong><small>{entry.trackingStatus === 'COMPLETED' ? 'Completado' : entry.trackingStatus === 'IN_PROGRESS' ? 'En curso' : 'En tu biblioteca'}</small></span></button>)}{!entries.length && <p>No hay series que coincidan con esta búsqueda.</p>}</div></motion.div>}
      </AnimatePresence>
    </motion.section>
  </motion.div>
}
