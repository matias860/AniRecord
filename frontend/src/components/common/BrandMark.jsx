export default function BrandMark({ className = '' }) {
  return <span className={`brand-logo${className ? ` ${className}` : ''}`} aria-hidden="true">AR</span>
}
