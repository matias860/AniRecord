export default function BrandMark({ className = '' }) {
  return <img className={`brand-logo${className ? ` ${className}` : ''}`} src="/anirecord-logo.svg" alt="" aria-hidden="true" />
}
