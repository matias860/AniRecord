import useModalFocus from '../../lib/useModalFocus.js'
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
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
  if (prompt.requiresStarted && entry.trackingStatus === 'PLANNED') return false
  if (prompt.mediaType && entry.mediaType !== prompt.mediaType) return false
  if (prompt.format && entry.mediaFormat?.toUpperCase() !== prompt.format.toUpperCase()) return false
  if (!prompt.genre) return true
  return entry.genres?.split('|').some((genre) => genre.trim().toLowerCase() === prompt.genre.toLowerCase())
}

function Picker({ anchor, entries, pending, onPending, onConfirm, pickerRef }) {
  const [search, setSearch] = useState('')
  const [position, setPosition] = useState(null)
  const query = search.trim().toLocaleLowerCase('es')
  const visible = entries.filter((entry) => !query || entry.title.toLocaleLowerCase('es').includes(query)
    || entry.romajiTitle?.toLocaleLowerCase('es').includes(query)).slice(0, 80)

  useLayoutEffect(() => {
    const place = () => {
      if (!anchor) return
      const rect = anchor.getBoundingClientRect()
      const width = Math.min(560, window.innerWidth - 24)
      const height = Math.min(640, window.innerHeight - 24)
      const gap = 16
      const margin = 12
      const opensLeft = rect.right + gap + width > window.innerWidth - margin
      const left = opensLeft ? Math.max(margin, rect.left - gap - width) : Math.min(window.innerWidth - width - margin, rect.right + gap)
      const top = Math.max(margin, Math.min(window.innerHeight - height - margin, rect.top + rect.height / 2 - height / 2))
      const connectorY = Math.max(20, Math.min(height - 20, rect.top + rect.height / 2 - top))
      setPosition({ height, left, opensLeft, top, width, connectorY })
    }
    place()
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => {
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
    }
  }, [anchor])

  if (!position) return null
  return createPortal(<motion.div ref={pickerRef} style={{ height: position.height, left: position.left, top: position.top, width: position.width, '--connector-y': `${position.connectorY}px` }} className={`top-nine-picker${position.opensLeft ? ' opens-left' : ''}`} onPointerDown={(event) => {
    if (!event.target.closest('.top-nine-candidate')) onPending(null)
  }} initial={{ opacity: 0, scale: .94, x: position.opensLeft ? 12 : -12 }} animate={{ opacity: 1, scale: 1, x: 0 }} exit={{ opacity: 0, scale: .95 }} transition={{ duration: .18, ease: 'easeOut' }}>
    <label className="top-nine-search"><Search /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar en tu biblioteca…" autoFocus /></label>
    <div className="top-nine-picker-grid">
      {visible.map((entry) => <div className={`top-nine-candidate${pending?.id === entry.id ? ' selected' : ''}`} key={entry.id}>
        <button type="button" className="top-nine-candidate-cover" title={entry.title} aria-label={`Seleccionar ${entry.title}`} onClick={() => onPending(pending?.id === entry.id ? null : entry)}>
          <img src={entry.coverImage || COVER_FALLBACK} onError={coverError} alt="" loading="lazy" decoding="async" />
        </button>
        {pending?.id === entry.id && <button type="button" className="top-nine-confirm-cover" onClick={() => onConfirm(entry)} aria-label={`Confirmar ${entry.title}`}><Check /></button>}
      </div>)}
      {!visible.length && <div className="top-nine-picker-empty"><ImageIcon /><span>No hay obras que coincidan.</span></div>}
    </div>
  </motion.div>, document.body)
}

