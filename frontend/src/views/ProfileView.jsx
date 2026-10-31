import { safeDarkColor, safeAccentColor, accentTextColor } from '../lib/themeContrast.js'
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { BookOpen, Camera, ChevronLeft, ChevronRight, Clapperboard, FileImage, Heart, Languages, LogOut, Palette, RotateCcw, Save, ShieldCheck, Sparkles, Star, Trophy, Upload, UserRound } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { prepareImageForCrop } from '../lib/profileImage.js'
import ProfileAnalytics from '../components/profile/ProfileAnalytics.jsx'
import ActivityTimeline from '../components/profile/ActivityTimeline.jsx'
import { fetchLibrary } from '../features/library/librarySlice.js'
import { selectTopRatedAnime, selectTopRatedManga } from '../features/library/librarySelectors.js'
import { fetchHistory } from '../features/history/historySlice.js'
import { deleteProfileAsset, saveProfile, uploadProfileAsset } from '../features/profile/profileSlice.js'
import { fetchPersonFavorites } from '../features/favorites/favoritesSlice.js'
import useSignOut from '../lib/useSignOut.js'

const ImageCropModal = lazy(() => import('../components/profile/ImageCropModal.jsx'))
const AniListExportPanel = lazy(() => import('../components/profile/AniListExportPanel.jsx'))
const ThemeColorPicker = lazy(() => import('../components/profile/ThemeColorPicker.jsx'))
const PwaSettingsPanel = lazy(() => import('../components/profile/PwaSettingsPanel.jsx'))
const draftKeys = ['displayName', 'bio', 'backgroundColor', 'surfaceColor', 'accentColor', 'dotsEnabled', 'adultContentMode', 'titleLanguage']
const defaultTheme = { backgroundColor: '#0a0c14', surfaceColor: '#171b29', accentColor: '#a88bff', dotsEnabled: true }
const profileDraft = (profile) => Object.fromEntries(draftKeys.map((key) => [key, profile[key]]))

