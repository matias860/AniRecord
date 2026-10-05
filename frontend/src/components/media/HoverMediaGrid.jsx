import { Children } from 'react'

export default function HoverMediaGrid({ children, className = '' }) {
  return (
    <div className={`media-grid hover-media-grid ${className}`}>
      {Children.map(children, (child) => (
        <div className="hover-media-item">
          <span className="card-hover-glow" aria-hidden="true" />
          <div className="hover-card-content">{child}</div>
        </div>
      ))}
    </div>
  )
}
