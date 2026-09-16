import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { BookOpen, ChevronLeft, ChevronRight, Clapperboard, FileImage, Heart, Palette, RotateCcw, Save, Sparkles, Star, Trophy, Upload, UserRound } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import ThemeColorPicker from '../components/profile/ThemeColorPicker.jsx'
import ImageCropModal from '../components/profile/ImageCropModal.jsx'
import { prepareImageForCrop } from '../lib/profileImage.js'
import ProfileAnalytics from '../components/profile/ProfileAnalytics.jsx'
import ActivityTimeline from '../components/profile/ActivityTimeline.jsx'
import AniListExportPanel from '../components/profile/AniListExportPanel.jsx'
import { fetchLibrary } from '../features/library/librarySlice.js'
import { selectTopRatedAnime, selectTopRatedManga } from '../features/library/librarySelectors.js'
import { fetchHistory } from '../features/history/historySlice.js'
import { deleteProfileAsset, resetTheme, saveProfile, updateProfile, uploadProfileAsset } from '../features/profile/profileSlice.js'
import { fetchPersonFavorites } from '../features/favorites/favoritesSlice.js'

export default function ProfileView() {
  const dispatch = useDispatch()
  const profile = useSelector((state) => state.profile)
  const libraryState = useSelector((state) => state.library)
  const historyState = useSelector((state) => state.history)
  const favorites = useSelector((state) => state.favorites)
  const library = libraryState.items
  const topAnime = useSelector(selectTopRatedAnime)
  const topManga = useSelector(selectTopRatedManga)
  const update = (changes) => dispatch(updateProfile(changes))
  useEffect(() => { if (libraryState.status === 'idle') dispatch(fetchLibrary()) }, [dispatch, libraryState.status])
  useEffect(() => { if (historyState.status === 'idle') dispatch(fetchHistory()) }, [dispatch, historyState.status])
  useEffect(() => { dispatch(fetchPersonFavorites()) }, [dispatch])
  return (
    <>
      <section className="profile-hero">
        <div className="profile-banner" style={profile.bannerUrl ? { backgroundImage: `url(${profile.bannerUrl})` } : undefined}><Sparkles /></div>
        <div className="profile-summary"><div className="profile-avatar">{profile.avatarUrl ? <img src={profile.avatarUrl} alt="Avatar del perfil" /> : <UserRound />}</div><div><span className="eyebrow">Perfil personal</span><h1>{profile.displayName}</h1><p>{profile.bio}</p></div></div>
      </section>
      <ProfileAnalytics library={library} history={historyState.items} />
      <div className="profile-insights-grid">
        <ActivityTimeline entries={historyState.items} status={historyState.status} error={historyState.error} />
        <section className="profile-highlights"><div className="section-heading"><div><span className="eyebrow">Tu selección</span><h2><Trophy /> Tus mejores puntuados</h2></div></div><div className="top-rated-grid"><TopRated title="Top 5 anime" icon={<Clapperboard />} entries={topAnime} /><TopRated title="Top 5 manga" icon={<BookOpen />} entries={topManga} /></div></section>
      </div>
      <FavoritePeople entries={favorites.people} />
      <AniListExportPanel />
      <div className="profile-layout">
        <section className="theme-panel"><div className="section-heading"><div><span className="eyebrow">Apariencia</span><h2><Palette /> Personalización</h2></div><button className="reset-theme" onClick={() => dispatch(resetTheme())}><RotateCcw /> Restaurar</button></div>
          <label className="profile-field"><span>Nombre mostrado</span><input value={profile.displayName} maxLength={40} onChange={(e) => update({ displayName: e.target.value })} /></label>
          <label className="profile-field"><span>Biografía</span><textarea value={profile.bio} maxLength={160} onChange={(e) => update({ bio: e.target.value })} /></label>
          <div className="profile-file-grid"><ProfileFileField type="avatar" label="Avatar" hint="Recorte cuadrado · WebP 512 × 512" value={profile.avatarUrl} custom={profile.customAvatar} aniListFallback={profile.anilistAvatarUrl} busy={profile.assetStatus === 'uploading'} dispatch={dispatch} /><ProfileFileField type="banner" label="Banner" hint="Recorte panorámico · WebP 1600 × 500" value={profile.bannerUrl} custom={profile.customBanner} aniListFallback={profile.anilistBannerUrl} busy={profile.assetStatus === 'uploading'} dispatch={dispatch} /></div>
          <div className="color-options"><ThemeColorPicker label="Color de fondo" value={profile.backgroundColor} onChange={(backgroundColor) => update({ backgroundColor })} /><ThemeColorPicker label="Tarjetas y rectángulos" value={profile.surfaceColor} onChange={(surfaceColor) => update({ surfaceColor })} /><ThemeColorPicker label="Color de acento" value={profile.accentColor} onChange={(accentColor) => update({ accentColor })} /></div>
          <label className="dot-toggle"><input type="checkbox" checked={profile.dotsEnabled} onChange={(e) => update({ dotsEnabled: e.target.checked })} /><span><b>Dot Background</b><small>Mostrar una trama sutil de puntos en el fondo.</small></span></label>
          <div className="profile-actions"><span>{profile.saved ? 'Cambios guardados' : 'Hay cambios sin guardar'}</span><button className="primary-button" disabled={profile.status === 'saving'} onClick={() => dispatch(saveProfile())}><Save /> {profile.status === 'saving' ? 'Guardando...' : 'Guardar perfil'}</button></div>
        </section>
      </div>
    </>
  )
}

