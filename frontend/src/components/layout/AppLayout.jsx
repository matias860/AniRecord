import { useEffect, useState } from 'react'
import { BookOpen, Compass, Home, Library, UserRound } from 'lucide-react'
import { motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { NavLink, Outlet } from 'react-router-dom'
import { fetchProfile } from '../../features/profile/profileSlice.js'
import NotificationBell from './NotificationBell.jsx'
import ToastViewport from '../common/ToastViewport.jsx'

const links = [
  { to: '/', label: 'Inicio', icon: Home, end: true },
  { to: '/biblioteca', label: 'Biblioteca', icon: Library },
  { to: '/catalogo', label: 'Catálogo', icon: Compass },
  { to: '/perfil', label: 'Perfil', icon: UserRound },
]

export default function AppLayout() {
  const [hovered, setHovered] = useState(null)
  const dispatch = useDispatch()
  const profile = useSelector((state) => state.profile)
  useEffect(() => { if (profile.status === 'idle') dispatch(fetchProfile()) }, [dispatch, profile.status])
  const themeStyle = {
    '--user-bg': profile.backgroundColor,
    '--user-surface': profile.surfaceColor,
    '--accent': profile.accentColor,
  }
  return (
    <div className={`app-shell ${profile.dotsEnabled ? 'dot-background' : ''}`} style={themeStyle}>
      <header className="topbar-wrap">
        <div className="topbar">
          <NavLink className="brand" to="/"><span className="brand-mark"><BookOpen size={20} /></span><span>Anime<span>List</span></span></NavLink>
          <nav className="main-nav" aria-label="Navegación principal" onMouseLeave={() => setHovered(null)}>
            {links.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} onMouseEnter={() => setHovered(to)} className={({ isActive }) => isActive ? 'active' : ''}>
                {hovered === to && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}
                <Icon size={17} /><span>{label}</span>
              </NavLink>
            ))}
          </nav>
          <NotificationBell />
        </div>
      </header>
      <main className="page"><Outlet /></main>
      <ToastViewport />
    </div>
  )
}
