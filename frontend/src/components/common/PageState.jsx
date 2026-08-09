import { AlertCircle, LoaderCircle } from 'lucide-react'

export default function PageState({ loading, error, empty, children }) {
  if (loading) return <div className="page-state"><LoaderCircle className="spin" /> Cargando...</div>
  if (error) return <div className="page-state error"><AlertCircle /> {error}<small>El backend gratuito puede tardar hasta dos minutos en activarse. Recarga la página para volver a intentarlo.</small></div>
  if (empty) return <div className="page-state">{children}</div>
  return null
}