export default function TopNineComposer({ prompt, mine, onClose }) {
  const dialogRef = useModalFocus(true)
  const dispatch = useDispatch()
  const library = useSelector((state) => state.library.items)
  const userId = useSelector((state) => state.auth.userId)
  const publishing = useSelector((state) => state.topNine.publishStatus === 'loading')
  const draftKey = `anirecord:top-nine:${userId}:${prompt.key}`
  const [slots, setSlots] = useState(() => restoreSlots(library, mine, draftKey))
  const [activeSlot, setActiveSlot] = useState(null)
  const [activeAnchor, setActiveAnchor] = useState(null)
  const [pending, setPending] = useState(null)
  const pickerRef = useRef(null)

  useEffect(() => {
    const ids = slots.map((entry) => entry?.id ?? null)
    if (ids.some(Boolean)) window.localStorage.setItem(draftKey, JSON.stringify(ids))
    else window.localStorage.removeItem(draftKey)
  }, [draftKey, slots])

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key !== 'Escape' || publishing) return
      if (activeSlot !== null) { setActiveSlot(null); setActiveAnchor(null); setPending(null) } else onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [activeSlot, onClose, publishing])

  useEffect(() => {
    if (activeSlot === null) return undefined
    const onPointerDown = (event) => {
      if (pickerRef.current?.contains(event.target) || event.target.closest('.top-nine-slot-shell')) return
      setActiveSlot(null)
      setActiveAnchor(null)
      setPending(null)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [activeSlot])

  const eligible = useMemo(() => library.filter((entry) => matchesPrompt(entry, prompt)), [library, prompt])
  const completed = slots.filter(Boolean).length
  const availableFor = (slot) => {
    const used = new Set(slots.map((entry, index) => index === slot ? null : entry?.id).filter(Boolean))
    return eligible.filter((entry) => !used.has(entry.id))
  }
  const openSlot = (index, anchor) => {
    if (activeSlot === index) {
      setActiveSlot(null)
      setActiveAnchor(null)
      setPending(null)
      return
    }
    setActiveSlot(index)
    setActiveAnchor(anchor)
    setPending(slots[index])
  }
  const confirm = (entry) => {
    setSlots((current) => current.map((value, index) => index === activeSlot ? entry : value))
    setActiveSlot(null)
    setActiveAnchor(null)
    setPending(null)
  }
  const reset = () => {
    setSlots(EMPTY)
    setActiveSlot(null)
    setActiveAnchor(null)
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

  return createPortal(<motion.div className="top-nine-modal-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onPointerDown={(event) => {
    if (event.target === event.currentTarget && !publishing && activeSlot === null) onClose()
  }}>
  <motion.section ref={dialogRef} className="top-nine-composer" role="dialog" aria-modal="true" aria-labelledby="top-nine-composer-title" initial={{ opacity: 0, y: 18, scale: .97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 12, scale: .98 }} transition={{ duration: .25, ease: 'easeOut' }}>
    <button className="top-nine-close" type="button" onClick={onClose} disabled={publishing} aria-label="Cerrar el 3×3"><X /></button>
    <header>
      <span className="eyebrow">Consigna quincenal</span>
      <h2 id="top-nine-composer-title">{prompt.title}</h2>
      <p>{prompt.description}</p>
      {(prompt.genre || prompt.format || prompt.mediaType) && <small>{prompt.genre ? `Solo obras de ${prompt.genre}` : prompt.format ? `Solo formato ${prompt.format}` : prompt.mediaType === 'ANIME' ? 'Solo anime' : 'Solo manga'} · {eligible.length} disponibles</small>}
    </header>
    <div className="top-nine-builder">
      {slots.map((entry, index) => <div className={`top-nine-slot-shell${activeSlot === index ? ' active' : ''}`} key={index}>
        <button type="button" className={`top-nine-slot${entry ? ' filled' : ''}`} onClick={(event) => openSlot(index, event.currentTarget)} aria-label={entry ? `Cambiar ${entry.title}` : `Elegir obra para el espacio ${index + 1}`}>
          <AnimatePresence mode="wait" initial={false}>
            {entry
              ? <motion.img key={entry.id} src={entry.coverImage || COVER_FALLBACK} onError={coverError} alt={entry.title} initial={{ opacity: 0, scale: .88 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: .76, y: 18, rotate: -3 }} transition={{ duration: .24, ease: 'easeInOut' }} />
              : <motion.span className="top-nine-empty" key="empty" initial={{ opacity: 0, scale: .8 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: .8 }} transition={{ duration: .2 }}><Plus /><small>{index + 1}</small></motion.span>}
          </AnimatePresence>
        </button>
        {entry && <button type="button" className="top-nine-remove" onClick={() => setSlots((current) => current.map((value, position) => position === index ? null : value))} aria-label={`Quitar ${entry.title}`}><X /></button>}
        <AnimatePresence>{activeSlot === index && <Picker anchor={activeAnchor} entries={availableFor(index)} pending={pending} onPending={setPending} onConfirm={confirm} pickerRef={pickerRef} />}</AnimatePresence>
      </div>)}
    </div>
    {eligible.length < 9 && <p className="top-nine-warning">Esta consigna necesita nueve obras compatibles y actualmente tenés {eligible.length}.</p>}
    <footer>
      <button type="button" className="top-nine-reset" onClick={reset} disabled={!completed || publishing}><RotateCcw /> Resetear</button>
      <span>{completed} / 9</span>
      <button type="button" className="primary-button" onClick={publish} disabled={completed !== 9 || publishing}><Send /> {publishing ? 'Publicando…' : mine ? 'Actualizar publicación' : 'Publicar'}</button>
    </footer>
  </motion.section>
  </motion.div>, document.body)
}
