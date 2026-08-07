import { BookOpen, LogIn, ShieldCheck, Sparkles } from 'lucide-react'
import anilistLogo from '../assets/anilist-logo.svg'

export default function LoginView() {
  return <main className="login-page dot-background">
    <section className="login-card">
      <div className="login-brand"><span><BookOpen /></span>Anime<strong>List</strong></div>
      <div className="login-art"><Sparkles /><img src={anilistLogo} alt="AniList" /></div>
      <span className="eyebrow">Tu biblioteca personal</span>
      <h1>Todo lo que ves y leés, en un solo lugar.</h1>
      <p>Usá tu cuenta de AniList para entrar. La primera vez importaremos automáticamente tu anime, manga, progreso y puntuaciones.</p>
      <a className="anilist-login-button" href="/api/auth/login"><img src={anilistLogo} alt="" /><span>Continuar con AniList</span><LogIn /></a>
      <small><ShieldCheck /> No guardamos contraseñas. AniList confirma tu identidad de forma segura.</small>
    </section>
  </main>
}
