import { Children } from 'react'
import { AnimatePresence, motion } from 'framer-motion'

export default function HoverMediaGrid({ children, className = '', animateItems = false }) {
  const items = Children.toArray(children)

  return (
    <div className={`media-grid hover-media-grid ${className}`}>
      <AnimatePresence initial={false} mode={animateItems ? 'popLayout' : 'sync'}>
        {items.map((child) => animateItems ? (
          <motion.div
            className="hover-media-item"
            key={child.key}
            layout="position"
            initial={{ opacity: 0, scale: .96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: .96, y: -6 }}
            transition={{ duration: .18, ease: 'easeOut', layout: { type: 'spring', stiffness: 430, damping: 38 } }}
          >
            <span className="card-hover-glow" aria-hidden="true" />
            <div className="hover-card-content">{child}</div>
          </motion.div>
        ) : (
          <div className="hover-media-item" key={child.key}>
          <span className="card-hover-glow" aria-hidden="true" />
          <div className="hover-card-content">{child}</div>
          </div>
        ))}
      </AnimatePresence>
    </div>
  )
}