export default function ProfileView() {
  const dispatch = useDispatch()
  const signOut = useSignOut()
  const profile = useSelector((state) => state.profile)
  const libraryState = useSelector((state) => state.library)
  const historyState = useSelector((state) => state.history)
  const favorites = useSelector((state) => state.favorites)
  const library = libraryState.items
  const topAnime = useSelector(selectTopRatedAnime)
  const topManga = useSelector(selectTopRatedManga)
  const savedDraft = profileDraft(profile)
  const savedDraftKey = JSON.stringify(savedDraft)
  const [draftSource, setDraftSource] = useState(savedDraftKey)
  const [draft, setDraft] = useState(savedDraft)
  if (profile.status === 'succeeded' && profile.saved && draftSource !== savedDraftKey) {
    setDraftSource(savedDraftKey)
    setDraft(savedDraft)
  }
  const update = (changes) => setDraft((current) => ({ ...current, ...changes }))
  const dirty = draftKeys.some((key) => draft[key] !== profile[key])
  useEffect(() => {
    if (!dirty) return undefined
    const warnAboutUnsavedChanges = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }
    const confirmInternalNavigation = (event) => {
      const link = event.target.closest?.('a[href]')
      if (!link || link.target === '_blank' || link.href === window.location.href) return
      if (!window.confirm('Hay cambios de perfil sin guardar. ¿Querés salir de esta pantalla?')) {
        event.preventDefault()
        event.stopPropagation()
      }
    }
    window.addEventListener('beforeunload', warnAboutUnsavedChanges)
    document.addEventListener('click', confirmInternalNavigation, true)
    return () => {
      window.removeEventListener('beforeunload', warnAboutUnsavedChanges)
      document.removeEventListener('click', confirmInternalNavigation, true)
    }
  }, [dirty])
  useEffect(() => { if (libraryState.status === 'idle') dispatch(fetchLibrary()) }, [dispatch, libraryState.status])
  useEffect(() => { if (historyState.status === 'idle') dispatch(fetchHistory()) }, [dispatch, historyState.status])
  useEffect(() => { dispatch(fetchPersonFavorites()) }, [dispatch])
  useEffect(() => {
    const shell = document.querySelector('.app-shell')
    if (!shell) return undefined
    const variables = { '--user-bg': safeDarkColor(draft.backgroundColor), '--user-surface': safeDarkColor(draft.surfaceColor), '--surface': safeDarkColor(draft.surfaceColor), '--accent': safeAccentColor(draft.accentColor), '--accent-text': accentTextColor(safeAccentColor(draft.accentColor)) }
    Object.entries(variables).forEach(([name, value]) => shell.style.setProperty(name, value))
    shell.classList.toggle('dot-background', draft.dotsEnabled)
    return () => {
      const savedVariables = { '--user-bg': safeDarkColor(profile.backgroundColor), '--user-surface': safeDarkColor(profile.surfaceColor), '--surface': safeDarkColor(profile.surfaceColor), '--accent': safeAccentColor(profile.accentColor), '--accent-text': accentTextColor(safeAccentColor(profile.accentColor)) }
      Object.entries(savedVariables).forEach(([name, value]) => shell.style.setProperty(name, value))
      shell.classList.toggle('dot-background', profile.dotsEnabled)
    }
  }, [draft.backgroundColor, draft.surfaceColor, draft.accentColor, draft.dotsEnabled, profile.backgroundColor, profile.surfaceColor, profile.accentColor, profile.dotsEnabled])
  return (
    <>
      <section className="profile-hero">
        <div className="profile-banner" style={profile.bannerUrl ? { backgroundImage: `url(${profile.bannerUrl})` } : undefined}><Sparkles /></div>
        <div className="profile-summary"><div className="profile-avatar">{profile.avatarUrl ? <img src={profile.avatarUrl} alt="Avatar del perfil" decoding="async" /> : <UserRound />}</div><div><span className="eyebrow">Perfil personal</span><h1>{draft.displayName}</h1><p>{draft.bio}</p></div></div>
      </section>
      <ProfileAnalytics library={library} history={historyState.items} />
      <div className="profile-insights-grid">
        <ActivityTimeline entries={historyState.items} status={historyState.status} error={historyState.error} />
        <section className="profile-highlights"><div className="section-heading"><div><span className="eyebrow">Tu selección</span><h2><Trophy /> Tus mejores puntuados</h2></div></div><div className="top-rated-grid"><TopRated title="Top 5 anime" icon={<Clapperboard />} entries={topAnime} /><TopRated title="Top 5 manga" icon={<BookOpen />} entries={topManga} /></div></section>
      </div>
      <FavoritePeople entries={favorites.people} />
      <ViewportSection className="profile-deferred-section"><Suspense fallback={null}><AniListExportPanel /></Suspense></ViewportSection>
      <ViewportSection className="profile-deferred-section"><Suspense fallback={null}><PwaSettingsPanel /></Suspense></ViewportSection>
      <div className="profile-layout">
        <section className="theme-panel"><div className="section-heading"><div><span className="eyebrow">Apariencia</span><h2><Palette /> Personalización</h2></div><button className="reset-theme" onClick={() => update(defaultTheme)}><RotateCcw /> Restaurar</button></div>
          <label className="profile-field"><span>Nombre mostrado</span><input value={draft.displayName} maxLength={40} onChange={(e) => update({ displayName: e.target.value })} /></label>
          <label className="profile-field"><span>Biografía</span><textarea value={draft.bio} maxLength={160} onChange={(e) => update({ bio: e.target.value })} /></label>
          <div className="profile-file-grid"><ProfileFileField type="avatar" label="Avatar" hint="Recorte cuadrado · WebP 512 × 512" value={profile.avatarUrl} custom={profile.customAvatar} aniListFallback={profile.anilistAvatarUrl} busy={profile.assetStatus === 'uploading'} dispatch={dispatch} /><ProfileFileField type="banner" label="Banner" hint="Recorte panorámico · WebP 1600 × 500" value={profile.bannerUrl} custom={profile.customBanner} aniListFallback={profile.anilistBannerUrl} busy={profile.assetStatus === 'uploading'} dispatch={dispatch} /></div>
          <ViewportSection className="color-options profile-color-options"><Suspense fallback={<div className="profile-color-loading" aria-label="Cargando colores" />}><ThemeColorPicker dark label="Color de fondo" value={draft.backgroundColor} onChange={(backgroundColor) => update({ backgroundColor })} /><ThemeColorPicker label="Tarjetas y rectángulos" value={draft.surfaceColor} onChange={(surfaceColor) => update({ surfaceColor })} /><ThemeColorPicker label="Color de acento" value={draft.accentColor} onChange={(accentColor) => update({ accentColor })} /></Suspense></ViewportSection>
          <p className="theme-contrast-hint">Los fondos se ajustan automáticamente para mantener legibles los textos.</p><label className="dot-toggle"><input type="checkbox" checked={draft.dotsEnabled} onChange={(e) => update({ dotsEnabled: e.target.checked })} /><span><b>Dot Background</b><small>Mostrar una trama sutil de puntos en el fondo.</small></span></label>
          <div className="content-preferences" aria-labelledby="content-preferences-title">
            <div className="content-preferences-heading"><span className="eyebrow">Descubrimiento</span><h3 id="content-preferences-title">Contenido y títulos</h3><p>Elegí cómo querés explorar AniRecord. Estas opciones se aplican a toda la página.</p></div>
            <PreferenceChoice icon={<ShieldCheck />} label="Contenido adulto" description="Las obras que ya guardaste nunca desaparecen de tu biblioteca." value={draft.adultContentMode} onChange={(adultContentMode) => update({ adultContentMode })} options={[['HIDE', 'Ocultar'], ['BLUR', 'Difuminar'], ['SHOW', 'Mostrar']]} />
            <PreferenceChoice icon={<Languages />} label="Idioma de títulos" description="Si una traducción no existe, se usa automáticamente la mejor alternativa disponible." value={draft.titleLanguage} onChange={(titleLanguage) => update({ titleLanguage })} options={[['AUTO', 'Automático'], ['ENGLISH', 'Inglés'], ['ROMAJI', 'Romaji'], ['NATIVE', 'Original']]} />
          </div>
          <div className="profile-actions"><span>{dirty ? 'Hay cambios sin guardar' : 'Cambios guardados'}</span><button className="primary-button" disabled={profile.status === 'saving' || !dirty} onClick={() => dispatch(saveProfile(draft))}><Save /> {profile.status === 'saving' ? 'Guardando...' : 'Guardar perfil'}</button></div>
        </section>
      </div>
      <section className="profile-mobile-session"><div><span className="eyebrow">Cuenta</span><strong>Sesión de AniRecord</strong><small>Cerrá la sesión de este dispositivo.</small></div><button type="button" onClick={signOut}><LogOut /> Cerrar sesión</button></section>
    </>
  )
}

