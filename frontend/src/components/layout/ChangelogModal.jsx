import { useEffect, useRef, useState } from 'react'
import { Check, ChevronRight, History, Sparkles, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { CURRENT_VERSION, RELEASES } from '../../data/releaseNotes.js'
import './ChangelogModal.css'

const STORAGE_KEY = 'anirecord:last-seen-version'
const MONTHS = { agosto: 'ago', septiembre: 'sep', octubre: 'oct' }

function shortDate(date) {
  const [day, , month] = date.split(' ')
  return `${day} ${MONTHS[month] || month.slice(0, 3)}`
}

export function VersionButton({ onClick }) {
  const [unseen, setUnseen] = useState(() => window.localStorage.getItem(STORAGE_KEY) !== CURRENT_VERSION)

  const open = () => {
    window.localStorage.setItem(STORAGE_KEY, CURRENT_VERSION)
    setUnseen(false)
    onClick()
  }

  return (
    <button className="version-button" type="button" onClick={open} aria-label={`Ver cambios de la versión ${CURRENT_VERSION}`}>
      {unseen && <i aria-hidden="true" />}
      <span>Versión</span> {CURRENT_VERSION}
    </button>
  )
}

export default function ChangelogModal({ open, onClose }) {
  const [selectedVersion, setSelectedVersion] = useState(CURRENT_VERSION)
  const closeButtonRef = useRef(null)
  const selectedRelease = RELEASES.find((release) => release.version === selectedVersion) || RELEASES[0]

  useEffect(() => {
    if (!open) return undefined
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const handleKeyDown = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', handleKeyDown)
    window.requestAnimationFrame(() => closeButtonRef.current?.focus())
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose, open])

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div className="changelog-backdrop" role="presentation" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
          <motion.section className="changelog-modal" role="dialog" aria-modal="true" aria-labelledby="changelog-title" initial={{ opacity: 0, y: 18, scale: .98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 12, scale: .98 }} transition={{ duration: .22, ease: 'easeOut' }}>
            <header className="changelog-header">
              <span className="changelog-icon"><Sparkles aria-hidden="true" /></span>
              <div>
                <small>Novedades de AniRecord</small>
                <h2 id="changelog-title">Historial de versiones</h2>
              </div>
              <button ref={closeButtonRef} className="changelog-close" type="button" onClick={onClose} aria-label="Cerrar historial"><X /></button>
            </header>

            <div className="changelog-layout">
              <nav className="release-list" aria-label="Versiones anteriores">
                {RELEASES.map((release, index) => {
                  const active = release.version === selectedRelease.version
                  return (
                    <button key={release.version} type="button" className={active ? 'active' : ''} onClick={() => setSelectedVersion(release.version)} aria-current={active ? 'true' : undefined}>
                      <span>{index === 0 ? 'Actual' : shortDate(release.date)}</span>
                      <strong>v{release.version}</strong>
                      <ChevronRight aria-hidden="true" />
                    </button>
                  )
                })}
              </nav>

              <article className="release-detail" key={selectedRelease.version}>
                <motion.div initial={{ opacity: 0, x: 8 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: .18 }}>
                  <div className="release-meta"><span>Versión {selectedRelease.version}</span><time>{selectedRelease.date}</time></div>
                  <h3>{selectedRelease.title}</h3>
                  <p>{selectedRelease.summary}</p>
                  <div className="release-changes">
                    {selectedRelease.changes.map((change) => (
                      <div key={`${change.type}-${change.text}`}>
                        <span className={`change-type type-${change.type.toLowerCase().replace('ó', 'o')}`}>{change.type}</span>
                        <p><Check aria-hidden="true" /> {change.text}</p>
                      </div>
                    ))}
                  </div>
                  {selectedRelease.version === CURRENT_VERSION && <div className="release-current"><History aria-hidden="true" /> Esta es la versión que estás usando.</div>}
                </motion.div>
              </article>
            </div>
          </motion.section>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  )
}
