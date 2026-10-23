import { useEffect, useRef, useState } from 'react'
import { Clock3, Search, UserRound, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import apiClient from '../../api/apiClient.js'
import useModalFocus from '../../lib/useModalFocus.js'

const RECENT_KEY = 'anirecord:recent-searches'
const emptyResults = { media: [], characters: [], staff: [] }

function recentSearches() {
  try { return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]').filter(Boolean).slice(0, 5) } catch { return [] }
}

export default function GlobalSearch({ open, onClose }) {
  const inputRef = useRef(null)
  const dialogRef = useModalFocus(open)
  const requestRef = useRef(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState(emptyResults)
  const [status, setStatus] = useState('idle')
  const [recents, setRecents] = useState(recentSearches)
  useEffect(() => {
    if (!open) return undefined
    inputRef.current?.focus()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const close = (event) => { if (event.key === 'Escape') onClose() }
    document.addEventListener('keydown', close)
    return () => { document.removeEventListener('keydown', close); document.body.style.overflow = previousOverflow }
  }, [onClose, open])
  useEffect(() => {
    const normalized = query.trim()
    requestRef.current?.abort()
    if (normalized.length < 2) return undefined
    const controller = new AbortController()
    requestRef.current = controller
    const timer = window.setTimeout(async () => {
      setStatus('loading')
      try {
        const { data } = await apiClient.get('/anilist/global-search', { params: { query: normalized }, signal: controller.signal })
        setResults(data)
        setStatus('succeeded')
      } catch (error) {
        if (error.name !== 'CanceledError' && error.code !== 'ERR_CANCELED') setStatus('failed')
      }
    }, 220)
    return () => { window.clearTimeout(timer); controller.abort() }
  }, [query])
  const remember = () => {
    const value = query.trim()
    if (value.length < 2) return
    const next = [value, ...recents.filter((item) => item.toLowerCase() !== value.toLowerCase())].slice(0, 5)
    setRecents(next)
    localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  }
  const changeQuery = (event) => {
    const value = event.target.value
    setQuery(value)
    if (value.trim().length < 2) { setStatus('idle'); setResults(emptyResults) } else setStatus('loading')
  }
  const closeResult = () => { remember(); onClose() }
  const groups = [
    ['Títulos', results.media, (item) => `/media/${item.anilistId}`],
    ['Personajes', results.characters, (item) => `/personajes/${item.anilistId}`],
    ['Seiyūs y profesionales', results.staff, (item) => `/seiyuus/${item.anilistId}`],
  ]
  const effectiveStatus = query.trim().length < 2 ? 'idle' : status
  const total = groups.reduce((sum, [, items]) => sum + items.length, 0)
  return <AnimatePresence>{open && <motion.div className="global-search-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <motion.section ref={dialogRef} className="global-search-panel" role="dialog" aria-modal="true" aria-labelledby="global-search-title" initial={{ opacity: 0, y: -18, scale: .98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -12, scale: .985 }} transition={{ duration: .18 }}>
      <div className="global-search-heading"><div><span className="eyebrow">Búsqueda global</span><h2 id="global-search-title">Encontrá cualquier cosa</h2></div><button type="button" onClick={onClose} aria-label="Cerrar búsqueda"><X /></button></div>
      <label className="global-search-input"><Search /><input ref={inputRef} value={query} onChange={changeQuery} placeholder="Anime, manga, personaje o seiyū…" autoComplete="off" /><kbd>Esc</kbd></label>
      {query.trim().length < 2 && <div className="global-search-recents"><strong>Búsquedas recientes</strong>{recents.length ? recents.map((recent) => <button type="button" key={recent} onClick={() => setQuery(recent)}><Clock3 />{recent}</button>) : <p>Tus búsquedas recientes aparecerán acá.</p>}</div>}
      {effectiveStatus === 'loading' && <div className="global-search-loading" aria-label="Buscando"><i /><i /><i /></div>}
      {effectiveStatus === 'failed' && <div className="global-search-message">No pudimos buscar ahora. Revisá tu conexión e intentá de nuevo.</div>}
      {effectiveStatus === 'succeeded' && !total && <div className="global-search-message">No encontramos resultados para “{query.trim()}”.</div>}
      {effectiveStatus === 'succeeded' && total > 0 && <div className="global-search-results">{groups.map(([label, items, href]) => items.length > 0 && <section key={label}><h3>{label}</h3><div>{items.map((item) => <Link key={`${item.kind}-${item.anilistId}`} to={href(item)} onClick={closeResult}><span className="global-search-image">{item.image ? <img src={item.image} alt="" /> : <UserRound />}</span><span><strong>{item.title}</strong><small>{item.subtitle?.replaceAll('_', ' ') || item.kind}</small></span></Link>)}</div></section>)}</div>}
      <footer><span><kbd>⌘/Ctrl</kbd><kbd>K</kbd> para abrir</span><span><kbd>Esc</kbd> para cerrar</span></footer>
    </motion.section>
  </motion.div>}</AnimatePresence>
}
