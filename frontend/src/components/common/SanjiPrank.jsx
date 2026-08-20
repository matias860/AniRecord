import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'

export default function SanjiPrank() {
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (!open) return undefined
    const closeOnEscape = (event) => { if (event.key === 'Escape') setOpen(false) }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [open])

  return (
    <>
      <AnimatePresence>
        {!open && (
          <motion.button
            className="sanji-prank-trigger"
            type="button"
            onClick={() => setOpen(true)}
            initial={{ opacity: 0, scale: .82, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: .88 }}
            transition={{ type: 'spring', stiffness: 310, damping: 22 }}
          >
            Para Mantecas
          </motion.button>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {open && (
          <motion.div
            className="sanji-prank-backdrop"
            role="presentation"
            onMouseDown={(event) => { if (event.target === event.currentTarget) setOpen(false) }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.section
              className="sanji-prank-banner"
              role="dialog"
              aria-modal="true"
              aria-labelledby="sanji-prank-title"
              initial={{ opacity: 0, scale: .8, rotateX: 12 }}
              animate={{ opacity: 1, scale: 1, rotateX: 0 }}
              exit={{ opacity: 0, scale: .88, y: 22 }}
              transition={{ type: 'spring', stiffness: 230, damping: 21 }}
            >
              <span className="sanji-blue-fire" aria-hidden="true" />
              <img src="/pranks/sanji-mantecas.jpg" alt="Vinsmoke Sanji" />
              <div className="sanji-prank-shade" />
              <div className="sanji-prank-copy">
                <span>Chupala mantetas</span>
                <h2 id="sanji-prank-title">Sanji besto mugi</h2>
              </div>
              <button className="sanji-prank-close" type="button" onClick={() => setOpen(false)} aria-label="Cerrar"><X /></button>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}
