import { useEffect } from 'react'
import { BookOpen, Clapperboard, FileImage, Palette, RefreshCw, RotateCcw, Save, Sparkles, Star, Trash2, Trophy, Upload, UserRound } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import ThemeColorPicker from '../components/profile/ThemeColorPicker.jsx'
import ProfileAnalytics from '../components/profile/ProfileAnalytics.jsx'
import ActivityTimeline from '../components/profile/ActivityTimeline.jsx'
import { fetchLibrary, synchronizeAniListLibrary } from '../features/library/librarySlice.js'
import { selectTopRatedAnime, selectTopRatedManga } from '../features/library/librarySelectors.js'
import { fetchHistory } from '../features/history/historySlice.js'
import { deleteProfileAsset, resetTheme, saveProfile, updateProfile, uploadProfileAsset } from '../features/profile/profileSlice.js'

export default function ProfileView() {
  const dispatch = useDispatch()
  const profile = useSelector((state) => state.profile)
  const libraryState = useSelector((state) => state.library)
  const historyState = useSelector((state) => state.history)
  const library = libraryState.items
  const topAnime = useSelector(selectTopRatedAnime)
  const topManga = useSelector(selectTopRatedManga)
  const update = (changes) => dispatch(updateProfile(changes))
  useEffect(() => { if (libraryState.status === 'idle') dispatch(fetchLibrary()) }, [dispatch, libraryState.status])
  useEffect(() => { if (historyState.status === 'idle') dispatch(fetchHistory()) }, [dispatch, historyState.status])
  return (
    <>
      <section className="profile-hero">
        <div className="profile-banner" style={profile.bannerUrl ? { backgroundImage: `linear-gradient(90deg,rgba(12,14,23,.35),rgba(12,14,23,.75)),url(${profile.bannerUrl})` } : undefined}><Sparkles /></div>
        <div className="profile-summary"><div className="profile-avatar">{profile.avatarUrl ? <img src={profile.avatarUrl} alt="Avatar del perfil" /> : <UserRound />}</div><div><span className="eyebrow">Perfil personal</span><h1>{profile.displayName}</h1><p>{profile.bio}</p></div></div>
      </section>
      <ProfileAnalytics library={library} history={historyState.items} />
      <div className="profile-insights-grid">
        <ActivityTimeline entries={historyState.items} status={historyState.status} error={historyState.error} />
        <section className="profile-highlights"><div className="section-heading"><div><span className="eyebrow">Tu selección</span><h2><Trophy /> Tus mejores puntuados</h2></div></div><div className="top-rated-grid"><TopRated title="Top 5 anime" icon={<Clapperboard />} entries={topAnime} /><TopRated title="Top 5 manga" icon={<BookOpen />} entries={topManga} /></div></section>
      </div>
      <div className="profile-layout">
        <section className="theme-panel"><div className="section-heading"><div><span className="eyebrow">Apariencia</span><h2><Palette /> Personalización</h2></div><button className="reset-theme" onClick={() => dispatch(resetTheme())}><RotateCcw /> Restaurar</button></div>
          <label className="profile-field"><span>Nombre mostrado</span><input value={profile.displayName} maxLength={40} onChange={(e) => update({ displayName: e.target.value })} /></label>
          <label className="profile-field"><span>Biografía</span><textarea value={profile.bio} maxLength={160} onChange={(e) => update({ bio: e.target.value })} /></label>
          {profile.accountType === 'LOCAL'
            ? <div className="profile-file-grid"><ProfileFileField type="avatar" label="Avatar" hint="JPG, PNG o WebP · máximo 2 MB" value={profile.avatarUrl} busy={profile.assetStatus === 'uploading'} dispatch={dispatch} /><ProfileFileField type="banner" label="Banner" hint="JPG, PNG o WebP · máximo 6 MB" value={profile.bannerUrl} busy={profile.assetStatus === 'uploading'} dispatch={dispatch} /></div>
            : <><div className="profile-url-grid"><label className="profile-field"><span>URL del avatar</span><input value={profile.avatarUrl || ''} onChange={(e) => update({ avatarUrl: e.target.value })} placeholder="https://..." /></label><label className="profile-field"><span>URL del banner</span><input value={profile.bannerUrl || ''} onChange={(e) => update({ bannerUrl: e.target.value })} placeholder="https://..." /></label></div><div className="profile-actions"><span>{libraryState.syncResult ? `${libraryState.syncResult.total} títulos sincronizados` : 'Actualizá progreso, puntajes y nuevas obras desde AniList'}</span><button className="primary-button" disabled={libraryState.syncStatus === 'loading'} onClick={() => dispatch(synchronizeAniListLibrary())}><RefreshCw className={libraryState.syncStatus === 'loading' ? 'spin' : ''} /> {libraryState.syncStatus === 'loading' ? 'Sincronizando...' : 'Sincronizar AniList'}</button></div></>}
          <div className="color-options"><ThemeColorPicker label="Color de fondo" value={profile.backgroundColor} onChange={(backgroundColor) => update({ backgroundColor })} /><ThemeColorPicker label="Tarjetas y rectángulos" value={profile.surfaceColor} onChange={(surfaceColor) => update({ surfaceColor })} /><ThemeColorPicker label="Color de acento" value={profile.accentColor} onChange={(accentColor) => update({ accentColor })} /></div>
          <label className="dot-toggle"><input type="checkbox" checked={profile.dotsEnabled} onChange={(e) => update({ dotsEnabled: e.target.checked })} /><span><b>Dot Background</b><small>Mostrar una trama sutil de puntos en el fondo.</small></span></label>
          <div className="profile-actions"><span>{profile.saved ? 'Cambios guardados' : 'Hay cambios sin guardar'}</span><button className="primary-button" disabled={profile.status === 'saving'} onClick={() => dispatch(saveProfile())}><Save /> {profile.status === 'saving' ? 'Guardando...' : 'Guardar perfil'}</button></div>
        </section>
      </div>
    </>
  )
}

function ProfileFileField({ type, label, hint, value, busy, dispatch }) {
  const inputId = `profile-${type}-file`
  const choose = (event) => {
    const file = event.target.files?.[0]
    if (file) dispatch(uploadProfileAsset({ type, file }))
    event.target.value = ''
  }
  return <article className="profile-file-field">
    <div className="profile-file-icon">{value ? <img src={value} alt="" /> : <FileImage />}</div>
    <div><strong>{label}</strong><small>{hint}</small><div className="profile-file-actions"><label htmlFor={inputId} className={busy ? 'disabled' : ''}><Upload /> {value ? 'Cambiar' : 'Subir'}</label>{value && <button type="button" disabled={busy} onClick={() => dispatch(deleteProfileAsset(type))}><Trash2 /> Quitar</button>}</div></div>
    <input id={inputId} type="file" accept="image/jpeg,image/png,image/webp" onChange={choose} disabled={busy} />
  </article>
}

function TopRated({ title, icon, entries }) {
  return <div className="top-rated"><h3>{icon} {title}</h3>{entries.length ? <div>{entries.map((entry, index) => <Link to={`/media/${entry.anilistId}`} key={entry.id}><span className="top-rated-rank">#{index + 1}</span><img src={entry.coverImage} alt="" /><strong>{entry.title}</strong><span><Star /> {entry.personalScore}</span></Link>)}</div> : <p>Todavía no puntuaste títulos de este tipo.</p>}</div>
}
