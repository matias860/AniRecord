import { useEffect, useState } from 'react'
import { BookOpen, Compass, Home, Library, LogIn, LogOut, UserRound } from 'lucide-react'
import { motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { NavLink, Outlet } from 'react-router-dom'
import { fetchProfile } from '../../features/profile/profileSlice.js'
import NotificationBell from './NotificationBell.jsx'
import ToastViewport from '../common/ToastViewport.jsx'
import { logout } from '../../features/auth/authSlice.js'

const links = [
  { to: '/', label: 'Inicio', icon: Home, end: true },
  { to: '/biblioteca', label: 'Biblioteca', icon: Library },
  { to: '/catalogo', label: 'Catálogo', icon: Compass },
]

export default function AppLayout() {
  const [hovered, setHovered] = useState(null)
  const dispatch = useDispatch()
  const profile = useSelector((state) => state.profile)
  const accountType = useSelector((state) => state.auth.accountType)
  const authenticated = useSelector((state) => state.auth.authenticated)
  const signOut = async () => { await dispatch(logout()); window.localStorage.removeItem('persist:anime-list'); window.localStorage.removeItem('persist:anime-list-v2'); window.location.replace('/') }
  useEffect(() => { if (authenticated && profile.status === 'idle') dispatch(fetchProfile()) }, [authenticated, dispatch, profile.status])
  const themeStyle = {
    '--user-bg': profile.backgroundColor,
    '--user-surface': profile.surfaceColor,
    '--accent': profile.accentColor,
    '--surface': profile.surfaceColor,
  }
  useEffect(() => {
    const root = document.documentElement
    const variables = {
      '--user-bg': profile.backgroundColor,
      '--user-surface': profile.surfaceColor,
      '--surface': profile.surfaceColor,
      '--accent': profile.accentColor,
    }
    Object.entries(variables).forEach(([name, value]) => value && root.style.setProperty(name, value))
    return () => Object.keys(variables).forEach((name) => root.style.removeProperty(name))
  }, [profile.accentColor, profile.backgroundColor, profile.surfaceColor])
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
            <NavLink to={authenticated ? '/perfil' : '/login'} aria-label={authenticated ? 'Perfil' : 'Iniciar sesión'} title={authenticated ? 'Perfil' : 'Iniciar sesión'} onMouseEnter={() => setHovered(authenticated ? '/perfil' : '/login')} className={({ isActive }) => `nav-profile${isActive ? ' active' : ''}`}>
              {hovered === (authenticated ? '/perfil' : '/login') && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}
              <span className="nav-avatar">{authenticated && profile.avatarUrl ? <img src={profile.avatarUrl} alt="" /> : authenticated ? <UserRound size={18} /> : <LogIn size={18} />}</span>
            </NavLink>
            {authenticated && accountType === 'ANILIST' && <NotificationBell hovered={hovered === '/notificaciones'} onMouseEnter={() => setHovered('/notificaciones')} />}
          </nav>
          {authenticated && <button className="logout-button" onClick={signOut} title="Cerrar sesión" aria-label="Cerrar sesión"><LogOut size={17} /></button>}
        </div>
      </header>
      <main className="page"><Outlet /></main>
      <ToastViewport />
    </div>
  )
}
