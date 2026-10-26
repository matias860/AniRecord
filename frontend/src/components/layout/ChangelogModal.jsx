import useModalFocus from '../../lib/useModalFocus.js'
import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown, ChevronRight, History, ScrollText, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { CURRENT_VERSION, RELEASES } from '../../data/releaseNotes.js'
import BrandMark from '../common/BrandMark.jsx'
import './ChangelogModal.css'
import { backdropMotion, fadeUp, modalMotion } from '../../lib/motionVariants.js'

const STORAGE_KEY = 'anirecord:last-seen-version'
const currentFamily = familyOf(CURRENT_VERSION)
const versionGroups = RELEASES.reduce((groups, release) => {
  const family = familyOf(release.version)
  const existing = groups.find((group) => group.version === family)
  if (existing) existing.releases.push(release)
  else groups.push({ version: family, releases: [release] })
  return groups
}, []).map((group) => ({
  ...group,
  base: group.releases.find((release) => release.version.endsWith('.0')) || group.releases.at(-1),
  patches: group.releases.filter((release) => !release.version.endsWith('.0')),
}))

function familyOf(version) {
  return version.split('.').slice(0, 2).join('.')
}
export function VersionButton({ onClick, variant = 'label' }) {
  const [unseen, setUnseen] = useState(() => window.localStorage.getItem(STORAGE_KEY) !== CURRENT_VERSION)

  const open = () => {
    window.localStorage.setItem(STORAGE_KEY, CURRENT_VERSION)
    setUnseen(false)
    onClick()
  }

  return (
    <button className={`version-button${variant === 'mark' ? ' version-mark-button' : ''}`} type="button" onClick={open} aria-label={`Ver cambios de la versión ${CURRENT_VERSION}`}>
      {unseen && <i aria-hidden="true" />}
      {variant === 'mark' ? <span className="brand-mark"><BrandMark /></span> : <><span>Versión</span> {CURRENT_VERSION}</>}
    </button>
  )
}

export default function ChangelogModal({ open, onClose }) {
  const dialogRef = useModalFocus(open)
  const [selectedFamily, setSelectedFamily] = useState(currentFamily)
  const [expandedPatch, setExpandedPatch] = useState(CURRENT_VERSION)
  const closeButtonRef = useRef(null)
  const selectedGroup = versionGroups.find((group) => group.version === selectedFamily) || versionGroups[0]
  const selectedRelease = selectedGroup.base

  const selectGroup = (group) => {
    setSelectedFamily(group.version)
    setExpandedPatch(group.patches[0]?.version || null)
  }

  useEffect(() => {
    if (!open) return undefined
    const handleKeyDown = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', handleKeyDown)
    window.requestAnimationFrame(() => closeButtonRef.current?.focus())
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose, open])

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div {...backdropMotion} className="changelog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
          <motion.section {...modalMotion} ref={dialogRef} className="changelog-modal" role="dialog" aria-modal="true" aria-labelledby="changelog-title">
            <header className="changelog-header">
              <span className="changelog-icon"><ScrollText aria-hidden="true" /></span>
              <div>
                <small>Novedades de AniRecord</small>
                <h2 id="changelog-title">Historial de versiones</h2>
              </div>
              <button ref={closeButtonRef} className="changelog-close" type="button" onClick={onClose} aria-label="Cerrar historial"><X /></button>
            </header>

            <div className="changelog-layout">
              <nav className="release-list" aria-label="Versiones anteriores">
                {versionGroups.map((group) => {
                  const active = group.version === selectedGroup.version
                  return (
                    <button key={group.version} type="button" className={active ? 'active' : ''} onClick={() => selectGroup(group)} aria-current={active ? 'true' : undefined}>
                      <span>{group.base.date}</span>
                      <strong>v{group.version}{group.version === currentFamily && <i>Actual</i>}</strong>
                      <ChevronRight aria-hidden="true" />
                    </button>
                  )
                })}
              </nav>

              <article className="release-detail" key={selectedGroup.version}>
                <motion.div {...fadeUp}>
                  <div className="release-meta"><span>Versión {selectedGroup.version}</span><time>{selectedRelease.date}</time></div>
                  <h3>{selectedRelease.title}</h3>
                  <p>{selectedRelease.summary}</p>
                  <ChangeList changes={selectedRelease.changes} />
                  {selectedGroup.patches.length > 0 && (
                    <section className="minor-updates" aria-labelledby={`minor-updates-${selectedGroup.version}`}>
                      <div className="minor-updates-heading"><div><small>Dentro de esta versión</small><h4 id={`minor-updates-${selectedGroup.version}`}>Mejoras posteriores</h4></div><span>{selectedGroup.patches.length}</span></div>
                      {selectedGroup.patches.map((patch) => {
                        const expanded = expandedPatch === patch.version
                        return (
                          <article className={`minor-update${expanded ? ' expanded' : ''}`} key={patch.version}>
                            <button type="button" onClick={() => setExpandedPatch(expanded ? null : patch.version)} aria-expanded={expanded}>
                              <span><small>v{patch.version} · {patch.date}</small><strong>{patch.title}</strong></span>
                              <ChevronDown aria-hidden="true" />
                            </button>
                            <AnimatePresence initial={false}>
                              {expanded && <motion.div className="minor-update-content" initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: .2 }}><p>{patch.summary}</p><ChangeList changes={patch.changes} compact /></motion.div>}
                            </AnimatePresence>
                          </article>
                        )
                      })}
                    </section>
                  )}
                  {selectedGroup.version === currentFamily && <div className="release-current"><History aria-hidden="true" /> Estás usando la actualización {CURRENT_VERSION} de esta versión.</div>}
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

function ChangeList({ changes, compact = false }) {
  return <div className={`release-changes${compact ? ' compact' : ''}`}>{changes.map((change) => (
    <div key={`${change.type}-${change.text}`}>
      <span className={`change-type type-${change.type.toLowerCase().replace('ó', 'o')}`}>{change.type}</span>
      <p><Check aria-hidden="true" /> {change.text}</p>
    </div>
  ))}</div>
}
