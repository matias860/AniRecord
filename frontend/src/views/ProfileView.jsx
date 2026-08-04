import { useEffect } from 'react'
import { Palette, RotateCcw, Save, Sparkles, UserRound } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import ThemeColorPicker from '../components/profile/ThemeColorPicker.jsx'
import { fetchLibrary } from '../features/library/librarySlice.js'
import { resetTheme, saveProfile, updateProfile } from '../features/profile/profileSlice.js'

export default function ProfileView() {
  const dispatch = useDispatch()
  const profile = useSelector((state) => state.profile)
  const libraryState = useSelector((state) => state.library)
  const library = libraryState.items
  const update = (changes) => dispatch(updateProfile(changes))
  useEffect(() => { if (libraryState.status === 'idle') dispatch(fetchLibrary()) }, [dispatch, libraryState.status])
  return (
    <>
      <section className="profile-hero">
        <div className="profile-banner" style={profile.bannerUrl ? { backgroundImage: `linear-gradient(90deg,rgba(12,14,23,.35),rgba(12,14,23,.75)),url(${profile.bannerUrl})` } : undefined}><Sparkles /></div>
        <div className="profile-summary"><div className="profile-avatar">{profile.avatarUrl ? <img src={profile.avatarUrl} alt="Avatar del perfil" /> : <UserRound />}</div><div><span className="eyebrow">Perfil personal</span><h1>{profile.displayName}</h1><p>{profile.bio}</p></div></div>
      </section>
      <div className="profile-layout">
        <section className="profile-preview"><h2>Resumen</h2><div className="profile-stats"><div><strong>{library.length}</strong><span>En biblioteca</span></div><div><strong>{library.filter((m) => m.trackingStatus === 'COMPLETED').length}</strong><span>Completados</span></div><div><strong>{library.filter((m) => m.trackingStatus === 'IN_PROGRESS').length}</strong><span>En curso</span></div></div></section>
        <section className="theme-panel"><div className="section-heading"><div><span className="eyebrow">Apariencia</span><h2><Palette /> Personalización</h2></div><button className="reset-theme" onClick={() => dispatch(resetTheme())}><RotateCcw /> Restaurar</button></div>
          <label className="profile-field"><span>Nombre mostrado</span><input value={profile.displayName} maxLength={40} onChange={(e) => update({ displayName: e.target.value })} /></label>
          <label className="profile-field"><span>Biografía</span><textarea value={profile.bio} maxLength={160} onChange={(e) => update({ bio: e.target.value })} /></label>
          <div className="profile-url-grid"><label className="profile-field"><span>URL del avatar</span><input value={profile.avatarUrl || ''} onChange={(e) => update({ avatarUrl: e.target.value })} placeholder="https://..." /></label><label className="profile-field"><span>URL del banner</span><input value={profile.bannerUrl || ''} onChange={(e) => update({ bannerUrl: e.target.value })} placeholder="https://..." /></label></div>
          <div className="color-options"><ThemeColorPicker label="Color de fondo" value={profile.backgroundColor} onChange={(backgroundColor) => update({ backgroundColor })} /><ThemeColorPicker label="Tarjetas y rectángulos" value={profile.surfaceColor} onChange={(surfaceColor) => update({ surfaceColor })} /><ThemeColorPicker label="Color de acento" value={profile.accentColor} onChange={(accentColor) => update({ accentColor })} /></div>
          <label className="dot-toggle"><input type="checkbox" checked={profile.dotsEnabled} onChange={(e) => update({ dotsEnabled: e.target.checked })} /><span><b>Dot Background</b><small>Mostrar una trama sutil de puntos en el fondo.</small></span></label>
          <div className="profile-actions"><span>{profile.error || (profile.saved ? 'Cambios guardados' : 'Hay cambios sin guardar')}</span><button className="primary-button" disabled={profile.status === 'saving'} onClick={() => dispatch(saveProfile())}><Save /> {profile.status === 'saving' ? 'Guardando...' : 'Guardar perfil'}</button></div>
        </section>
      </div>
    </>
  )
}
