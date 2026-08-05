import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'

export default function HoverMediaGrid({ children, className = '' }) {
  const [hovered, setHovered] = useState(null)
  return (
    <motion.div className={`media-grid hover-media-grid ${className}`} layout onPointerLeave={() => setHovered(null)}>
      <AnimatePresence mode="popLayout">
      {children.map((child, index) => (
        <motion.div className="hover-media-item" key={child.key ?? index} layout initial={{ opacity: 0, y: 14, scale: .98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -8, scale: .97 }} transition={{ duration: .24, ease: 'easeOut', layout: { type: 'spring', stiffness: 380, damping: 34 } }} onPointerEnter={() => setHovered(index)} onPointerLeave={() => setHovered(null)}>
          <AnimatePresence initial={false}>
            {hovered === index && <motion.span className="card-hover-glow" layoutId="media-card-hover" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ layout: { type: 'spring', stiffness: 320, damping: 28 }, opacity: { duration: .18 } }} />}
          </AnimatePresence>
          <div className="hover-card-content">{child}</div>
        </motion.div>
      ))}
      </AnimatePresence>
    </motion.div>
  )
}
