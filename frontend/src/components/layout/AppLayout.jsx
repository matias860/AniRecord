import { safeDarkColor, safeAccentColor, accentTextColor } from '../../lib/themeContrast.js'
import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Compass, Home, Library, LogIn, LogOut, Sparkles, UserRound } from 'lucide-react'
import { motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { NavLink, useLocation, useNavigate, useNavigationType, useOutlet } from 'react-router-dom'
import { fetchProfile } from '../../features/profile/profileSlice.js'
import NotificationBell from './NotificationBell.jsx'
import ChangelogModal, { VersionButton } from './ChangelogModal.jsx'
import ToastViewport from '../common/ToastViewport.jsx'
import BrandMark from '../common/BrandMark.jsx'
import PwaUpdateToast from '../common/PwaUpdateToast.jsx'
import { updatePwaBranding } from '../../lib/pwaBranding.js'
import { requestLogin } from '../../lib/authNavigation.js'
import { preloadRoute } from '../../lib/routeModules.js'
import { routeSectionTitle } from '../../lib/navigation.js'
import useSignOut from '../../lib/useSignOut.js'
import ProfileLoader from '../common/ProfileLoader.jsx'

const AssistantView = lazy(() => import('../../views/AssistantView.jsx'))
const scrollPositions = new Map()
const routeFallback = <div className="page-state full-page route-loading"><ProfileLoader message="Cargando…" /></div>

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
  const navigationType = useNavigationType()
  const { pathname } = location
  const navigate = useNavigate()
  const mainRef = useRef(null)
  const previousPathname = useRef(null)
  const activeScrollKey = useRef(location.key)
  const outlet = useOutlet()
  const dispatch = useDispatch()
  const profileStatus = useSelector((state) => state.profile.status)
  const avatarUrl = useSelector((state) => state.profile.avatarUrl)
  const backgroundColor = useSelector((state) => state.profile.backgroundColor)
  const surfaceColor = useSelector((state) => state.profile.surfaceColor)
  const accentColor = useSelector((state) => state.profile.accentColor)
  const dotsEnabled = useSelector((state) => state.profile.dotsEnabled)
  const authenticated = useSelector((state) => state.auth.authenticated)
  const displayName = useSelector((state) => state.profile.displayName)
  const signOut = useSignOut()
  const openAssistant = useCallback(() => {
    if (!authenticated) {
      requestLogin(navigate, location)
      return
    }
    setAssistantLoaded(true)
    setAssistantOpen(true)
  }, [authenticated, location, navigate])
  const closeAssistant = useCallback(() => setAssistantOpen(false), [])
  const rememberCurrentScroll = useCallback(() => {
    scrollPositions.set(location.key, window.scrollY)
  }, [location.key])
  useEffect(() => {
    if (!location.state?.openAssistant || !authenticated) return
    navigate(pathname, { replace: true, state: null })
    window.setTimeout(openAssistant, 0)
  }, [authenticated, location.state, navigate, openAssistant, pathname])
  useLayoutEffect(() => {
    const savedPosition = scrollPositions.get(location.key)
    const pathChanged = previousPathname.current !== pathname
    let restoreFrame = null
    activeScrollKey.current = location.key
    if (navigationType === 'POP' || pathChanged) {
      const top = navigationType === 'POP' && savedPosition != null ? savedPosition : 0
      window.scrollTo({ top, left: 0, behavior: 'auto' })
      restoreFrame = window.requestAnimationFrame(() => window.scrollTo({ top, left: 0, behavior: 'auto' }))
    }
    document.title = `${routeSectionTitle(pathname)} · AniRecord`
    if (pathChanged && navigationType !== 'POP') mainRef.current?.focus({ preventScroll: true })
    previousPathname.current = pathname
    return () => { if (restoreFrame != null) window.cancelAnimationFrame(restoreFrame) }
  }, [location.key, navigationType, pathname])
  useEffect(() => {
    let frame = null
    const rememberPosition = () => {
      if (frame != null) return
      frame = window.requestAnimationFrame(() => {
        scrollPositions.set(activeScrollKey.current, window.scrollY)
        if (scrollPositions.size > 50) scrollPositions.delete(scrollPositions.keys().next().value)
        frame = null
      })
    }
    window.addEventListener('scroll', rememberPosition, { passive: true })
    return () => {
      window.removeEventListener('scroll', rememberPosition)
      if (frame != null) window.cancelAnimationFrame(frame)
    }
  }, [])
  useEffect(() => {
    const previous = window.history.scrollRestoration
    window.history.scrollRestoration = 'manual'
    return () => { window.history.scrollRestoration = previous }
  }, [])
  useEffect(() => {
    if (navigator.connection?.saveData) return undefined
    const warmRoutes = () => Promise.allSettled([preloadRoute('/biblioteca'), preloadRoute('/catalogo')])
    if ('requestIdleCallback' in window) {
      const handle = window.requestIdleCallback(warmRoutes, { timeout: 3500 })
      return () => window.cancelIdleCallback(handle)
    }
    const handle = window.setTimeout(warmRoutes, 1800)
    return () => window.clearTimeout(handle)
  }, [])
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
    <div className={`app-shell ${dotsEnabled ? 'dot-background' : ''}`} style={themeStyle} onClickCapture={rememberCurrentScroll}>
      <a className="skip-content" href="#main-content">Saltar al contenido</a>
      <header className="topbar-wrap">
        <div className="topbar">
          <NavLink className="brand desktop-brand" to="/"><span className="brand-mark"><BrandMark /></span><span>Ani<span>Record</span></span></NavLink>
          <VersionButton variant="mark" onClick={() => setChangelogOpen(true)} />
          <div className="mobile-route-title"><small>AniRecord</small><strong>{routeSectionTitle(pathname)}</strong></div>
          <div className="desktop-version"><VersionButton onClick={() => setChangelogOpen(true)} /></div>
          <nav className="main-nav desktop-main-nav" aria-label="Navegación principal" onMouseLeave={() => setHovered(null)}>
            {links.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} onMouseEnter={() => { setHovered(to); preloadRoute(to) }} onFocus={() => preloadRoute(to)} className={({ isActive }) => isActive ? 'active' : ''}>
                {hovered === to && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}
                <Icon size={17} /><span>{label}</span>
              </NavLink>
            ))}
            <button type="button" className={`assistant-nav-button${assistantOpen ? ' active' : ''}`} aria-label="Abrir asistente" aria-expanded={assistantOpen} aria-controls="global-assistant-panel" title="Asistente" onMouseEnter={() => setHovered('/asistente')} onClick={openAssistant}>
              {hovered === '/asistente' && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}
              <Sparkles size={17} /><span>Asistente</span>
            </button>
          </nav>
          <div className="topbar-actions">
            {authenticated && <NotificationBell hovered={hovered === '/notificaciones'} onMouseEnter={() => setHovered('/notificaciones')} />}
            {authenticated
              ? <AccountMenu avatarUrl={avatarUrl} displayName={displayName} onOpenChangelog={() => setChangelogOpen(true)} onSignOut={signOut} />
              : <NavLink className="desktop-login-button" to="/login" onFocus={() => preloadRoute('/login')}><LogIn /><span>Ingresar</span></NavLink>}
          </div>
        </div>
      </header>
      <nav className="mobile-bottom-nav" aria-label="Navegación principal móvil">
        <MobileNavLink to="/" label="Inicio" icon={Home} end />
        <MobileNavLink to="/biblioteca" label="Biblioteca" icon={Library} />
        <button type="button" className={`mobile-assistant-button${assistantOpen ? ' active' : ''}`} aria-label="Abrir asistente" aria-expanded={assistantOpen} aria-controls="global-assistant-panel" onClick={openAssistant}><span><Sparkles /></span><small>Asistente</small></button>
        <MobileNavLink to="/catalogo" label="Catálogo" icon={Compass} />
        <MobileNavLink to={authenticated ? '/perfil' : '/login'} label={authenticated ? 'Perfil' : 'Ingresar'} icon={authenticated ? UserRound : LogIn} avatarUrl={authenticated ? avatarUrl : null} />
      </nav>
      <main id="main-content" ref={mainRef} tabIndex={-1} className="page"><Suspense fallback={routeFallback}><motion.div className="route-page" key={pathname} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .14, ease: 'easeOut' }}>{outlet}</motion.div></Suspense></main>
      <ToastViewport />
      <PwaUpdateToast />
      <ChangelogModal open={changelogOpen} onClose={() => setChangelogOpen(false)} />
      {assistantLoaded && <Suspense fallback={null}><AssistantView panel open={assistantOpen} onClose={closeAssistant} /></Suspense>}
    </div>
  )
}

