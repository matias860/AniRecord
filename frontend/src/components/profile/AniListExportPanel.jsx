import { useEffect, useRef, useState } from 'react'
import { useSelector } from 'react-redux'
import { ArrowUpRight, RefreshCw } from 'lucide-react'
import apiClient from '../../api/apiClient.js'
import './AniListExportPanel.css'

const statuses = { CURRENT: 'Viendo', COMPLETED: 'Completado', PLANNING: 'Pendiente', PAUSED: 'Pausado', DROPPED: 'Abandonado', REPEATING: 'Volviendo a ver' }
const describe = (value) => value
  ? `${statuses[value.status] ?? value.status} · ${value.progress} episodios · ${value.score ? `${value.score / 10}/10` : 'Sin puntuar'}`
  : 'No está en tu lista'

export default function AniListExportPanel() {
  const accountType = useSelector((state) => state.auth.accountType)
  const [preview, setPreview] = useState(null)
  const [phase, setPhase] = useState('idle')
  const [error, setError] = useState('')
  const [completed, setCompleted] = useState(0)
  const [selected, setSelected] = useState(new Set())
  const [total, setTotal] = useState(0)
  const stop = useRef(false)
  const mounted = useRef(true)
  const busy = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; stop.current = true }
  }, [])

  const review = async () => {
    if (busy.current) return
    busy.current = true
    setError(''); setPhase('loading'); setPreview(null); setCompleted(0)
    try {
      const { data } = await apiClient.post('/library/export/anilist/preview')
      if (!mounted.current) return
      setPreview(data); setSelected(new Set(data.changes.map((entry) => entry.entryId))); setPhase('review')
    } catch (reason) {
      if (mounted.current) { setError(reason.message); setPhase('idle') }
    } finally { busy.current = false }
  }

  const apply = async () => {
    if (busy.current || !preview || !selected.size) return
    busy.current = true; stop.current = false
    const changes = preview.changes.filter((entry) => selected.has(entry.entryId))
    setError(''); setPhase('sending'); setCompleted(0); setTotal(changes.length)
    let count = 0
    try {
      for (const entry of changes) {
        if (stop.current) break
        await apiClient.post(`/library/export/anilist/${preview.id}/entries/${entry.entryId}`)
        count += 1
        if (mounted.current) setCompleted(count)
      }
      if (mounted.current) setPhase(count === changes.length ? 'done' : 'stopped')
    } catch (reason) {
      if (mounted.current) { setError(reason.message); setPhase('stopped') }
    } finally { busy.current = false }
  }

  if (accountType !== 'ANILIST') return null
  const working = phase === 'loading' || phase === 'sending' || phase === 'stopping'
  const reviewing = phase === 'review'
  const additions = preview?.changes.filter((entry) => !entry.before).length ?? 0

  return <section className="anilist-export-panel" aria-labelledby="anilist-export-heading">
    <div className="section-heading"><div><span className="eyebrow">Tu biblioteca conectada</span><h2 id="anilist-export-heading"><ArrowUpRight /> Sincronizar con AniList</h2></div></div>
    <p>Envía tus animes de AniRecord a AniList. Revisa los cambios de estado, episodios y puntuación antes de confirmarlos.</p>
    <p className="anilist-export-note">No se borran entradas de AniList. Manga, fechas, notas y otros datos se conservan. Esta sincronización es manual y va de AniRecord hacia AniList.</p>
    {error && <p role="alert" className="anilist-export-error">{error}</p>}
    <div aria-live="polite" role="status">
      {phase === 'loading' && <p>Comparando tu biblioteca con AniList… Puede tardar si tu lista es grande.</p>}
      {(phase === 'sending' || phase === 'stopping') && <><p>{phase === 'stopping' ? 'Deteniendo después de la entrada actual…' : 'Enviando cambios… Mantén esta página abierta.'} {completed} de {total} confirmados.</p><progress value={completed} max={total} aria-label="Cambios enviados a AniList" /></>}
      {phase === 'done' && <p>Sincronización completada: {completed} {completed === 1 ? 'entrada confirmada' : 'entradas confirmadas'} en AniList.</p>}
      {phase === 'stopped' && <p>Envío detenido. {completed} {completed === 1 ? 'entrada confirmada' : 'entradas confirmadas'}. Los cambios ya enviados se conservan; revisa de nuevo para ver qué queda pendiente.</p>}
    </div>
    {reviewing && preview && <>
      <p><strong>Destino: {preview.username}</strong> · {additions} para agregar · {preview.changes.length - additions} para actualizar · {preview.unchanged} sin cambios.</p>
      {preview.changes.length ? <>
        <p>Los valores de AniRecord reemplazarán los de AniList en las entradas seleccionadas, incluso si el progreso o la puntuación son menores.</p>
        <label className="anilist-export-selection"><input type="checkbox" checked={selected.size === preview.changes.length} onChange={(event) => setSelected(new Set(event.target.checked ? preview.changes.map((entry) => entry.entryId) : []))} /> Seleccionar todos los cambios</label>
        <div className="anilist-export-changes" role="region" aria-label="Vista previa de cambios" tabIndex={0}>
          {preview.changes.map((entry) => <label key={entry.entryId} className="anilist-export-entry">
            <input type="checkbox" checked={selected.has(entry.entryId)} onChange={(event) => setSelected((previous) => {
              const next = new Set(previous)
              if (event.target.checked) next.add(entry.entryId); else next.delete(entry.entryId)
              return next
            })} />
            <span><strong>{entry.title}</strong><small>AniList: {describe(entry.before)}</small><small>AniRecord: {describe(entry.after)}</small></span>
          </label>)}
        </div>
        <p className="anilist-export-note">Se envían gradualmente para respetar los límites de AniList. Puedes detener el proceso entre entradas.</p>
      </> : <p>Tus animes ya están al día. No hay nada que enviar.</p>}
    </>}
    <div className="anilist-export-actions">
      {!working && <button type="button" className="reset-theme" onClick={review}><RefreshCw /> {preview ? 'Revisar de nuevo' : 'Revisar cambios'}</button>}
      {reviewing && selected.size > 0 && <button type="button" className="primary-button" onClick={apply}>Confirmar y enviar {selected.size} {selected.size === 1 ? 'cambio' : 'cambios'}</button>}
      {(phase === 'sending' || phase === 'stopping') && <button type="button" className="reset-theme" disabled={phase === 'stopping'} onClick={() => { stop.current = true; setPhase('stopping') }}>Detener envío</button>}
    </div>
  </section>
}
