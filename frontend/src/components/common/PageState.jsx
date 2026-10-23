import { AlertCircle, House, SearchX, WifiOff } from 'lucide-react'
import { Link } from 'react-router-dom'
import ProfileLoader from './ProfileLoader.jsx'

export default function PageState({ loading, error, empty, fullPage = false, onRetry, children, title, description, actionLabel, actionTo, onAction }) {
  const className = `page-state${fullPage ? ' full-page' : ''}`
  if (loading) return fullPage
    ? <div className={className}><ProfileLoader /></div>
    : <div className="content-skeleton" role="status" aria-label="Cargando contenido"><i /><i /><i /><span className="sr-only">Cargando contenido</span></div>
  if (error?.includes('AniList respondió con el estado HTTP 403')) return <div role="alert" className={`${className} error anilist-page-error`}><WifiOff /><strong>AniList no está disponible temporalmente</strong><span>Esta sección necesita su API pública para obtener información actualizada.</span><Link className="primary-button" to="/"><House /> Volver al inicio</Link></div>
  if (error) return <div role="alert" className={`${className} error`}><AlertCircle /> {error}<small>Puede ser un problema temporal. Volvé a intentarlo.</small>{onRetry && <button type="button" className="secondary-button" onClick={onRetry}>Reintentar</button>}</div>
  if (empty) return <div className={`${className} empty-state`}><SearchX /><strong>{title || children}</strong>{description && <span>{description}</span>}{actionTo && <Link className="secondary-button" to={actionTo}>{actionLabel}</Link>}{onAction && <button className="secondary-button" type="button" onClick={onAction}>{actionLabel}</button>}</div>
  return null
}
