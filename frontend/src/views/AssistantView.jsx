import { useEffect, useRef, useState } from 'react'
import { ArrowUp, Bot, Check, RefreshCw, Sparkles, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import apiClient from '../api/apiClient.js'
import './AssistantView.css'

const EXAMPLES = [
  'Un romance terminado y corto',
  'Algo parecido a Frieren, pero con más acción',
  'Una película de ciencia ficción',
  'Algo de mis pendientes para ver hoy',
]
const SOURCE_LABELS = { LOCAL: 'Respuesta rápida', GEMINI: 'Interpretado con Gemini', FALLBACK: 'Modo de respaldo' }
const COVER_FALLBACK = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="240" height="360"%3E%3Crect width="100%25" height="100%25" fill="%2310182b"/%3E%3Cpath d="M60 70h120v190H60zM120 70v190" fill="none" stroke="%23a78bfa" stroke-width="12"/%3E%3C/svg%3E'

export default function AssistantView() {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState([])
  const [previousIntent, setPreviousIntent] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const abortRef = useRef(null)
  const requestIdRef = useRef(0)
  const endRef = useRef(null)
  useEffect(() => () => { abortRef.current?.abort() }, [])
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }) }, [messages, loading])

  const submit = async (provided) => {
    const text = (provided ?? draft).trim()
    if (!text || loading) return
    setDraft(''); setError(null); setLoading(true)
    setMessages((items) => [...items, { role: 'user', text }])
    const requestId = ++requestIdRef.current
    abortRef.current = new AbortController()
    try {
      const { data } = await apiClient.post('/assistant/recommend', { text, previousIntent }, { signal: abortRef.current.signal })
      if (requestId === requestIdRef.current) {
        setPreviousIntent(data.intent)
        setMessages((items) => [...items, { role: 'assistant', ...data }])
      }
    } catch (requestError) {
      if (requestId === requestIdRef.current && requestError.code !== 'ERR_CANCELED') setError(requestError.message)
    } finally { if (requestId === requestIdRef.current) setLoading(false) }
  }
  const clear = () => { requestIdRef.current += 1; abortRef.current?.abort(); setMessages([]); setPreviousIntent(null); setDraft(''); setError(null); setLoading(false) }
  return <section className="assistant-page">
    <header className="assistant-hero">
      <div className="assistant-orb"><Sparkles /></div>
      <div><span className="eyebrow">Asistente de recomendaciones</span><h1>Encontrá qué ver o leer</h1><p>Contame qué buscás. AniRecord convierte tu idea en filtros y comprueba cada resultado en el catálogo.</p></div>
      {messages.length > 0 && <button type="button" className="assistant-clear" onClick={clear}><Trash2 /> Nueva consulta</button>}
    </header>

    <div className="assistant-shell">
      <div className="assistant-conversation" aria-live="polite">
        {messages.length === 0 && <div className="assistant-welcome">
          <Bot /><h2>Podés pedirlo como te salga</h2>
          <p>Entiendo género, duración, estado, país, año, títulos parecidos y también búsquedas dentro de tu biblioteca.</p>
          <div className="assistant-examples">{EXAMPLES.map((example) => <button type="button" key={example} onClick={() => submit(example)}>{example}</button>)}</div>
          <small>Los pedidos claros se resuelven en AniRecord. Si una frase es ambigua, Gemini ayuda a interpretarla sin recibir tu biblioteca.</small>
        </div>}
        {messages.map((message, index) => message.role === 'user'
          ? <div className="assistant-user-message" key={`${index}-${message.text}`}>{message.text}</div>
          : <AssistantAnswer key={index} message={message} />)}
        {loading && <div className="assistant-thinking"><span /><span /><span /><em>Buscando opciones reales…</em></div>}
        {error && <div className="assistant-error" role="alert"><strong>No pude completar la búsqueda</strong><span>{error}</span><button type="button" onClick={() => submit(messages.at(-1)?.text)}><RefreshCw /> Reintentar</button></div>}
        <div ref={endRef} />
      </div>
      <form className="assistant-composer" onSubmit={(event) => { event.preventDefault(); submit() }}>
        <label htmlFor="assistant-prompt">Tu pedido</label>
        <textarea id="assistant-prompt" value={draft} maxLength={400} rows={2} placeholder="Ej.: Quiero algo corto, oscuro y sin romance…" onChange={(event) => setDraft(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit() } }} />
        <span>{draft.length}/400</span><button type="submit" disabled={loading || !draft.trim()} aria-label="Enviar pedido"><ArrowUp /></button>
      </form>
    </div>
  </section>
}

function AssistantAnswer({ message }) {
  return <article className="assistant-answer">
    <header><span><Sparkles /> {SOURCE_LABELS[message.source] || 'AniRecord'}</span>{message.source === 'GEMINI' && <small>{message.remainingAiRequests} consultas asistidas disponibles hoy</small>}</header>
    <p>{message.reply}</p>
    {!!message.understood?.length && <div className="assistant-understood"><b><Check /> Entendí</b>{message.understood.map((item) => <span key={item}>{item}</span>)}</div>}
    {!!message.results?.length && <div className="assistant-results">{message.results.map((media) => <Link to={`/media/${media.anilistId}`} className="assistant-result" key={media.anilistId}>
      <img src={media.coverImage || COVER_FALLBACK} onError={(event) => { event.currentTarget.src = COVER_FALLBACK }} alt={`Portada de ${media.title}`} loading="lazy" decoding="async" />
      <div><small>{media.mediaType === 'ANIME' ? 'Anime' : 'Manga'}{media.releaseYear ? ` · ${media.releaseYear}` : ''}</small><h3>{media.title}</h3><p>{media.reason}</p><div>{media.genres?.slice(0, 3).map((genre) => <span key={genre}>{genre}</span>)}</div></div>
    </Link>)}</div>}
  </article>
}
