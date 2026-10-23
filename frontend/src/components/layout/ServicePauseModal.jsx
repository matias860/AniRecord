import { useEffect, useState } from 'react'
import { BellRing, CalendarClock, ExternalLink, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import anilistLogo from '../../assets/anilist-logo.svg'
import useModalFocus from '../../lib/useModalFocus.js'
import './ServicePauseModal.css'

const DISMISSED_KEY = 'anirecord:service-pause:2026-11-01'
const SERVICE_RETURNS_AT = new Date('2026-11-01T03:00:00Z').getTime()

function wasDismissed() {
  try {
    return window.sessionStorage.getItem(DISMISSED_KEY) === 'dismissed'
  } catch {
    return false
  }
}

export default function ServicePauseModal() {
  const [open, setOpen] = useState(() => Date.now() < SERVICE_RETURNS_AT && !wasDismissed())
  const dialogRef = useModalFocus(open)

  const close = () => {
    try {
      window.sessionStorage.setItem(DISMISSED_KEY, 'dismissed')
    } catch {
      // El aviso sigue pudiendo cerrarse si el navegador bloquea sessionStorage.
    }
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return undefined
    const closeOnEscape = (event) => { if (event.key === 'Escape') close() }
    const remaining = SERVICE_RETURNS_AT - Date.now()
    const expiry = remaining > 0 ? window.setTimeout(() => setOpen(false), remaining) : null
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      window.removeEventListener('keydown', closeOnEscape)
      if (expiry != null) window.clearTimeout(expiry)
    }
  }, [open])

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div className="service-pause-backdrop" role="presentation" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onMouseDown={(event) => { if (event.target === event.currentTarget) close() }}>
          <motion.section ref={dialogRef} className="service-pause-modal" role="dialog" aria-modal="true" aria-labelledby="service-pause-title" aria-describedby="service-pause-description" initial={{ opacity: 0, y: 18, scale: .97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 12, scale: .98 }} transition={{ duration: .22, ease: 'easeOut' }}>
            <button className="service-pause-close" type="button" onClick={close} aria-label="Cerrar aviso"><X /></button>
            <span className="service-pause-icon"><CalendarClock aria-hidden="true" /></span>
            <small className="service-pause-eyebrow">Aviso temporal</small>
            <h2 id="service-pause-title">AniRecord vuelve el 1 de noviembre</h2>
            <p id="service-pause-description">La página no estará disponible durante estos días mientras esperamos que se renueve la capacidad del servicio.</p>

            <a className="service-pause-anilist" href="https://anilist.co" target="_blank" rel="noreferrer">
              <span><img src={anilistLogo} alt="" /></span>
              <span><small>Mientras tanto</small><strong>Continuar en AniList</strong></span>
              <ExternalLink aria-hidden="true" />
            </a>

            <aside className="service-pause-reminder">
              <BellRing aria-hidden="true" />
              <p><strong>Recordatorio</strong> Cuando AniRecord vuelva, sincronizá <b>AniList → AniRecord</b> para traer los cambios que hagas durante estos días.</p>
            </aside>

            <button className="service-pause-acknowledge" type="button" onClick={close}>Entendido</button>
          </motion.section>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  )
}
