import { safeDarkColor, safeAccentColor, accentTextColor } from '../../lib/themeContrast.js'
import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Compass, Home, Library, LogIn, LogOut, Sparkles, UserRound } from 'lucide-react'
import { motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { NavLink, useLocation, useNavigate, useOutlet } from 'react-router-dom'
import { fetchProfile } from '../../features/profile/profileSlice.js'
import NotificationBell from './NotificationBell.jsx'
import ChangelogModal, { VersionButton } from './ChangelogModal.jsx'
import ToastViewport from '../common/ToastViewport.jsx'
import BrandMark from '../common/BrandMark.jsx'
import PwaUpdateToast from '../common/PwaUpdateToast.jsx'
import { updatePwaBranding } from '../../lib/pwaBranding.js'
import { logout } from '../../features/auth/authSlice.js'
import { activePushSubscription } from '../../lib/pwaInstall.js'
import { requestLogin } from '../../lib/authNavigation.js'

const AssistantView = lazy(() => import('../../views/AssistantView.jsx'))

const links = [
  { to: '/', label: 'Inicio', icon: Home, end: true },
  { to: '/biblioteca', label: 'Biblioteca', icon: Library },
  { to: '/catalogo', label: 'Catálogo', icon: Compass },
]

export default function AppLayout() {
  const [hovered, setHovered] = useState(null)
  const [changelogOpen, setChangelogOpen] = useState(false)
  const [assistantOpen, setAssistantOpen] = useState(false)
  const [assistantLoaded, setAssistantLoaded] = useState(false)
  const location = useLocation()
  const { pathname } = location
  const navigate = useNavigate()
  const mainRef = useRef(null)
  const outlet = useOutlet()
  const dispatch = useDispatch()
  const profileStatus = useSelector((state) => state.profile.status)
  const avatarUrl = useSelector((state) => state.profile.avatarUrl)
  const backgroundColor = useSelector((state) => state.profile.backgroundColor)
  const surfaceColor = useSelector((state) => state.profile.surfaceColor)
  const accentColor = useSelector((state) => state.profile.accentColor)
  const dotsEnabled = useSelector((state) => state.profile.dotsEnabled)
  const authenticated = useSelector((state) => state.auth.authenticated)
  const openAssistant = useCallback(() => {
    if (!authenticated) {
      requestLogin(navigate, location)
      return
    }
    setAssistantLoaded(true)
    setAssistantOpen(true)
  }, [authenticated, location, navigate])
  const closeAssistant = useCallback(() => setAssistantOpen(false), [])
  useEffect(() => {
    if (!location.state?.openAssistant || !authenticated) return
    navigate(pathname, { replace: true, state: null })
    window.setTimeout(openAssistant, 0)
  }, [authenticated, location.state, navigate, openAssistant, pathname])
  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
    const names = { '/': 'Inicio', '/biblioteca': 'Biblioteca', '/catalogo': 'Catálogo', '/asistente': 'Asistente', '/perfil': 'Perfil', '/login': 'Iniciar sesión' }
    document.title = `${names[pathname] || 'Anime y manga'} · AniRecord`
    mainRef.current?.focus({ preventScroll: true })
  }, [pathname])
  const signOut = async () => {
    const subscription = await activePushSubscription()
    if (subscription) await subscription.unsubscribe().catch(() => false)
    await dispatch(logout(subscription?.endpoint || null))
    window.localStorage.removeItem('persist:anime-list')
    window.localStorage.removeItem('persist:anime-list-v2')
    window.location.replace('/')
  }
  useEffect(() => { if (authenticated && profileStatus === 'idle') dispatch(fetchProfile()) }, [authenticated, dispatch, profileStatus])
  const themeStyle = {
    '--user-bg': safeDarkColor(backgroundColor),
    '--user-surface': safeDarkColor(surfaceColor),
    '--accent': safeAccentColor(accentColor), '--accent-text': accentTextColor(safeAccentColor(accentColor)),
    '--surface': safeDarkColor(surfaceColor),
  }
  useEffect(() => {
    const root = document.documentElement
    const variables = {
      '--user-bg': safeDarkColor(backgroundColor),
      '--user-surface': safeDarkColor(surfaceColor),
      '--surface': safeDarkColor(surfaceColor),
      '--accent': safeAccentColor(accentColor), '--accent-text': accentTextColor(safeAccentColor(accentColor)),
    }
    Object.entries(variables).forEach(([name, value]) => value && root.style.setProperty(name, value))
    updatePwaBranding(safeAccentColor(accentColor), safeDarkColor(backgroundColor))
    return () => Object.keys(variables).forEach((name) => root.style.removeProperty(name))
  }, [accentColor, backgroundColor, surfaceColor])
  return (
    <div className={`app-shell ${dotsEnabled ? 'dot-background' : ''}`} style={themeStyle}>
      <a className="skip-content" href="#main-content">Saltar al contenido</a>
      <header className="topbar-wrap">
        <div className="topbar">
          <NavLink className="brand" to="/"><span className="brand-mark"><BrandMark /></span><span>Ani<span>Record</span></span></NavLink>
          <VersionButton onClick={() => setChangelogOpen(true)} />
          <nav className="main-nav" aria-label="Navegación principal" onMouseLeave={() => setHovered(null)}>
            {links.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} onMouseEnter={() => setHovered(to)} className={({ isActive }) => isActive ? 'active' : ''}>
                {hovered === to && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}
                <Icon size={17} /><span>{label}</span>
              </NavLink>
            ))}
            <button type="button" className={`assistant-nav-button${assistantOpen ? ' active' : ''}`} aria-label="Abrir asistente" aria-expanded={assistantOpen} aria-controls="global-assistant-panel" title="Asistente" onMouseEnter={() => setHovered('/asistente')} onClick={openAssistant}>
              {hovered === '/asistente' && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}
              <Sparkles size={17} /><span>Asistente</span>
            </button>
            <NavLink to={authenticated ? '/perfil' : '/login'} aria-label={authenticated ? 'Perfil' : 'Iniciar sesión'} title={authenticated ? 'Perfil' : 'Iniciar sesión'} onMouseEnter={() => setHovered(authenticated ? '/perfil' : '/login')} className={({ isActive }) => `nav-profile${isActive ? ' active' : ''}`}>
              {hovered === (authenticated ? '/perfil' : '/login') && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}
              <span className="nav-avatar">{authenticated && avatarUrl ? <img src={avatarUrl} alt="" decoding="async" /> : authenticated ? <UserRound size={18} /> : <LogIn size={18} />}</span>
            </NavLink>
            {authenticated && <NotificationBell hovered={hovered === '/notificaciones'} onMouseEnter={() => setHovered('/notificaciones')} />}
          </nav>
          {authenticated && <button className="logout-button" onClick={signOut} title="Cerrar sesión" aria-label="Cerrar sesión"><LogOut size={17} /></button>}
        </div>
      </header>
      <main id="main-content" ref={mainRef} tabIndex={-1} className="page"><motion.div className="route-page" key={pathname} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .14, ease: 'easeOut' }}>{outlet}</motion.div></main>
      <ToastViewport />
      <PwaUpdateToast />
      <ChangelogModal open={changelogOpen} onClose={() => setChangelogOpen(false)} />
      {assistantLoaded && <Suspense fallback={null}><AssistantView panel open={assistantOpen} onClose={closeAssistant} /></Suspense>}
    </div>
  )
}
