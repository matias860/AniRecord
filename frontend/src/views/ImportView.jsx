import { useEffect, useRef, useState } from 'react'
import { BookOpen, Download, LoaderCircle } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import apiClient from '../api/apiClient.js'
import { fetchAuth } from '../features/auth/authSlice.js'

export default function ImportView() {
  const auth = useSelector((state) => state.auth)
  const dispatch = useDispatch(); const started = useRef(false)
  const [error, setError] = useState(null)
  useEffect(() => { if (started.current) return; started.current = true
    apiClient.post('/library/import/anilist')
      .then(() => dispatch(fetchAuth())).catch((reason) => setError(reason.message))
  }, [auth.username, dispatch])
  return <main className="login-page dot-background"><section className="login-card import-card">
    <div className="login-brand"><span><BookOpen /></span>Ani<strong>Record</strong></div>
    {error ? <><Download /><h1>No pudimos importar tu biblioteca</h1><p>{error}</p><button className="anilist-login-button" onClick={() => window.location.reload()}>Reintentar</button></>
      : <><LoaderCircle className="spin import-spinner" /><span className="eyebrow">Preparando tu cuenta</span><h1>Importando tu biblioteca</h1><p>Estamos trayendo anime, manga, progreso y puntuaciones de <b>{auth.username}</b>. Puede tardar unos segundos y solo sucede la primera vez.</p></>}
  </section></main>
}
