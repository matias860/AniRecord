import { useEffect, useState } from 'react'
import { BookOpen, LogIn, ShieldCheck, Sparkles, UserPlus } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { useLocation } from 'react-router-dom'
import anilistLogo from '../assets/anilist-logo.svg'
import { clearAuthError, localLogin, registerLocal } from '../features/auth/authSlice.js'
import { currentReturnPath, oauthLoginUrl, rememberReturnPath } from '../lib/authNavigation.js'

export default function LoginView({ checkingSession = false }) {
  const dispatch = useDispatch()
  const location = useLocation()
  const { mutationStatus, error } = useSelector((state) => state.auth)
  const [mode, setMode] = useState('options')
  const [form, setForm] = useState({ username: '', displayName: '', password: '' })
  const busy = mutationStatus === 'loading'
  const returnTo = location.state?.returnTo
  useEffect(() => { dispatch(clearAuthError()) }, [dispatch, mode])
  useEffect(() => {
    if (returnTo) rememberReturnPath(returnTo)
  }, [returnTo])
  const update = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  const submit = (event) => {
    event.preventDefault()
    if (mode === 'register') dispatch(registerLocal(form))
    else dispatch(localLogin({ username: form.username, password: form.password }))
  }
  const rememberDestination = () => {
    const current = currentReturnPath(location)
    if (current !== '/login') rememberReturnPath(current)
  }
  return <main className="login-page dot-background">
    <section className={`login-card ${mode !== 'options' ? 'with-local-form' : ''}`}>
      <div className="login-brand"><span><BookOpen /></span>Ani<strong>Record</strong></div>
      {checkingSession && <p className="login-session-check" role="status">Comprobando si ya iniciaste sesión…</p>}
      {mode === 'options' ? <>
        <div className="login-art"><Sparkles /><img src={anilistLogo} alt="AniList" /></div>
        <span className="eyebrow">Tu biblioteca personal</span>
        <h1>Todo lo que ves y leés, en un solo lugar.</h1>
        <p>Inicia sesión con AniList para importar tu colección o crea un perfil independiente desde cero.</p>
        <div className="login-options">
          <a className="anilist-login-button" href={oauthLoginUrl()} onClick={rememberDestination}><img src={anilistLogo} alt="" /><span>Continuar con AniList</span><LogIn /></a>
          <button className="local-account-button" onClick={() => setMode('register')}><UserPlus /><span>Crear un perfil nuevo</span></button>
          <button className="login-text-button" onClick={() => setMode('login')}>Ya tengo un perfil local</button>
        </div>
        <small><ShieldCheck /> Las contraseñas locales se guardan cifradas y nunca se muestran.</small>
      </> : <>
        <span className="eyebrow">{mode === 'register' ? 'Nuevo perfil local' : 'Bienvenido de nuevo'}</span>
        <h1>{mode === 'register' ? 'Crea tu espacio.' : 'Entra en tu perfil.'}</h1>
        <p>{mode === 'register' ? 'Empezá con una biblioteca vacía y personalizala a tu manera.' : 'Usá el nombre y la contraseña que elegiste.'}</p>
        <form className="local-auth-form" onSubmit={submit}>
          <label><span>Usuario</span><input name="username" value={form.username} onChange={update} minLength={3} maxLength={30} pattern="[A-Za-z0-9._-]+" autoComplete="username" required /></label>
          {mode === 'register' && <label><span>Nombre mostrado</span><input name="displayName" value={form.displayName} onChange={update} maxLength={40} autoComplete="name" required /></label>}
          <label><span>Contraseña</span><input name="password" type="password" value={form.password} onChange={update} minLength={8} maxLength={72} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} required /></label>
          {error && <p className="auth-form-error" role="alert">{error}</p>}
          <button className="local-account-button" disabled={busy} type="submit">{mode === 'register' ? <UserPlus /> : <LogIn />}<span>{busy ? 'Procesando…' : mode === 'register' ? 'Crear perfil' : 'Iniciar sesión'}</span></button>
        </form>
        <button className="login-text-button" onClick={() => setMode('options')}>Volver a las opciones</button>
      </>}
    </section>
  </main>
}
