import { AlertCircle, House, WifiOff } from 'lucide-react'
import { Link } from 'react-router-dom'
import ProfileLoader from './ProfileLoader.jsx'

export default function PageState({ loading, error, empty, fullPage = false, onRetry, children }) {
  const className = `page-state${fullPage ? ' full-page' : ''}`
  if (loading) return <div className={className}><ProfileLoader /></div>
  if (error?.includes('AniList respondió con el estado HTTP 403')) return <div role="alert" className={`${className} error anilist-page-error`}><WifiOff /><strong>AniList no está disponible temporalmente</strong><span>Esta sección necesita su API pública para obtener información actualizada.</span><Link className="primary-button" to="/"><House /> Volver al inicio</Link></div>
  if (error) return <div role="alert" className={`${className} error`}><AlertCircle /> {error}<small>Puede ser un problema temporal. Volvé a intentarlo.</small>{onRetry && <button type="button" className="secondary-button" onClick={onRetry}>Reintentar</button>}</div>
  if (empty) return <div className={className}>{children}</div>
  return null
}
