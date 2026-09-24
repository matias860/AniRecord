import { useEffect, useMemo, useState } from 'react'
import { Check, Image as ImageIcon, Plus, RotateCcw, Search, Send, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { publishTopNine } from '../../features/topNine/topNineSlice.js'
import './TopNine.css'

const EMPTY = Array(9).fill(null)
const COVER_FALLBACK = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="240" height="360"%3E%3Crect width="100%25" height="100%25" fill="%2310182b"/%3E%3Cpath d="M60 70h120v190H60zM120 70v190" fill="none" stroke="%2334d058" stroke-width="12"/%3E%3C/svg%3E'
const coverError = (event) => { if (event.currentTarget.src !== COVER_FALLBACK) event.currentTarget.src = COVER_FALLBACK }

function restoreSlots(library, mine, draftKey) {
  const byId = new Map(library.map((entry) => [entry.id, entry]))
  let ids = []
  try { ids = JSON.parse(window.localStorage.getItem(draftKey) || '[]') } catch { ids = [] }
  if (!ids.length && mine) ids = mine.selections.map((entry) => entry.libraryEntryId)
  return EMPTY.map((_, index) => byId.get(ids[index]) || null)
}

function matchesPrompt(entry, prompt) {
  if (prompt.mediaType && entry.mediaType !== prompt.mediaType) return false
  if (prompt.format && entry.mediaFormat?.toUpperCase() !== prompt.format.toUpperCase()) return false
  if (!prompt.genre) return true
  return entry.genres?.split('|').some((genre) => genre.trim().toLowerCase() === prompt.genre.toLowerCase())
}

function Picker({ entries, slot, pending, onPending, onConfirm }) {
  const [search, setSearch] = useState('')
  const query = search.trim().toLocaleLowerCase('es')
  const visible = entries.filter((entry) => !query || entry.title.toLocaleLowerCase('es').includes(query)
    || entry.romajiTitle?.toLocaleLowerCase('es').includes(query)).slice(0, 80)
  const column = slot % 3
  const row = Math.floor(slot / 3)

  return <motion.div className={`top-nine-picker${column === 2 ? ' opens-left' : ''}${row === 2 ? ' opens-up' : ''}`} initial={{ opacity: 0, scale: .94, x: column === 2 ? 12 : -12 }} animate={{ opacity: 1, scale: 1, x: 0 }} exit={{ opacity: 0, scale: .95 }} transition={{ duration: .18, ease: 'easeOut' }}>
    <label className="top-nine-search"><Search /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar en tu biblioteca…" autoFocus /></label>
    <div className="top-nine-picker-grid">
      {visible.map((entry) => <div className={`top-nine-candidate${pending?.id === entry.id ? ' selected' : ''}`} key={entry.id}>
        <button type="button" className="top-nine-candidate-cover" title={entry.title} aria-label={`Seleccionar ${entry.title}`} onClick={() => onPending(entry)}>
          <img src={entry.coverImage || COVER_FALLBACK} onError={coverError} alt="" />
        </button>
        {pending?.id === entry.id && <button type="button" className="top-nine-confirm-cover" onClick={() => onConfirm(entry)} aria-label={`Confirmar ${entry.title}`}><Check /></button>}
      </div>)}
      {!visible.length && <div className="top-nine-picker-empty"><ImageIcon /><span>No hay obras que coincidan.</span></div>}
    </div>
  </motion.div>
}

export default function TopNineComposer({ prompt, mine, onClose }) {
  const dispatch = useDispatch()
  const library = useSelector((state) => state.library.items)
  const userId = useSelector((state) => state.auth.userId)
  const publishing = useSelector((state) => state.topNine.publishStatus === 'loading')
  const draftKey = `anirecord:top-nine:${userId}:${prompt.key}`
  const [slots, setSlots] = useState(() => restoreSlots(library, mine, draftKey))
  const [activeSlot, setActiveSlot] = useState(null)
  const [pending, setPending] = useState(null)

  useEffect(() => {
    const ids = slots.map((entry) => entry?.id ?? null)
    if (ids.some(Boolean)) window.localStorage.setItem(draftKey, JSON.stringify(ids))
    else window.localStorage.removeItem(draftKey)
  }, [draftKey, slots])

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key !== 'Escape' || publishing) return
      if (activeSlot !== null) { setActiveSlot(null); setPending(null) } else onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [activeSlot, onClose, publishing])

  const eligible = useMemo(() => library.filter((entry) => matchesPrompt(entry, prompt)), [library, prompt])
  const completed = slots.filter(Boolean).length
  const availableFor = (slot) => {
    const used = new Set(slots.map((entry, index) => index === slot ? null : entry?.id).filter(Boolean))
    return eligible.filter((entry) => !used.has(entry.id))
  }
  const openSlot = (index) => {
    setActiveSlot(index)
    setPending(slots[index])
  }
  const confirm = (entry) => {
    setSlots((current) => current.map((value, index) => index === activeSlot ? entry : value))
    setActiveSlot(null)
    setPending(null)
  }
  const reset = () => {
    setSlots(EMPTY)
    setActiveSlot(null)
    setPending(null)
  }
  const publish = async () => {
    if (completed !== 9) return
    try {
      await dispatch(publishTopNine(slots.map((entry) => entry.id))).unwrap()
      window.localStorage.removeItem(draftKey)
      onClose()
    } catch { /* El toast global presenta el error. */ }
  }

  return <motion.section className="top-nine-composer" aria-labelledby="top-nine-composer-title" initial={{ opacity: 0, y: -18, height: 0 }} animate={{ opacity: 1, y: 0, height: 'auto' }} exit={{ opacity: 0, y: -12, height: 0 }} transition={{ duration: .3, ease: 'easeOut' }}>
    <button className="top-nine-close" type="button" onClick={onClose} disabled={publishing} aria-label="Cerrar el 3×3"><X /></button>
    <header>
      <span className="eyebrow">Consigna quincenal</span>
      <h2 id="top-nine-composer-title">{prompt.title}</h2>
      <p>{prompt.description}</p>
      {(prompt.genre || prompt.format || prompt.mediaType) && <small>{prompt.genre ? `Solo obras de ${prompt.genre}` : prompt.format ? `Solo formato ${prompt.format}` : prompt.mediaType === 'ANIME' ? 'Solo anime' : 'Solo manga'} · {eligible.length} disponibles</small>}
    </header>
    <div className="top-nine-builder">
      {slots.map((entry, index) => <div className={`top-nine-slot-shell${activeSlot === index ? ' active' : ''}`} key={index}>
        <button type="button" className={`top-nine-slot${entry ? ' filled' : ''}`} onClick={() => openSlot(index)} aria-label={entry ? `Cambiar ${entry.title}` : `Elegir obra para el espacio ${index + 1}`}>
          {entry ? <img src={entry.coverImage || COVER_FALLBACK} onError={coverError} alt={entry.title} /> : <><Plus /><span>{index + 1}</span></>}
        </button>
        {entry && <button type="button" className="top-nine-remove" onClick={() => setSlots((current) => current.map((value, position) => position === index ? null : value))} aria-label={`Quitar ${entry.title}`}><X /></button>}
        <AnimatePresence>{activeSlot === index && <Picker entries={availableFor(index)} slot={index} pending={pending} onPending={setPending} onConfirm={confirm} />}</AnimatePresence>
      </div>)}
    </div>
    {eligible.length < 9 && <p className="top-nine-warning">Esta consigna necesita nueve obras compatibles y actualmente tenés {eligible.length}.</p>}
    <footer>
      <button type="button" className="secondary-button" onClick={reset} disabled={!completed || publishing}><RotateCcw /> Resetear</button>
      <span>{completed} / 9</span>
      <button type="button" className="primary-button" onClick={publish} disabled={completed !== 9 || publishing}><Send /> {publishing ? 'Publicando…' : mine ? 'Actualizar publicación' : 'Publicar'}</button>
    </footer>
  </motion.section>
}
