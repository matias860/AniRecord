import { useState } from 'react'
import { motion } from 'framer-motion'

export default function HoverMediaGrid({ children, className = '' }) {
  const [hovered, setHovered] = useState(null)
  return (
    <div className={`media-grid hover-media-grid ${className}`} onMouseLeave={() => setHovered(null)}>
      {children.map((child, index) => (
        <div className="hover-media-item" key={child.key ?? index} onMouseEnter={() => setHovered(index)}>
          {hovered === index && <motion.span className="card-hover-glow" layoutId="media-card-hover" transition={{ type: 'spring', stiffness: 320, damping: 28 }} />}
          <div className="hover-card-content">{child}</div>
        </div>
      ))}
    </div>
  )
}