function PreferenceChoice({ icon, label, description, value, onChange, options }) {
  return <fieldset className="preference-choice"><legend><span>{icon}</span><span><strong>{label}</strong><small>{description}</small></span></legend><div>{options.map(([optionValue, optionLabel]) => <button type="button" key={optionValue} className={value === optionValue ? 'active' : ''} aria-pressed={value === optionValue} onClick={() => onChange(optionValue)}>{optionLabel}</button>)}</div></fieldset>
}

function ViewportSection({ children, className = '' }) {
  const ref = useRef(null)
  const [ready, setReady] = useState(false)
  useEffect(() => {
    if (ready) return undefined
    if (!('IntersectionObserver' in window)) {
      const timer = window.setTimeout(() => setReady(true), 0)
      return () => window.clearTimeout(timer)
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setReady(true)
        observer.disconnect()
      }
    }, { rootMargin: '600px 0px' })
    if (ref.current) observer.observe(ref.current)
    return () => observer.disconnect()
  }, [ready])
  return <div ref={ref} className={className}>{ready ? children : null}</div>
}

function FavoritePeople({ entries }) {
  const [characters, staff] = useMemo(() => [entries.filter((item) => item.personType === 'CHARACTER'), entries.filter((item) => item.personType === 'STAFF')], [entries])
  return <section className="profile-favorite-people"><div className="section-heading"><div><span className="eyebrow">Tu reparto</span><h2><Heart /> Personajes y seiyūs favoritos</h2></div></div><div className="favorite-people-columns"><FavoritePeopleList title="Personajes" entries={characters} path="personajes" /><FavoritePeopleList title="Seiyūs" entries={staff} path="seiyuus" /></div></section>
}

