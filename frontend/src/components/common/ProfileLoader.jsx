import { BookOpen } from 'lucide-react'
import { useSelector } from 'react-redux'

export default function ProfileLoader({ message = 'Cargando...', compact = false, className = '' }) {
  const avatarUrl = useSelector((state) => state.profile?.avatarUrl)

  return <div className={`profile-loader ${compact ? 'compact' : ''} ${className}`.trim()} role="status" aria-live="polite">
    <span className="profile-loader-orbit" aria-hidden="true">
      <span className="profile-loader-avatar">
        {avatarUrl ? <img src={avatarUrl} alt="" /> : <BookOpen />}
      </span>
    </span>
    {message && <span>{message}</span>}
  </div>
}
