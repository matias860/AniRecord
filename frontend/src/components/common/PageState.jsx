import { AlertCircle, House, WifiOff } from 'lucide-react'
import { Link } from 'react-router-dom'
import ProfileLoader from './ProfileLoader.jsx'

export default function PageState({ loading, error, empty, children }) {
  if (loading) return <div className="page-state"><ProfileLoader /></div>
  if (error?.includes('AniList respondió con el estado HTTP 403')) return <div className="page-state error anilist-page-error"><WifiOff /><strong>AniList no está disponible temporalmente</strong><span>Esta sección necesita su API pública para obtener información actualizada.</span><Link className="primary-button" to="/"><House /> Volver al inicio</Link></div>
  if (error) return <div className="page-state error"><AlertCircle /> {error}<small>El backend gratuito puede tardar hasta dos minutos en activarse. Recarga la página para volver a intentarlo.</small></div>
  if (empty) return <div className="page-state">{children}</div>
  return null
}