function FavoritePeopleList({ title, entries, path }) {
  const [page, setPage] = useState(0)
  const pageCount = Math.max(1, Math.ceil(entries.length / 8))
  const currentPage = Math.min(page, pageCount - 1)
  const visibleEntries = entries.slice(currentPage * 8, currentPage * 8 + 8)
  return <div className="favorite-people-list"><div className="favorite-people-heading"><h3>{title}</h3>{entries.length > 0 && <span>{entries.length} favoritos</span>}</div>{entries.length ? <><AnimatePresence mode="wait" initial={false}><motion.div className="favorite-people-grid" key={currentPage} initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -18 }} transition={{ duration: .2, ease: 'easeOut' }}>{visibleEntries.map((item) => <Link key={item.id} to={`/${path}/${item.anilistId}`}><img src={item.imageUrl} alt={item.name} loading="lazy" decoding="async" /><span><strong>{item.name}</strong>{item.nativeName && <small>{item.nativeName}</small>}</span></Link>)}</motion.div></AnimatePresence>{pageCount > 1 && <div className="favorite-people-controls"><button type="button" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label={`Ocho ${title.toLowerCase()} anteriores`}><ChevronLeft /></button><span>{currentPage + 1} / {pageCount}</span><button type="button" disabled={currentPage === pageCount - 1} onClick={() => setPage(currentPage + 1)} aria-label={`Ocho ${title.toLowerCase()} siguientes`}><ChevronRight /></button></div>}</> : <p>Aún no has agregado favoritos de este tipo.</p>}</div>
}

function ProfileFileField({ type, label, hint, value, custom, aniListFallback, busy, dispatch }) {
  const inputId = `profile-${type}-file`
  const cameraInputId = `profile-${type}-camera`
  const [selectedFile, setSelectedFile] = useState(null)
  const [preparing, setPreparing] = useState(false)
  const choose = async (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setPreparing(true)
    try { setSelectedFile(await prepareImageForCrop(file, type)) }
    finally { setPreparing(false) }
  }
  return <article className="profile-file-field">
    <div className="profile-file-icon">{value ? <img src={value} alt="" decoding="async" /> : <FileImage />}</div>
    <div><strong>{label}</strong><small>{preparing ? 'Preparando vista previa...' : hint}</small><div className="profile-file-actions"><label htmlFor={inputId} className={busy || preparing ? 'disabled' : ''}><Upload /> {preparing ? 'Preparando...' : custom ? 'Cambiar' : 'Subir'}</label><label htmlFor={cameraInputId} className={busy || preparing ? 'disabled' : ''}><Camera /> Cámara</label>{custom && <button type="button" disabled={busy || preparing} onClick={() => dispatch(deleteProfileAsset(type))}><RotateCcw /> {aniListFallback ? 'Usar AniList' : 'Quitar'}</button>}</div></div>
    <input id={inputId} type="file" accept="image/jpeg,image/png,image/webp" onChange={choose} disabled={busy || preparing} />
    <input id={cameraInputId} type="file" accept="image/*" capture={type === 'avatar' ? 'user' : 'environment'} onChange={choose} disabled={busy || preparing} />
    <AnimatePresence>{selectedFile && <Suspense fallback={null}><ImageCropModal file={selectedFile} type={type} onCancel={() => setSelectedFile(null)} onConfirm={(file) => { dispatch(uploadProfileAsset({ type, file })); setSelectedFile(null) }} /></Suspense>}</AnimatePresence>
  </article>
}

function TopRated({ title, icon, entries }) {
  return <div className="top-rated"><h3>{icon} {title}</h3>{entries.length ? <div>{entries.map((entry, index) => <Link to={`/media/${entry.anilistId}`} key={entry.id}><span className="top-rated-rank">#{index + 1}</span><img src={entry.coverImage} alt="" loading="lazy" decoding="async" /><strong>{entry.title}</strong><span><Star /> {entry.personalScore}</span></Link>)}</div> : <p>Todavía no puntuaste títulos de este tipo.</p>}</div>
}