function FavoritePeople({ entries }) {
  const characters = entries.filter((item) => item.personType === 'CHARACTER')
  const staff = entries.filter((item) => item.personType === 'STAFF')
  return <section className="profile-favorite-people"><div className="section-heading"><div><span className="eyebrow">Tu reparto</span><h2><Heart /> Personajes y seiyūs favoritos</h2></div></div><div className="favorite-people-columns"><FavoritePeopleList title="Personajes" entries={characters} path="personajes" /><FavoritePeopleList title="Seiyūs" entries={staff} path="seiyuus" /></div></section>
}

function FavoritePeopleList({ title, entries, path }) {
  const [page, setPage] = useState(0)
  const pageCount = Math.max(1, Math.ceil(entries.length / 8))
  const currentPage = Math.min(page, pageCount - 1)
  const visibleEntries = entries.slice(currentPage * 8, currentPage * 8 + 8)
  return <div className="favorite-people-list"><div className="favorite-people-heading"><h3>{title}</h3>{entries.length > 0 && <span>{entries.length} favoritos</span>}</div>{entries.length ? <><AnimatePresence mode="wait" initial={false}><motion.div className="favorite-people-grid" key={currentPage} initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -18 }} transition={{ duration: .2, ease: 'easeOut' }}>{visibleEntries.map((item) => <Link key={item.id} to={`/${path}/${item.anilistId}`}><img src={item.imageUrl} alt={item.name} /><span><strong>{item.name}</strong>{item.nativeName && <small>{item.nativeName}</small>}</span></Link>)}</motion.div></AnimatePresence>{pageCount > 1 && <div className="favorite-people-controls"><button type="button" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label={`Ocho ${title.toLowerCase()} anteriores`}><ChevronLeft /></button><span>{currentPage + 1} / {pageCount}</span><button type="button" disabled={currentPage === pageCount - 1} onClick={() => setPage(currentPage + 1)} aria-label={`Ocho ${title.toLowerCase()} siguientes`}><ChevronRight /></button></div>}</> : <p>Aún no has agregado favoritos de este tipo.</p>}</div>
}

function ProfileFileField({ type, label, hint, value, custom, aniListFallback, busy, dispatch }) {
  const inputId = `profile-${type}-file`
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
    <div className="profile-file-icon">{value ? <img src={value} alt="" /> : <FileImage />}</div>
    <div><strong>{label}</strong><small>{preparing ? 'Preparando vista previa...' : hint}</small><div className="profile-file-actions"><label htmlFor={inputId} className={busy || preparing ? 'disabled' : ''}><Upload /> {preparing ? 'Preparando...' : custom ? 'Cambiar' : 'Subir'}</label>{custom && <button type="button" disabled={busy || preparing} onClick={() => dispatch(deleteProfileAsset(type))}><RotateCcw /> {aniListFallback ? 'Usar AniList' : 'Quitar'}</button>}</div></div>
    <input id={inputId} type="file" accept="image/jpeg,image/png,image/webp" onChange={choose} disabled={busy || preparing} />
    <AnimatePresence>{selectedFile && <ImageCropModal file={selectedFile} type={type} onCancel={() => setSelectedFile(null)} onConfirm={(file) => { dispatch(uploadProfileAsset({ type, file })); setSelectedFile(null) }} />}</AnimatePresence>
  </article>
}

function TopRated({ title, icon, entries }) {
  return <div className="top-rated"><h3>{icon} {title}</h3>{entries.length ? <div>{entries.map((entry, index) => <Link to={`/media/${entry.anilistId}`} key={entry.id}><span className="top-rated-rank">#{index + 1}</span><img src={entry.coverImage} alt="" /><strong>{entry.title}</strong><span><Star /> {entry.personalScore}</span></Link>)}</div> : <p>Todavía no puntuaste títulos de este tipo.</p>}</div>
}
