import { AlertCircle, LoaderCircle } from 'lucide-react'

export default function PageState({ loading, error, empty, children }) {
  if (loading) return <div className="page-state"><LoaderCircle className="spin" /> Cargando...</div>
  if (error) return <div className="page-state error"><AlertCircle /> {error}<small>Comprobá que el backend esté corriendo en el puerto 8080.</small></div>
  if (empty) return <div className="page-state">{children}</div>
  return null
}