function MobileNavLink({ to, label, icon: Icon, avatarUrl, end = false }) {
  return <NavLink to={to} end={end} onPointerEnter={() => preloadRoute(to)} onFocus={() => preloadRoute(to)} className={({ isActive }) => isActive ? 'active' : ''}>
    <span className={avatarUrl ? 'mobile-nav-avatar' : ''}>{avatarUrl ? <img src={avatarUrl} alt="" decoding="async" /> : <Icon />}</span><small>{label}</small>
  </NavLink>
}

function AccountMenu({ avatarUrl, displayName, onOpenChangelog, onSignOut }) {
  const [open, setOpen] = useState(false)
  const root = useRef(null)
  useEffect(() => {
    if (!open) return undefined
    const close = (event) => {
      if (event.key === 'Escape' || !root.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', close)
    }
  }, [open])
  return <div className="account-menu" ref={root}>
    <button className="account-trigger" type="button" onClick={() => setOpen((current) => !current)} aria-label="Abrir menú de cuenta" aria-expanded={open} aria-controls="account-menu-popover"><span className="nav-avatar">{avatarUrl ? <img src={avatarUrl} alt="" decoding="async" /> : <UserRound />}</span></button>
    {open && <div className="account-popover" id="account-menu-popover" role="menu">
      <div className="account-summary"><span className="nav-avatar">{avatarUrl ? <img src={avatarUrl} alt="" /> : <UserRound />}</span><span><small>Sesión activa</small><strong>{displayName || 'Mi perfil'}</strong></span></div>
      <NavLink to="/perfil" role="menuitem" onClick={() => setOpen(false)}><UserRound /> Perfil y configuración</NavLink>
      <button type="button" role="menuitem" onClick={() => { setOpen(false); onOpenChangelog() }}><Sparkles /> Historial de cambios</button>
      <button className="account-signout" type="button" role="menuitem" onClick={onSignOut}><LogOut /> Cerrar sesión</button>
    </div>}
  </div>
}
